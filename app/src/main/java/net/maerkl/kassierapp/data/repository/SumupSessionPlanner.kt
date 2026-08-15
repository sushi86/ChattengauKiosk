package net.maerkl.kassierapp.data.repository

/** Was passieren muss, damit ein Checkout gegen eine gueltige SumUp-Session laeuft. */
sealed class SumupSessionAction {
    /** SDK-Session lebt und gehoert dem richtigen Verein — nur Token auffrischen. */
    data class Refresh(val accessToken: String) : SumupSessionAction()

    /** Keine SDK-Session (mehr) — Login-Activity mit frischem Backend-Token oeffnen. */
    data class Login(val accessToken: String) : SumupSessionAction()

    /** SDK ist auf einem fremden Merchant — erst ausloggen, dann neu einloggen. */
    data class LogoutAndLogin(val accessToken: String) : SumupSessionAction()

    /** Merchant nicht pruefbar — es darf nicht kassiert werden. */
    data object Blocked : SumupSessionAction()
}

/**
 * Das SumUp-Reader-SDK haelt eine eigene Session auf dem Geraet, deren Token
 * altert, waehrend die Kiosk-App tagelang im Vordergrund weiterlaeuft. Ohne
 * Auffrischen bzw. Neu-Login schlaegt die erste Kartenzahlung nach einer langen
 * Pause fehl. Diese Planung ist der einzige Ort, der entscheidet, was dann zu
 * tun ist — sie wird vor jeder Zahlung und beim Zurueckkehren in die App genutzt.
 */
object SumupSessionPlanner {
    fun plan(loggedIn: Boolean, session: SumupSession, sdkMerchantCode: String?): SumupSessionAction {
        if (!loggedIn) return SumupSessionAction.Login(session.accessToken)
        return when (SumupMerchantGuard.check(session.merchantCode, sdkMerchantCode)) {
            MerchantCheck.Match -> SumupSessionAction.Refresh(session.accessToken)
            MerchantCheck.Mismatch -> SumupSessionAction.LogoutAndLogin(session.accessToken)
            MerchantCheck.Unverifiable -> SumupSessionAction.Blocked
        }
    }

    /**
     * Checkout-Ergebnisse, die bedeuten "die SumUp-Session ist tot", nicht
     * "die Zahlung ist schiefgegangen" — danach lohnt ein Neu-Login.
     * Codes aus SumUpAPI.Response.ResultCode (hier ohne SDK-Abhaengigkeit, damit testbar).
     */
    fun isSessionExpiredResult(resultCode: Int?): Boolean =
        resultCode == RESULT_ERROR_INVALID_TOKEN || resultCode == RESULT_ERROR_NOT_LOGGED_IN

    const val RESULT_ERROR_INVALID_TOKEN = 5
    const val RESULT_ERROR_NOT_LOGGED_IN = 8
}
