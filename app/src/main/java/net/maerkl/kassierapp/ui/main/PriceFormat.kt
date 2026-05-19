package net.maerkl.kassierapp.ui.main

import java.text.NumberFormat
import java.util.Locale

private val euroFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

fun Long.centsToEuroString(): String = euroFormat.format(this / 100.0)
