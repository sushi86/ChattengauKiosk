package net.maerkl.kassierapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SumupMerchantGuardTest {

    @Test
    fun `match when expected equals logged-in merchant`() {
        assertEquals(MerchantCheck.Match, SumupMerchantGuard.check("M123", "M123"))
    }

    @Test
    fun `match ignores surrounding whitespace`() {
        assertEquals(MerchantCheck.Match, SumupMerchantGuard.check(" M123 ", "M123"))
    }

    @Test
    fun `mismatch when logged-in merchant differs`() {
        assertEquals(MerchantCheck.Mismatch, SumupMerchantGuard.check("M123", "M999"))
    }

    @Test
    fun `unverifiable when backend has no merchant code`() {
        assertEquals(MerchantCheck.Unverifiable, SumupMerchantGuard.check(null, "M123"))
        assertEquals(MerchantCheck.Unverifiable, SumupMerchantGuard.check("  ", "M123"))
    }

    @Test
    fun `unverifiable when sdk reports no merchant`() {
        assertEquals(MerchantCheck.Unverifiable, SumupMerchantGuard.check("M123", null))
        assertEquals(MerchantCheck.Unverifiable, SumupMerchantGuard.check("M123", ""))
    }
}
