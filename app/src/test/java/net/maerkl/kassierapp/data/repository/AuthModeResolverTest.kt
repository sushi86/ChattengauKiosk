package net.maerkl.kassierapp.data.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthModeResolverTest {
    private fun resolver(state: PairingState, affKey: String, token: String): AuthModeResolver {
        val sessionFlow = kotlinx.coroutines.flow.MutableStateFlow(state)
        return AuthModeResolver(
            pairingStateFlow = sessionFlow,
            manualAffiliateKeyFlow = flowOf(affKey),
            manualOauthTokenFlow = flowOf(token)
        )
    }

    @Test
    fun `paired and manual filled → Backend wins`() = runTest {
        val r = resolver(PairingState.Paired("V", "G"), "aff", "tok")
        assertEquals(AuthMode.Backend, r.authMode.first())
    }

    @Test
    fun `paired only → Backend`() = runTest {
        val r = resolver(PairingState.Paired("V", "G"), "", "")
        assertEquals(AuthMode.Backend, r.authMode.first())
    }

    @Test
    fun `only manual filled → Manual`() = runTest {
        val r = resolver(PairingState.Unpaired, "aff", "tok")
        assertEquals(AuthMode.Manual, r.authMode.first())
    }

    @Test
    fun `neither → None`() = runTest {
        val r = resolver(PairingState.Unpaired, "", "")
        assertEquals(AuthMode.None, r.authMode.first())
    }

    @Test
    fun `only one manual field filled → None`() = runTest {
        val r = resolver(PairingState.Unpaired, "aff", "")
        assertEquals(AuthMode.None, r.authMode.first())
    }
}
