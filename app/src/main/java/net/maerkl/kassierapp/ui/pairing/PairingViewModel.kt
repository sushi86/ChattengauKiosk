package net.maerkl.kassierapp.ui.pairing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.remote.PairingError
import net.maerkl.kassierapp.data.remote.PairingThrowable

sealed class PairingUiState {
    data object Idle : PairingUiState()
    data object Loading : PairingUiState()
    data class Error(val message: String) : PairingUiState()
    data object Success : PairingUiState()
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as KassierApplication).pairingRepository

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Idle)
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    fun pair(code: String) {
        if (code.isBlank()) {
            _state.value = PairingUiState.Error("Bitte Code eingeben")
            return
        }
        _state.value = PairingUiState.Loading
        viewModelScope.launch {
            val result = repo.pair(code.trim())
            _state.value = result.fold(
                onSuccess = { PairingUiState.Success },
                onFailure = { t ->
                    val err = (t as? PairingThrowable)?.error
                    PairingUiState.Error(messageFor(err, t))
                }
            )
        }
    }

    fun resetError() {
        if (_state.value is PairingUiState.Error) _state.value = PairingUiState.Idle
    }

    private fun messageFor(err: PairingError?, t: Throwable): String = when (err) {
        PairingError.InvalidCodeFormat -> "Bitte gültigen Code eingeben"
        PairingError.CodeUnknown -> "Ungültiger Code"
        PairingError.CodeAlreadyUsed -> "Code wurde bereits verwendet"
        PairingError.CodeExpired -> "Code ist abgelaufen, Admin bitten"
        PairingError.AppCheckRejected -> "Gerät nicht attestiert. App neu starten."
        is PairingError.Unknown -> "Verbindungsfehler, bitte erneut versuchen"
        null -> "Fehler: ${t.message ?: "unbekannt"}"
    }
}
