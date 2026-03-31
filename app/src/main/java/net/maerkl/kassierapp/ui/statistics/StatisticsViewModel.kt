package net.maerkl.kassierapp.ui.statistics

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.Flow
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.local.ArticleDaySummary
import net.maerkl.kassierapp.data.local.DailySummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val saleDao = app.database.saleDao()

    val dailySummaries: Flow<List<DailySummary>> = saleDao.getDailySummaries()

    fun getArticleSummaries(dayTimestamp: Long): Flow<List<ArticleDaySummary>> {
        return saleDao.getArticleSummariesForDay(dayTimestamp, dayTimestamp + 86_400_000)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("EE, dd.MM.yyyy", Locale.GERMANY)
        return sdf.format(Date(timestamp))
    }

    fun exportCsv(dayTimestamp: Long, articles: List<ArticleDaySummary>): Intent {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(dayTimestamp))
        val bom = "\uFEFF"
        val header = "Datum;Artikel;Anzahl Bar;Umsatz Bar;Anzahl Karte;Umsatz Karte;Anzahl Gesamt;Umsatz Gesamt"

        val rows = articles.map { a ->
            val totalQty = a.cashQuantity + a.cardQuantity
            val totalRev = a.cashRevenue + a.cardRevenue
            "$dateStr;${a.articleEmoji} ${a.articleName};${a.cashQuantity};${fmt(a.cashRevenue)};${a.cardQuantity};${fmt(a.cardRevenue)};$totalQty;${fmt(totalRev)}"
        }

        val totalCashRev = articles.sumOf { it.cashRevenue }
        val totalCardRev = articles.sumOf { it.cardRevenue }
        val totalRev = totalCashRev + totalCardRev
        val totalCashQty = articles.sumOf { it.cashQuantity }
        val totalCardQty = articles.sumOf { it.cardQuantity }
        val totalQty = totalCashQty + totalCardQty
        val sumRow = ";GESAMT;$totalCashQty;${fmt(totalCashRev)};$totalCardQty;${fmt(totalCardRev)};$totalQty;${fmt(totalRev)}"

        val csv = bom + header + "\n" + rows.joinToString("\n") + "\n" + sumRow + "\n"

        val file = File(app.cacheDir, "umsatz-$dateStr.csv")
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Umsatz $dateStr")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun fmt(value: Double): String {
        return String.format(Locale.GERMANY, "%.2f", value)
    }
}
