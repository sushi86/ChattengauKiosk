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
import android.os.SystemClock
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
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.data.repository.PairingState
import net.maerkl.kassierapp.data.repository.SumupSessionAction
import net.maerkl.kassierapp.data.repository.SumupSessionPlanner
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

        /** Nach einem fehlgeschlagenen Login nicht sofort wieder automatisch anmelden. */
        private const val LOGIN_RETRY_COOLDOWN_MS = 60_000L
    }

    private var pendingCardReaderSetup = false
    private var mainViewModel: MainViewModel? = null
    private var sumUpLoggedIn by mutableStateOf(false)

    /** Laeuft gerade ein Session-Abgleich bzw. eine Login-Activity? Verhindert Doppel-Logins. */
    private var sumupSessionSyncing = false
    private var sumupLoginInFlight = false
    private var lastLoginFailureAt: Long? = null

    /** Zahlung, die wegen abgelaufener SumUp-Session auf den Re-Login wartet. */
    private var pendingCheckoutAmount: Double? = null

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
        // Der SumUp-Session-Abgleich laeuft in onResume — das deckt den Kaltstart
        // genauso ab wie das Zurueckkehren in die App.

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
        syncSumupSession()
    }

    override fun onPause() {
        super.onPause()
        dimHandler.removeCallbacks(dimRunnable)
        sensorManager?.unregisterListener(proximityListener)
    }

    /**
     * Haelt die SumUp-SDK-Session frisch. Das SDK hat eine eigene, persistente
     * Session auf dem Geraet, deren Access-Token altert — waehrend die Kiosk-App
     * tagelang durchlaeuft, laeuft sie ab, und die naechste Kartenzahlung
     * scheitert. Darum bei jedem onResume abgleichen: Token auffrischen, wenn die
     * Session lebt, sonst neu anmelden.
     */
    @Suppress("DEPRECATION")
    private fun syncSumupSession() {
        val app = application as KassierApplication
        if (app.deviceSessionRepository.pairingState.value !is PairingState.Paired) {
            // Verwaiste SDK-Session ohne Pairing: darf nicht weiterleben,
            // sonst kassiert das Geraet auf einen alten Merchant.
            if (SumUpAPI.isLoggedIn()) SumUpAPI.logout()
            sumUpLoggedIn = false
            return
        }
        if (sumupSessionSyncing || sumupLoginInFlight) return
        sumupSessionSyncing = true

        lifecycleScope.launch {
            try {
                val session = try {
                    app.sumupTokenRepository.getSession()
                } catch (e: Exception) {
                    // Backend nicht erreichbar: bestehende Session behalten, vor
                    // jeder Zahlung wird ohnehin erneut geprueft.
                    sumUpLoggedIn = SumUpAPI.isLoggedIn()
                    return@launch
                }
                val sdkMerchant = SumUpAPI.getCurrentMerchant()?.merchantCode
                when (val action = SumupSessionPlanner.plan(SumUpAPI.isLoggedIn(), session, sdkMerchant)) {
                    is SumupSessionAction.Refresh -> {
                        SumUpAPI.updateAccessToken(action.accessToken)
                        sumUpLoggedIn = true
                    }
                    is SumupSessionAction.Login -> {
                        sumUpLoggedIn = false
                        if (canRetryLoginAutomatically()) openSumupLogin(action.accessToken)
                    }
                    is SumupSessionAction.LogoutAndLogin -> {
                        SumUpAPI.logout()
                        sumUpLoggedIn = false
                        Toast.makeText(this@MainActivity, "SumUp-Konto gehört nicht zu diesem Verein – melde neu an", Toast.LENGTH_LONG).show()
                        if (canRetryLoginAutomatically()) openSumupLogin(action.accessToken)
                    }
                    SumupSessionAction.Blocked -> sumUpLoggedIn = SumUpAPI.isLoggedIn()
                }
            } finally {
                sumupSessionSyncing = false
            }
        }
    }

    /**
     * Nach einem gescheiterten Login nicht in einer Endlosschleife aus
     * onResume → Login-Activity → Fehler → onResume landen.
     */
    private fun canRetryLoginAutomatically(): Boolean {
        val last = lastLoginFailureAt ?: return true
        return SystemClock.elapsedRealtime() - last > LOGIN_RETRY_COOLDOWN_MS
    }

    @Suppress("DEPRECATION")
    private fun openSumupLogin(accessToken: String) {
        if (sumupLoginInFlight) return
        val login = SumUpLogin.builder(Config.SUMUP_AFFILIATE_KEY).accessToken(accessToken).build()
        sumupLoginInFlight = true
        SumUpAPI.openLoginActivity(this, login, REQUEST_CODE_LOGIN)
    }

    private fun loginWithBackendToken() {
        val app = application as KassierApplication
        lifecycleScope.launch {
            val token = try {
                app.sumupTokenRepository.getAccessToken()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "SumUp-Token-Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                return@launch
            }
            openSumupLogin(token)
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

    /**
     * Vor JEDER Zahlung: frisches Backend-Token holen, damit die SDK-Session nach
     * langer Standzeit nicht mit abgelaufenem Token kassiert — und pruefen, dass
     * der im Reader-SDK eingeloggte Merchant exakt dem entspricht, den das Backend
     * fuer den gekoppelten Verein erwartet. Nur bei Match wird kassiert.
     */
    @Suppress("DEPRECATION")
    private fun startCheckout(amount: Double) {
        val app = application as KassierApplication
        if (app.deviceSessionRepository.pairingState.value !is PairingState.Paired) {
            Toast.makeText(this, "Bitte zuerst Tablet aktivieren", Toast.LENGTH_LONG).show()
            mainViewModel?.onPaymentFailed()
            return
        }

        lifecycleScope.launch {
            val session = try {
                app.sumupTokenRepository.getSession()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "SumUp-Konto nicht überprüfbar: ${e.message}", Toast.LENGTH_LONG).show()
                mainViewModel?.onPaymentFailed()
                return@launch
            }
            val sdkMerchant = SumUpAPI.getCurrentMerchant()?.merchantCode

            when (val action = SumupSessionPlanner.plan(SumUpAPI.isLoggedIn(), session, sdkMerchant)) {
                is SumupSessionAction.Refresh -> {
                    SumUpAPI.updateAccessToken(action.accessToken)
                    sumUpLoggedIn = true
                    val payment = SumUpPayment.builder()
                        .total(BigDecimal(amount))
                        .currency(SumUpPayment.Currency.EUR)
                        .title("Spieltag-Verkauf")
                        .skipSuccessScreen()
                        .skipFailedScreen()
                        .build()
                    SumUpAPI.checkout(this@MainActivity, payment, REQUEST_CODE_CHECKOUT)
                }
                is SumupSessionAction.Login -> {
                    // Session ist waehrend der Standzeit abgelaufen: automatisch neu
                    // anmelden und die angefangene Zahlung danach fortsetzen.
                    sumUpLoggedIn = false
                    pendingCheckoutAmount = amount
                    Toast.makeText(this@MainActivity, "SumUp-Sitzung abgelaufen – melde neu an…", Toast.LENGTH_SHORT).show()
                    openSumupLogin(action.accessToken)
                }
                is SumupSessionAction.LogoutAndLogin -> {
                    SumUpAPI.logout()
                    sumUpLoggedIn = false
                    pendingCheckoutAmount = null
                    Toast.makeText(this@MainActivity, "Zahlung abgebrochen: SumUp-Konto gehört nicht zu diesem Verein. Neu-Anmeldung gestartet.", Toast.LENGTH_LONG).show()
                    mainViewModel?.onPaymentFailed()
                    openSumupLogin(action.accessToken)
                }
                SumupSessionAction.Blocked -> {
                    Toast.makeText(this@MainActivity, "Zahlung abgebrochen: SumUp-Merchant nicht überprüfbar. Bitte SumUp im Backend neu verbinden.", Toast.LENGTH_LONG).show()
                    mainViewModel?.onPaymentFailed()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startSumUpLogin() {
        if (SumUpAPI.isLoggedIn()) {
            Toast.makeText(this, "Bereits bei SumUp eingeloggt", Toast.LENGTH_SHORT).show()
            return
        }
        val app = application as KassierApplication
        if (app.deviceSessionRepository.pairingState.value !is PairingState.Paired) {
            Toast.makeText(this, "Bitte zuerst Tablet aktivieren", Toast.LENGTH_LONG).show()
            return
        }
        loginWithBackendToken()
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
                val sumUpResult = data?.extras?.getInt(SumUpAPI.Response.RESULT_CODE)
                if (sumUpResult == SumUpAPI.Response.ResultCode.SUCCESSFUL) {
                    val txCode = data?.extras?.getString(SumUpAPI.Response.TX_CODE)
                    mainViewModel?.onPaymentSuccess(txCode)
                } else {
                    mainViewModel?.onPaymentFailed()
                    if (SumupSessionPlanner.isSessionExpiredResult(sumUpResult)) {
                        // Das SDK meldete die Session erst beim Kassieren als tot:
                        // Token verwerfen und sofort neu anmelden, damit der naechste
                        // Versuch durchgeht.
                        sumUpLoggedIn = false
                        (application as KassierApplication).sumupTokenRepository.invalidate()
                        Toast.makeText(this, "SumUp-Sitzung abgelaufen – melde neu an…", Toast.LENGTH_SHORT).show()
                        loginWithBackendToken()
                    }
                }
            }
            REQUEST_CODE_LOGIN -> {
                sumupLoginInFlight = false
                sumUpLoggedIn = SumUpAPI.isLoggedIn()
                val resumeAmount = pendingCheckoutAmount
                pendingCheckoutAmount = null
                if (sumUpLoggedIn) {
                    lastLoginFailureAt = null
                    Toast.makeText(this, "SumUp Login erfolgreich", Toast.LENGTH_SHORT).show()
                    when {
                        pendingCardReaderSetup -> {
                            pendingCardReaderSetup = false
                            SumUpAPI.openCardReaderPage(this, REQUEST_CODE_CARD_READER)
                        }
                        resumeAmount != null -> startCheckout(resumeAmount)
                    }
                } else {
                    lastLoginFailureAt = SystemClock.elapsedRealtime()
                    pendingCardReaderSetup = false
                    if (resumeAmount != null) mainViewModel?.onPaymentFailed()
                    val msg = data?.extras?.getString(SumUpAPI.Response.MESSAGE)
                    Toast.makeText(this, "SumUp Login fehlgeschlagen: ${msg ?: "Unbekannter Fehler"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
