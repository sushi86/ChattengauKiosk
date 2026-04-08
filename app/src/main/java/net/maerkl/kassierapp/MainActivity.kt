package net.maerkl.kassierapp

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sumup.merchant.reader.api.SumUpAPI
import com.sumup.merchant.reader.api.SumUpLogin
import com.sumup.merchant.reader.api.SumUpPayment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.ui.main.MainViewModel
import net.maerkl.kassierapp.ui.navigation.AppNavigation
import net.maerkl.kassierapp.ui.theme.KassierappTheme
import android.widget.Toast
import java.math.BigDecimal

class MainActivity : ComponentActivity() {
    companion object {
        private const val REQUEST_CODE_CHECKOUT = 1001
        private const val REQUEST_CODE_LOGIN = 1002
        private const val REQUEST_CODE_CARD_READER = 1003
    }

    private var pendingCardReaderSetup = false
    private var mainViewModel: MainViewModel? = null
    private var sumUpLoggedIn by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions handled */ }

    private val isDeviceOwner: Boolean
        get() {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(packageName)
        }

    /** Kiosk-Modus ist pausiert wenn der Admin in den Settings ist (SumUp braucht WebViews) */
    private var kioskPaused = false

    /** Screen dimming after inactivity */
    private val dimHandler = Handler(Looper.getMainLooper())
    private val dimDelay = 30_000L
    private val dimBrightness = 0.05f
    private var isDimmed = false
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    private val dimRunnable = Runnable {
        if (!isCharging()) dimScreen()
    }

    private fun isCharging(): Boolean {
        val batteryStatus = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL
    }

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
                val maxRange = event.sensor.maximumRange
                if (event.values[0] < maxRange) {
                    wakeScreen()
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun dimScreen() {
        if (!isDimmed) {
            isDimmed = true
            val lp = window.attributes
            lp.screenBrightness = dimBrightness
            window.attributes = lp
        }
    }

    private fun wakeScreen() {
        isDimmed = false
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        resetDimTimer()
    }

    private fun resetDimTimer() {
        dimHandler.removeCallbacks(dimRunnable)
        dimHandler.postDelayed(dimRunnable, dimDelay)
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun startKioskMode() {
        if (isDeviceOwner && !kioskPaused) {
            startLockTask()
        }
        enableImmersiveMode()
    }

    fun pauseKioskMode() {
        kioskPaused = true
        if (isDeviceOwner) {
            stopLockTask()
        }
    }

    fun resumeKioskMode() {
        kioskPaused = false
        startKioskMode()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        resetDimTimer()
        requestPermissions()
        autoLoginSumUp()

        setContent {
            KassierappTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val vm: MainViewModel = viewModel()
                mainViewModel = vm

                // Collect checkout triggers (LaunchedEffect ensures single collector)
                LaunchedEffect(Unit) {
                    vm.checkoutAmount.collect { amount ->
                        startCheckout(amount)
                    }
                }

                // Collect snackbar messages (LaunchedEffect ensures single collector)
                LaunchedEffect(Unit) {
                    vm.snackbarMessage.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                AppNavigation(
                    snackbarHostState = snackbarHostState,
                    sumUpLoggedIn = sumUpLoggedIn,
                    onLogin = { startSumUpLogin() },
                    onOpenCardReader = { openCardReader() },
                    onShareIntent = { intent ->
                        startActivity(Intent.createChooser(intent, "CSV teilen"))
                    },
                    onPauseKiosk = { pauseKioskMode() },
                    onResumeKiosk = { resumeKioskMode() }
                )
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        wakeScreen()
        return super.dispatchTouchEvent(ev)
    }

    override fun onResume() {
        super.onResume()
        startKioskMode()
        resetDimTimer()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        proximitySensor?.let {
            sensorManager?.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        dimHandler.removeCallbacks(dimRunnable)
        sensorManager?.unregisterListener(proximityListener)
    }

    @Suppress("DEPRECATION")
    private fun autoLoginSumUp() {
        if (SumUpAPI.isLoggedIn()) {
            sumUpLoggedIn = true
            return
        }

        val app = application as KassierApplication
        lifecycleScope.launch {
            val token = app.settingsDataStore.oauthToken.first()
            val affiliateKey = app.settingsDataStore.affiliateKey.first()

            if (token.isNotBlank() && affiliateKey.isNotBlank()) {
                val login = SumUpLogin.builder(affiliateKey)
                    .accessToken(token)
                    .build()
                SumUpAPI.openLoginActivity(this@MainActivity, login, REQUEST_CODE_LOGIN)
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    @Suppress("DEPRECATION")
    private fun startCheckout(amount: Double) {
        if (!SumUpAPI.isLoggedIn()) {
            Toast.makeText(this, "Bitte zuerst bei SumUp einloggen", Toast.LENGTH_LONG).show()
            mainViewModel?.onPaymentFailed()
            return
        }

        val payment = SumUpPayment.builder()
            .total(BigDecimal(amount))
            .currency(SumUpPayment.Currency.EUR)
            .title("Spieltag-Verkauf")
            .skipSuccessScreen()
            .skipFailedScreen()
            .build()

        SumUpAPI.checkout(this@MainActivity, payment, REQUEST_CODE_CHECKOUT)
    }

    @Suppress("DEPRECATION")
    private fun startSumUpLogin() {
        if (SumUpAPI.isLoggedIn()) {
            Toast.makeText(this, "Bereits bei SumUp eingeloggt", Toast.LENGTH_SHORT).show()
            return
        }

        val app = application as KassierApplication
        lifecycleScope.launch {
            val token = app.settingsDataStore.oauthToken.first()
            val affiliateKey = app.settingsDataStore.affiliateKey.first()

            val loginBuilder = SumUpLogin.builder(affiliateKey)
            if (token.isNotBlank()) {
                loginBuilder.accessToken(token)
            }
            val login = loginBuilder.build()

            SumUpAPI.openLoginActivity(this@MainActivity, login, REQUEST_CODE_LOGIN)
        }
    }

    private fun openCardReader() {
        @Suppress("DEPRECATION")
        if (SumUpAPI.isLoggedIn()) {
            SumUpAPI.openCardReaderPage(this, REQUEST_CODE_CARD_READER)
        } else {
            pendingCardReaderSetup = true
            startSumUpLogin()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_CHECKOUT -> {
                val resultCode = data?.extras?.getInt(SumUpAPI.Response.RESULT_CODE)
                if (resultCode == SumUpAPI.Response.ResultCode.SUCCESSFUL) {
                    val txCode = data?.extras?.getString(SumUpAPI.Response.TX_CODE)
                    mainViewModel?.onPaymentSuccess(txCode)
                } else {
                    mainViewModel?.onPaymentFailed()
                }
            }
            REQUEST_CODE_LOGIN -> {
                sumUpLoggedIn = SumUpAPI.isLoggedIn()
                if (sumUpLoggedIn) {
                    Toast.makeText(this, "SumUp Login erfolgreich", Toast.LENGTH_SHORT).show()
                    if (pendingCardReaderSetup) {
                        pendingCardReaderSetup = false
                        SumUpAPI.openCardReaderPage(this, REQUEST_CODE_CARD_READER)
                    }
                } else {
                    val msg = data?.extras?.getString(SumUpAPI.Response.MESSAGE)
                    Toast.makeText(this, "SumUp Login fehlgeschlagen: ${msg ?: "Unbekannter Fehler"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
