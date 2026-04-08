package net.maerkl.kassierapp.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import net.maerkl.kassierapp.ui.components.PinDialog
import net.maerkl.kassierapp.ui.main.MainScreen
import net.maerkl.kassierapp.ui.main.MainViewModel
import net.maerkl.kassierapp.ui.settings.ArticleManagementScreen
import net.maerkl.kassierapp.ui.settings.SettingsScreen
import net.maerkl.kassierapp.ui.settings.SettingsViewModel
import net.maerkl.kassierapp.ui.statistics.StatisticsScreen
import net.maerkl.kassierapp.ui.statistics.StatisticsViewModel

@Composable
fun AppNavigation(
    snackbarHostState: SnackbarHostState,
    sumUpLoggedIn: Boolean,
    onLogin: () -> Unit,
    onOpenCardReader: () -> Unit,
    onShareIntent: (android.content.Intent) -> Unit,
    onPauseKiosk: () -> Unit,
    onResumeKiosk: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val mainViewModel: MainViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    var showPinDialog by remember { mutableStateOf(false) }
    val pin by settingsViewModel.pin.collectAsState()

    // Kiosk automatisch fortsetzen wenn Main-Screen erreicht wird (auch bei System-Back-Gesture)
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, dest, _ ->
            if (dest.route == "main") {
                onResumeKiosk()
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                snackbarHostState = snackbarHostState,
                sumUpLoggedIn = sumUpLoggedIn,
                onNavigateToSettings = { showPinDialog = true }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLogin = onLogin,
                onOpenCardReader = onOpenCardReader,
                onNavigateToStatistics = { navController.navigate("statistics") },
                onNavigateToArticles = { navController.navigate("articles") },
                onOpenWifiSettings = {
                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                },
                onExitKiosk = {
                    onPauseKiosk()
                    (context as? android.app.Activity)?.moveTaskToBack(true)
                }
            )
        }
        composable("articles") {
            ArticleManagementScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("statistics") {
            val statisticsViewModel: StatisticsViewModel = viewModel()
            StatisticsScreen(
                viewModel = statisticsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onShareCsv = onShareIntent
            )
        }
    }

    if (showPinDialog) {
        PinDialog(
            correctPin = pin,
            onSuccess = {
                showPinDialog = false
                navController.navigate("settings")
            },
            onDismiss = { showPinDialog = false }
        )
    }
}
