package net.maerkl.kassierapp.data.repository

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import net.maerkl.kassierapp.data.remote.SumupTxStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SumupTransactionVerifierTest {

    @Test
    fun `confirms immediately when the transaction is already successful`() = runTest {
        var calls = 0
        val outcome = SumupTransactionVerifier.verify(maxAttempts = 5, delayMs = 1_000) {
            calls++
            SumupTxStatus.Successful("TX42")
        }
        assertEquals(VerificationOutcome.Confirmed("TX42"), outcome)
        assertEquals(1, calls)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `keeps polling while pending and confirms once visible`() = runTest {
        var calls = 0
        val outcome = SumupTransactionVerifier.verify(maxAttempts = 5, delayMs = 1_000) {
            calls++
            if (calls < 3) SumupTxStatus.Pending else SumupTxStatus.Successful(null)
        }
        assertEquals(VerificationOutcome.Confirmed(null), outcome)
        assertEquals(3, calls)
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun `gives up as unverifiable after exhausting all attempts`() = runTest {
        var calls = 0
        val outcome = SumupTransactionVerifier.verify(maxAttempts = 4, delayMs = 1_000) {
            calls++
            SumupTxStatus.Pending
        }
        assertEquals(VerificationOutcome.Unverifiable, outcome)
        assertEquals(4, calls)
    }

    @Test
    fun `reports a definite failure without further polling`() = runTest {
        var calls = 0
        val outcome = SumupTransactionVerifier.verify(maxAttempts = 5, delayMs = 1_000) {
            calls++
            SumupTxStatus.Failed
        }
        assertEquals(VerificationOutcome.ConfirmedFailed, outcome)
        assertEquals(1, calls)
    }

    @Test
    fun `stops immediately when the token lacks the required scope`() = runTest {
        var calls = 0
        val outcome = SumupTransactionVerifier.verify(maxAttempts = 5, delayMs = 1_000) {
            calls++
            SumupTxStatus.Unauthorized
        }
        assertEquals(VerificationOutcome.Unverifiable, outcome)
        assertEquals(1, calls)
    }
}
