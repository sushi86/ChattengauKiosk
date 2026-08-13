package net.maerkl.kassierapp.data.repository

sealed class MerchantCheck {
    data object Match : MerchantCheck()
    data object Mismatch : MerchantCheck()
    data object Unverifiable : MerchantCheck()
}

/**
 * Vergleicht den vom Backend erwarteten Merchant des gekoppelten Vereins mit
 * dem im SumUp-Reader-SDK eingeloggten Merchant. Nur bei [MerchantCheck.Match]
 * darf ein Checkout gestartet werden — alles andere hiesse, das Geld landet
 * moeglicherweise auf dem SumUp-Konto eines anderen Vereins.
 */
object SumupMerchantGuard {
    fun check(expectedMerchantCode: String?, loggedInMerchantCode: String?): MerchantCheck {
        val expected = expectedMerchantCode?.trim().orEmpty()
        val actual = loggedInMerchantCode?.trim().orEmpty()
        return when {
            expected.isEmpty() || actual.isEmpty() -> MerchantCheck.Unverifiable
            expected == actual -> MerchantCheck.Match
            else -> MerchantCheck.Mismatch
        }
    }
}
