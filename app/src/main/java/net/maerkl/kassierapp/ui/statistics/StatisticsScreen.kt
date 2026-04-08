package net.maerkl.kassierapp.ui.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maerkl.kassierapp.data.local.ArticleDaySummary
import net.maerkl.kassierapp.ui.theme.Green900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: () -> Unit,
    onShareCsv: (android.content.Intent) -> Unit
) {
    val dailySummaries by viewModel.dailySummaries.collectAsState(initial = emptyList())
    var selectedDay by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectedDay != null) viewModel.formatDate(selectedDay!!) else "Statistik",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    TextButton(onClick = {
                        if (selectedDay != null) selectedDay = null else onNavigateBack()
                    }) {
                        Text("\u2190 Zur\u00FCck", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
            )
        }
    ) { padding ->
        if (selectedDay == null) {
            DaySummaryList(
                summaries = dailySummaries,
                viewModel = viewModel,
                onDaySelected = { selectedDay = it },
                modifier = Modifier.padding(padding)
            )
        } else {
            DayDetailView(
                dayTimestamp = selectedDay!!,
                viewModel = viewModel,
                onShareCsv = onShareCsv,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun DaySummaryList(
    summaries: List<net.maerkl.kassierapp.data.local.DailySummary>,
    viewModel: StatisticsViewModel,
    onDaySelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summaries.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Noch keine Verk\u00E4ufe erfasst", fontSize = 16.sp, color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(summaries, key = { it.dayTimestamp }) { summary ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDaySelected(summary.dayTimestamp) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            viewModel.formatDate(summary.dayTimestamp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "${summary.totalItems} Artikel verkauft",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    Text(
                        String.format("%.2f \u20AC", summary.totalRevenue),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Green900
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailView(
    dayTimestamp: Long,
    viewModel: StatisticsViewModel,
    onShareCsv: (android.content.Intent) -> Unit,
    modifier: Modifier = Modifier
) {
    val articles by viewModel.getArticleSummaries(dayTimestamp).collectAsState(initial = emptyList())

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tagesbericht", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { onShareCsv(viewModel.exportCsv(dayTimestamp, articles)) },
                colors = ButtonDefaults.buttonColors(containerColor = Green900)
            ) {
                Text("\uD83D\uDCE4 CSV Export")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Table header
        TableHeaderRow()

        HorizontalDivider()

        // Article rows
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(articles, key = { it.articleName }) { article ->
                ArticleRow(article)
                HorizontalDivider()
            }
        }

        // Totals row
        HorizontalDivider(thickness = 2.dp)
        TotalsRow(articles)
    }
}

@Composable
private fun TableHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Artikel", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
        Text("Bar", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text("Karte", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text("Storno", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 13.sp, color = Color.Red)
        Text("Gesamt", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 13.sp)
    }
}

@Composable
private fun ArticleRow(article: ArticleDaySummary) {
    val totalQty = article.cashQuantity + article.cardQuantity
    val totalRev = article.cashRevenue + article.cardRevenue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "${article.articleEmoji} ${article.articleName}",
            modifier = Modifier.weight(2f),
            fontSize = 14.sp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("${article.cashQuantity}x", fontSize = 13.sp)
            Text(String.format("%.2f \u20AC", article.cashRevenue), fontSize = 12.sp, color = Color.Gray)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${article.cardQuantity}x", fontSize = 13.sp)
            Text(String.format("%.2f \u20AC", article.cardRevenue), fontSize = 12.sp, color = Color.Gray)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${article.refundedQuantity}x", fontSize = 13.sp, color = Color.Red)
            Text(String.format("%.2f \u20AC", article.refundedRevenue), fontSize = 12.sp, color = Color.Red)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${totalQty}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f \u20AC", totalRev), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TotalsRow(articles: List<ArticleDaySummary>) {
    val totalCashQty = articles.sumOf { it.cashQuantity }
    val totalCashRev = articles.sumOf { it.cashRevenue }
    val totalCardQty = articles.sumOf { it.cardQuantity }
    val totalCardRev = articles.sumOf { it.cardRevenue }
    val totalRefundQty = articles.sumOf { it.refundedQuantity }
    val totalRefundRev = articles.sumOf { it.refundedRevenue }
    val totalQty = totalCashQty + totalCardQty
    val totalRev = totalCashRev + totalCardRev

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("GESAMT", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
        Column(modifier = Modifier.weight(1f)) {
            Text("${totalCashQty}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f \u20AC", totalCashRev), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${totalCardQty}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(String.format("%.2f \u20AC", totalCardRev), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${totalRefundQty}x", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Red)
            Text(String.format("%.2f \u20AC", totalRefundRev), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Red)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("${totalQty}x", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                String.format("%.2f \u20AC", totalRev),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Green900
            )
        }
    }
}
