package net.maerkl.kassierapp.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class TaxCalculatorTest {

    @Test
    fun `placeholder zero rate produces no buckets`() {
        val result = TaxCalculator.compute(
            listOf(
                TaxLineItem(einzelpreis = 2.50, anzahl = 3, taxRate = 0),
                TaxLineItem(einzelpreis = 1.50, anzahl = 1, taxRate = 0),
            )
        )
        assertEquals(9.00, result.gesamtbetrag, 0.0001)
        assertEquals(0.0, result.netto7, 0.0001)
        assertEquals(0.0, result.mwst7, 0.0001)
        assertEquals(0.0, result.netto19, 0.0001)
        assertEquals(0.0, result.mwst19, 0.0001)
    }

    @Test
    fun `mixed 7 and 19 percent items split into correct buckets`() {
        // Cake 1.50 @ 7%: netto 1.40, mwst 0.10
        // Beer 2.00 @ 19%: netto 1.68, mwst 0.32
        val result = TaxCalculator.compute(
            listOf(
                TaxLineItem(einzelpreis = 1.50, anzahl = 1, taxRate = 7),
                TaxLineItem(einzelpreis = 2.00, anzahl = 1, taxRate = 19),
            )
        )
        assertEquals(3.50, result.gesamtbetrag, 0.0001)
        assertEquals(1.40, result.netto7, 0.0001)
        assertEquals(0.10, result.mwst7, 0.0001)
        assertEquals(1.68, result.netto19, 0.0001)
        assertEquals(0.32, result.mwst19, 0.0001)
    }

    @Test
    fun `quantity multiplies into bucket`() {
        // 3x cake 1.50 @ 7% = 4.50 gross, netto 4.21, mwst 0.29
        val result = TaxCalculator.compute(
            listOf(TaxLineItem(einzelpreis = 1.50, anzahl = 3, taxRate = 7))
        )
        assertEquals(4.50, result.gesamtbetrag, 0.0001)
        assertEquals(4.21, result.netto7, 0.0001)
        assertEquals(0.29, result.mwst7, 0.0001)
    }
}
