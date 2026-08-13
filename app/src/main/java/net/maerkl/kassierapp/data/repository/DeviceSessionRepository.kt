package net.maerkl.kassierapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.maerkl.kassierapp.data.local.DeviceSessionStore

sealed class PairingState {
    data object Unpaired : PairingState()
    data class Paired(val vereinId: String, val geraetId: String) : PairingState()
}

interface AuthSignOut {
    suspend fun signOut()
}

class FirebaseAuthSignOut(private val auth: FirebaseAuth) : AuthSignOut {
    override suspend fun signOut() {
        auth.signOut()
    }
}

class DeviceSessionRepository(
    private val store: DeviceSessionStore,
    private val signOut: AuthSignOut
) {
    private val _pairingState: MutableStateFlow<PairingState> = MutableStateFlow(loadInitial())
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()
    private val unpairMutex = Mutex()
    private val onUnpairedListeners = mutableListOf<() -> Unit>()

    /** Wird nach erfolgreichem Unpair aufgerufen (z.B. SumUp-SDK-Logout, Token-Cache leeren). */
    fun addOnUnpairedListener(listener: () -> Unit) {
        onUnpairedListeners.add(listener)
    }

    private fun loadInitial(): PairingState {
        val v = store.getVereinId()
        val g = store.getGeraetId()
        return if (v != null && g != null) PairingState.Paired(v, g) else PairingState.Unpaired
    }

    fun markPaired(vereinId: String, geraetId: String) {
        store.save(vereinId, geraetId)
        _pairingState.value = PairingState.Paired(vereinId, geraetId)
    }

    suspend fun unpair() = unpairMutex.withLock {
        if (_pairingState.value is PairingState.Unpaired) return@withLock
        signOut.signOut()
        store.clear()
        _pairingState.value = PairingState.Unpaired
        onUnpairedListeners.forEach { it() }
    }

    fun currentVereinId(): String? =
        (_pairingState.value as? PairingState.Paired)?.vereinId
}
