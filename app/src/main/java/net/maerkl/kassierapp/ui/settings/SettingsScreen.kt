package net.maerkl.kassierapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import net.maerkl.kassierapp.ui.theme.Green900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenCardReader: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToArticles: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onExitKiosk: () -> Unit
) {
    val affiliateKey by viewModel.affiliateKey.collectAsState()
    val oauthToken by viewModel.oauthToken.collectAsState()
    val merchantInfo by viewModel.merchantInfo.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPinChangeDialog by remember { mutableStateOf(false) }

    var editAffiliateKey by remember(affiliateKey) { mutableStateOf(affiliateKey) }
    var editOauthToken by remember(oauthToken) { mutableStateOf(oauthToken) }

    LaunchedEffect(Unit) {
        viewModel.fetchMerchantInfo()
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("\u2699\uFE0F Einstellungen", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("\u2190 Zur\u00FCck", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Artikelverwaltung Section
            item {
                Text("Artikelverwaltung", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Button(
                    onClick = onNavigateToArticles,
                    colors = ButtonDefaults.buttonColors(containerColor = Green900)
                ) { Text("Artikel & Collections verwalten") }
            }

            // SumUp Configuration Section
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("SumUp-Konfiguration", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (merchantInfo != null) {
                            Text(
                                text = "Konto: $merchantInfo",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = Green900
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        OutlinedTextField(
                            value = editAffiliateKey,
                            onValueChange = { editAffiliateKey = it },
                            label = { Text("Affiliate Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editOauthToken,
                            onValueChange = { editOauthToken = it },
                            label = { Text("OAuth Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.saveSumUpConfig(editAffiliateKey, editOauthToken)
                                    viewModel.fetchMerchantInfo()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Green900)
                            ) { Text("Speichern") }
                            Button(onClick = onLogin) { Text("Login") }
                            Button(onClick = onOpenCardReader) { Text("Kartenleser") }
                        }
                    }
                }
            }

            // Statistics Section
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Statistik", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Button(
                    onClick = onNavigateToStatistics,
                    colors = ButtonDefaults.buttonColors(containerColor = Green900)
                ) { Text("\uD83D\uDCCA Verkaufsstatistik") }
            }

            // System Section
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("System", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenWifiSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Green900)
                    ) { Text("WLAN-Einstellungen") }
                    Button(
                        onClick = onExitKiosk,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Kiosk beenden") }
                }
            }

            // PIN Change Section
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("PIN \u00E4ndern", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Button(
                    onClick = { showPinChangeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Green900)
                ) { Text("PIN \u00E4ndern") }
            }
        }
    }

    if (showPinChangeDialog) {
        PinChangeDialog(
            onDismiss = { showPinChangeDialog = false },
            onSave = { newPin ->
                viewModel.changePin(newPin)
                showPinChangeDialog = false
            }
        )
    }
}

@Composable
private fun PinChangeDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN \u00E4ndern") },
        text = {
            Column {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("Neuer PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("PIN best\u00E4tigen") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPin.length != 4 -> error = "PIN muss 4 Ziffern haben"
                    newPin != confirmPin -> error = "PINs stimmen nicht \u00FCberein"
                    else -> onSave(newPin)
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
internal fun CollectionNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
