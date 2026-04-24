package net.maerkl.kassierapp.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class AuthMode { Backend, Manual, None }

class AuthModeResolver(
    private val pairingStateFlow: Flow<PairingState>,
    private val manualAffiliateKeyFlow: Flow<String>,
    private val manualOauthTokenFlow: Flow<String>
) {
    val authMode: Flow<AuthMode> = combine(
        pairingStateFlow, manualAffiliateKeyFlow, manualOauthTokenFlow
    ) { pair, aff, tok ->
        when {
            pair is PairingState.Paired -> AuthMode.Backend
            aff.isNotBlank() && tok.isNotBlank() -> AuthMode.Manual
            else -> AuthMode.None
        }
    }
}
