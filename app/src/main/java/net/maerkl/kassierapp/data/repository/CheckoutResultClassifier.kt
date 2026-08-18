package net.maerkl.kassierapp.data.repository

/** Was ein SumUp-Checkout-Ergebnis fuer die Kasse bedeutet. */
sealed class CheckoutVerdict {
    /** Zahlung bestaetigt durchgegangen. */
    data object Success : CheckoutVerdict()

    /**
     * Ausgang unbekannt — die Abbuchung kann durchgegangen sein. Es darf auf
     * keinen Fall erneut kassiert werden, bevor der Status geprueft wurde.
     */
    data object Unknown : CheckoutVerdict()

    /** Zahlung sicher nicht zustande gekommen. */
    data class Failed(val sessionExpired: Boolean) : CheckoutVerdict()
}

/**
 * Uebersetzt den Result-Code aus SumUpAPI.Response in ein Kassen-Urteil.
 * Kernpunkt: ERROR_UNKNOWN_TRANSACTION_STATUS (15) und fehlende Extras sind
 * KEIN Fehlschlag — genau diese Verwechslung hat am 16.08.2026 zu
 * Doppelabbuchungen gefuehrt. Codes ohne SDK-Abhaengigkeit, damit testbar.
 */
object CheckoutResultClassifier {
    private const val RESULT_SUCCESSFUL = 1
    private const val RESULT_ERROR_UNKNOWN_TRANSACTION_STATUS = 15

    fun classify(resultCode: Int?): CheckoutVerdict = when (resultCode) {
        RESULT_SUCCESSFUL -> CheckoutVerdict.Success
        null, RESULT_ERROR_UNKNOWN_TRANSACTION_STATUS -> CheckoutVerdict.Unknown
        else -> CheckoutVerdict.Failed(
            sessionExpired = SumupSessionPlanner.isSessionExpiredResult(resultCode)
        )
    }
}
