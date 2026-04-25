package net.maerkl.kassierapp.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PairingScreen(
    onPaired: () -> Unit,
    viewModel: PairingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var code by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is PairingUiState.Success) onPaired()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Gerät mit Verein verbinden", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Bitte den Aktivierungscode aus dem Admin-Portal eingeben.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.uppercase()
                viewModel.resetError()
            },
            label = { Text("Aktivierungscode") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is PairingUiState.Loading
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.pair(code) },
            enabled = state !is PairingUiState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Gerät verbinden") }

        Spacer(Modifier.height(16.dp))
        when (val s = state) {
            PairingUiState.Loading -> CircularProgressIndicator()
            is PairingUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            else -> {}
        }
    }
}
