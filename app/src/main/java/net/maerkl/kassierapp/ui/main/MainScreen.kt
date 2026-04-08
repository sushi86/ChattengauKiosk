package net.maerkl.kassierapp.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import net.maerkl.kassierapp.R
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.isManualPrice
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import net.maerkl.kassierapp.ui.components.ManualPriceDialog
import net.maerkl.kassierapp.ui.theme.Green900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    sumUpLoggedIn: Boolean,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val articles by viewModel.articles.collectAsState(initial = emptyList())
    val cart by viewModel.cart.collectAsState()
    val remainingStock by viewModel.remainingStock.collectAsState()
    val collectionName by viewModel.activeCollectionName.collectAsState()
    var manualPriceArticle by remember { mutableStateOf<Article?>(null) }
    var stockEditArticle by remember { mutableStateOf<Article?>(null) }
    var batteryPercent by remember { mutableStateOf(0) }
    var batteryCharging by remember { mutableStateOf(false) }
    var batteryEta by remember { mutableStateOf<String?>(null) }
    var wifiName by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf("") }
    val batteryReadings = remember { mutableListOf<Pair<Long, Int>>() }

    LaunchedEffect(Unit) {
        while (true) {
            // Battery
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) batteryPercent = (level * 100) / scale
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            batteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // Battery ETA
            if (batteryCharging) {
                batteryReadings.clear()
                batteryEta = null
            } else {
                val now = System.currentTimeMillis()
                batteryReadings.add(now to batteryPercent)
                // Keep last 10 minutes of readings
                val cutoff = now - 10 * 60 * 1000L
                batteryReadings.removeAll { it.first < cutoff }

                if (batteryReadings.size >= 2) {
                    val first = batteryReadings.first()
                    val last = batteryReadings.last()
                    val elapsedMin = (last.first - first.first) / 60_000.0
                    val droppedPercent = first.second - last.second
                    if (droppedPercent > 0 && elapsedMin >= 2.0) {
                        val minPerPercent = elapsedMin / droppedPercent
                        val remainingMin = (last.second * minPerPercent).toInt()
                        val hours = remainingMin / 60
                        val mins = remainingMin % 60
                        batteryEta = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    }
                }
            }

            // WiFi
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (hasWifi) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val ssid = wm.connectionInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
                wifiName = if (ssid != null && ssid != "<unknown ssid>") ssid else "Verbunden"
            } else {
                wifiName = null
            }

            // Clock
            val cal = java.util.Calendar.getInstance()
            currentTime = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))

            delay(30_000L)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.fsg_logo),
                            contentDescription = "FSG Logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("FSG Chattengau/Metze", color = Color.White)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = if (sumUpLoggedIn) "\u2705 Terminal bereit" else "\u274C Nicht verbunden",
                            color = if (sumUpLoggedIn) Color(0xFF90EE90) else Color(0xFFFF6B6B),
                            fontSize = 13.sp
                        )
                        if (collectionName.isNotBlank()) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "\uD83D\uDCC1 $collectionName",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                actions = {
                    val batteryColor = when {
                        batteryCharging -> Color(0xFF90EE90)
                        batteryPercent <= 15 -> Color(0xFFFF6B6B)
                        batteryPercent <= 30 -> Color(0xFFFFD700)
                        else -> Color.White
                    }
                    Text(
                        text = if (wifiName != null) "\uD83D\uDCF6 $wifiName" else "\uD83D\uDCF5 Kein WLAN",
                        color = if (wifiName != null) Color.White else Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    val etaSuffix = if (!batteryCharging && batteryEta != null) " ($batteryEta)" else ""
                    Text(
                        text = if (batteryCharging) "\u26A1 $batteryPercent%" else "\uD83D\uDD0B $batteryPercent%$etaSuffix",
                        color = batteryColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    if (currentTime.isNotBlank()) {
                        Text(
                            text = currentTime,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                    TextButton(onClick = onNavigateToSettings) {
                        Text("\u2699\uFE0F Einstellungen", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green900
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(articles, key = { it.id }) { article ->
                    ArticleCard(
                        article = article,
                        remainingStock = remainingStock[article.name],
                        onClick = {
                            if (article.isManualPrice) {
                                manualPriceArticle = article
                            } else {
                                viewModel.addToCart(article)
                            }
                        },
                        onLongClick = {
                            if (!article.isManualPrice) {
                                stockEditArticle = article
                            }
                        }
                    )
                }
            }

            CartPanel(
                cart = cart,
                total = viewModel.cartTotal,
                onRemove = { viewModel.removeFromCart(it) },
                onClear = { viewModel.clearCart() },
                onCashPayment = { viewModel.cashPayment() },
                onCheckout = { viewModel.checkout() },
                modifier = Modifier.width(300.dp)
            )
        }
    }

    manualPriceArticle?.let { article ->
        ManualPriceDialog(
            onDismiss = { manualPriceArticle = null },
            onConfirm = { price, name ->
                viewModel.addManualPriceToCart(price, name, article)
                manualPriceArticle = null
            }
        )
    }

    stockEditArticle?.let { article ->
        StockEditDialog(
            article = article,
            currentStock = remainingStock[article.name],
            onDismiss = { stockEditArticle = null },
            onSave = { newQuantity ->
                viewModel.updateStockQuantity(article, newQuantity)
                stockEditArticle = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArticleCard(article: Article, remainingStock: Int?, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(article.emoji, fontSize = 40.sp)
                Spacer(modifier = Modifier.height(8.dp))
                AutoSizeText(
                    text = if (article.isManualPrice) "Freier Preis" else article.name,
                    fontWeight = FontWeight.Bold,
                    maxFontSize = 16.sp,
                    minFontSize = 10.sp
                )
                if (!article.isManualPrice) {
                    Text(
                        String.format("%.2f €", article.price),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (remainingStock != null) {
                val badgeColor = if (remainingStock > 0) Color(0xFF666666) else Color.Red
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(badgeColor, shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (remainingStock > 0) "$remainingStock" else "0",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CartPanel(
    cart: List<CartItem>,
    total: Double,
    onRemove: (Article) -> Unit,
    onClear: () -> Unit,
    onCashPayment: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmpty = cart.isEmpty()

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxHeight()) {
            Text(
                "Warenkorb",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(cart, key = { _, item -> item.article.id }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (index % 2 == 0) Color(0xFFE8F5E9) else Color.Transparent)
                            .clickable { onRemove(item.article) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${item.quantity}\u00D7 ${item.article.name}",
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Text(
                            "${String.format("%.2f", item.article.price * item.quantity)} \u20AC",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "\u2212",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Gesamt: ${String.format("%.2f", total)} \u20AC",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCheckout,
                    enabled = !isEmpty,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green900)
                ) {
                    Text("\uD83D\uDCB3 Kassieren")
                }
                Button(
                    onClick = onCashPayment,
                    enabled = !isEmpty,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("\uD83D\uDCB5 Barzahlung")
                }
                Button(
                    onClick = onClear,
                    enabled = !isEmpty,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("\uD83D\uDDD1\uFE0F Leeren")
                }
            }
        }
    }
}

@Composable
private fun AutoSizeText(
    text: String,
    fontWeight: FontWeight,
    maxFontSize: androidx.compose.ui.unit.TextUnit,
    minFontSize: androidx.compose.ui.unit.TextUnit
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        fontWeight = fontWeight,
        fontSize = fontSize,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minFontSize) {
                fontSize = (fontSize.value - 1).sp
            } else {
                readyToDraw = true
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (readyToDraw) 1f else 0f)
    )
}

@Composable
private fun StockEditDialog(
    article: Article,
    currentStock: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit
) {
    var stockText by remember { mutableStateOf(currentStock?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "${article.emoji} ${article.name}",
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Verbleibende Menge", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val current = stockText.toIntOrNull() ?: 0
                            if (current > 0) stockText = (current - 1).toString()
                        },
                        modifier = Modifier.size(56.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("\u2212", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.width(100.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            val current = stockText.toIntOrNull() ?: 0
                            stockText = (current + 1).toString()
                        },
                        modifier = Modifier.size(56.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green900)
                    ) {
                        Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (currentStock != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { onSave(null) },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text("Entfernen", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val stock = stockText.toIntOrNull()
                if (stock != null) onSave(stock)
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
