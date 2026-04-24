package net.maerkl.kassierapp.data.remote

import com.google.firebase.functions.FirebaseFunctionsException
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebasePairingServiceTest {

    private fun err(code: FirebaseFunctionsException.Code, msg: String): FirebaseFunctionsException {
        val e = mockk<FirebaseFunctionsException>()
        every { e.code } returns code
        every { e.message } returns msg
        return e
    }

    @Test
    fun `invalid-argument maps to InvalidCodeFormat`() {
        val mapped = FirebasePairingService.mapHttpsError(err(FirebaseFunctionsException.Code.INVALID_ARGUMENT, ""))
        assertEquals(PairingError.InvalidCodeFormat, mapped)
    }

    @Test
    fun `not-found maps to CodeUnknown`() {
        val mapped = FirebasePairingService.mapHttpsError(err(FirebaseFunctionsException.Code.NOT_FOUND, ""))
        assertEquals(PairingError.CodeUnknown, mapped)
    }

    @Test
    fun `failed-precondition with Code already used maps to CodeAlreadyUsed`() {
        val mapped = FirebasePairingService.mapHttpsError(err(FirebaseFunctionsException.Code.FAILED_PRECONDITION, "Code already used."))
        assertEquals(PairingError.CodeAlreadyUsed, mapped)
    }

    @Test
    fun `failed-precondition with Code expired maps to CodeExpired`() {
        val mapped = FirebasePairingService.mapHttpsError(err(FirebaseFunctionsException.Code.FAILED_PRECONDITION, "Code expired."))
        assertEquals(PairingError.CodeExpired, mapped)
    }

    @Test
    fun `unauthenticated maps to AppCheckRejected`() {
        val mapped = FirebasePairingService.mapHttpsError(err(FirebaseFunctionsException.Code.UNAUTHENTICATED, ""))
        assertEquals(PairingError.AppCheckRejected, mapped)
    }

    @Test
    fun `other code maps to Unknown`() {
        val mapped = FirebasePairingService.mapHttpsError(err(FirebaseFunctionsException.Code.INTERNAL, ""))
        assertEquals(PairingError.Unknown::class, mapped::class)
    }
}
