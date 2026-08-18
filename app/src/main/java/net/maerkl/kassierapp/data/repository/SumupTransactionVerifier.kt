package net.maerkl.kassierapp.data.repository

import kotlinx.coroutines.delay
import net.maerkl.kassierapp.data.remote.SumupTxStatus

/** Endgueltiges Urteil nach dem Nachpruefen einer unklaren Kartenzahlung. */
sealed class VerificationOutcome {
    /** Zahlung ist durchgegangen — als Verkauf verbuchen. */
    data class Confirmed(val transactionCode: String?) : VerificationOutcome()

    /** Zahlung ist sicher nicht zustande gekommen — erneut kassieren ist ok. */
    data object ConfirmedFailed : VerificationOutcome()

    /** Nicht klaerbar — im Dashboard pruefen, keinesfalls automatisch erneut kassieren. */
    data object Unverifiable : VerificationOutcome()
}

/**
 * Pollt die Transactions-API, bis der Ausgang einer unklaren Zahlung feststeht
 * oder das Zeitbudget erschoepft ist. Bewusst konservativ: alles, was sich
 * nicht sicher klaeren laesst, endet als [VerificationOutcome.Unverifiable],
 * niemals als Fehlschlag — sonst droht wieder eine Doppelabbuchung.
 */
object SumupTransactionVerifier {
    suspend fun verify(
        maxAttempts: Int,
        delayMs: Long,
        fetchStatus: suspend () -> SumupTxStatus,
    ): VerificationOutcome {
        repeat(maxAttempts) { attempt ->
            when (val status = fetchStatus()) {
                is SumupTxStatus.Successful -> return VerificationOutcome.Confirmed(status.transactionCode)
                SumupTxStatus.Failed -> return VerificationOutcome.ConfirmedFailed
                SumupTxStatus.Unauthorized -> return VerificationOutcome.Unverifiable
                SumupTxStatus.Pending -> if (attempt < maxAttempts - 1) delay(delayMs)
            }
        }
        return VerificationOutcome.Unverifiable
    }
}
