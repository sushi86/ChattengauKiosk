package net.maerkl.kassierapp.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.KassierApplication

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val settings = app.settingsDataStore
    private val deviceSessionRepo = app.deviceSessionRepository

    val pin = settings.pin.stateIn(viewModelScope, SharingStarted.Eagerly, "0000")

    val pairingState = deviceSessionRepo.pairingState
        .stateIn(viewModelScope, SharingStarted.Eagerly, net.maerkl.kassierapp.data.repository.PairingState.Unpaired)

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    fun unpairDevice() {
        viewModelScope.launch {
            deviceSessionRepo.unpair()
            app.sumupTokenRepository.invalidate()
            _snackbarMessage.emit("Gerät entkoppelt")
        }
    }

    fun changePin(newPin: String) {
        viewModelScope.launch {
            settings.savePin(newPin)
            _snackbarMessage.emit("PIN geändert")
        }
    }
}
