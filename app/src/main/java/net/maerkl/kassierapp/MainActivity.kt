package net.maerkl.kassierapp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions()
        autoLoginSumUp()

        setContent {
            KassierappTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val vm: MainViewModel = viewModel()
                mainViewModel = vm

                // Collect checkout triggers
                lifecycleScope.launch {
                    vm.checkoutAmount.collect { amount ->
                        startCheckout(amount)
                    }
                }

                // Collect snackbar messages
                lifecycleScope.launch {
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
                    }
                )
            }
        }
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
                    mainViewModel?.onPaymentSuccess()
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
