package net.maerkl.kassierapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class CheckoutResultClassifierTest {

    @Test
    fun `code 1 is a confirmed success`() {
        assertEquals(CheckoutVerdict.Success, CheckoutResultClassifier.classify(1))
    }

    @Test
    fun `unknown transaction status must never count as failed`() {
        // ERROR_UNKNOWN_TRANSACTION_STATUS: Verbindung brach mitten in der
        // Transaktion ab — die Abbuchung kann trotzdem durchgegangen sein.
        assertEquals(CheckoutVerdict.Unknown, CheckoutResultClassifier.classify(15))
    }

    @Test
    fun `missing result extras mean the outcome is unknown`() {
        assertEquals(CheckoutVerdict.Unknown, CheckoutResultClassifier.classify(null))
    }

    @Test
    fun `expired session codes fail and trigger a re-login`() {
        // ERROR_INVALID_TOKEN
        assertEquals(CheckoutVerdict.Failed(sessionExpired = true), CheckoutResultClassifier.classify(5))
        // ERROR_NOT_LOGGED_IN
        assertEquals(CheckoutVerdict.Failed(sessionExpired = true), CheckoutResultClassifier.classify(8))
    }

    @Test
    fun `declined or aborted transactions fail without re-login`() {
        // ERROR_TRANSACTION_FAILED (Karte abgelehnt / Kunde abgebrochen)
        assertEquals(CheckoutVerdict.Failed(sessionExpired = false), CheckoutResultClassifier.classify(2))
        // ERROR_NO_CONNECTIVITY vor Transaktionsstart
        assertEquals(CheckoutVerdict.Failed(sessionExpired = false), CheckoutResultClassifier.classify(6))
    }
}
