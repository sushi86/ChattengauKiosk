package net.maerkl.kassierapp.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.maerkl.kassierapp.ui.components.PinDialog
import net.maerkl.kassierapp.ui.main.MainScreen
import net.maerkl.kassierapp.ui.main.MainViewModel
import net.maerkl.kassierapp.ui.settings.SettingsScreen
import net.maerkl.kassierapp.ui.settings.SettingsViewModel

@Composable
fun AppNavigation(
    snackbarHostState: SnackbarHostState,
    onLogin: () -> Unit,
    onOpenCardReader: () -> Unit
) {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    var showPinDialog by remember { mutableStateOf(false) }
    val pin by settingsViewModel.pin.collectAsState()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = mainViewModel,
                snackbarHostState = snackbarHostState,
                onNavigateToSettings = { showPinDialog = true }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onLogin = onLogin,
                onOpenCardReader = onOpenCardReader
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
