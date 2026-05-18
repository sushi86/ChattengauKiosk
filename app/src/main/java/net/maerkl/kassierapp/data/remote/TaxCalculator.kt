package net.maerkl.kassierapp.data.remote

import java.math.BigDecimal
import java.math.RoundingMode

data class TaxLineItem(
    val einzelpreis: Double,
    val anzahl: Int,
    val taxRate: Int,
)

data class TaxBreakdown(
    val gesamtbetrag: Double,
    val netto7: Double,
    val mwst7: Double,
    val netto19: Double,
    val mwst19: Double,
)

object TaxCalculator {
    private val HUNDRED = BigDecimal(100)

    fun compute(items: List<TaxLineItem>): TaxBreakdown {
        var gross = BigDecimal.ZERO
        var netto7 = BigDecimal.ZERO
        var mwst7 = BigDecimal.ZERO
        var netto19 = BigDecimal.ZERO
        var mwst19 = BigDecimal.ZERO

        for (item in items) {
            val lineGross = BigDecimal.valueOf(item.einzelpreis)
                .multiply(BigDecimal(item.anzahl))
            gross = gross.add(lineGross)

            when (item.taxRate) {
                7 -> {
                    val divisor = BigDecimal.ONE.add(BigDecimal(7).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                    val netto = lineGross.divide(divisor, 2, RoundingMode.HALF_UP)
                    netto7 = netto7.add(netto)
                    mwst7 = mwst7.add(lineGross.setScale(2, RoundingMode.HALF_UP).subtract(netto))
                }
                19 -> {
                    val divisor = BigDecimal.ONE.add(BigDecimal(19).divide(HUNDRED, 10, RoundingMode.HALF_UP))
                    val netto = lineGross.divide(divisor, 2, RoundingMode.HALF_UP)
                    netto19 = netto19.add(netto)
                    mwst19 = mwst19.add(lineGross.setScale(2, RoundingMode.HALF_UP).subtract(netto))
                }
                0 -> { /* no tax bucket */ }
            }
        }

        return TaxBreakdown(
            gesamtbetrag = gross.setScale(2, RoundingMode.HALF_UP).toDouble(),
            netto7 = netto7.setScale(2, RoundingMode.HALF_UP).toDouble(),
            mwst7 = mwst7.setScale(2, RoundingMode.HALF_UP).toDouble(),
            netto19 = netto19.setScale(2, RoundingMode.HALF_UP).toDouble(),
            mwst19 = mwst19.setScale(2, RoundingMode.HALF_UP).toDouble(),
        )
    }
}
