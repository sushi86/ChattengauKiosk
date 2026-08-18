package net.maerkl.kassierapp.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import kotlinx.coroutines.delay
import net.maerkl.kassierapp.R
import net.maerkl.kassierapp.data.remote.Artikel
import net.maerkl.kassierapp.data.remote.Sortiment
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
    val uiState by viewModel.uiState.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val paymentFeedback by viewModel.paymentFeedback.collectAsState()
    val paymentInProgress by viewModel.paymentInProgress.collectAsState()
    var batteryPercent by remember { mutableStateOf(0) }
    var batteryCharging by remember { mutableStateOf(false) }
    var batteryEta by remember { mutableStateOf<String?>(null) }
    var wifiName by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf("") }
    var showTransactionHistory by remember { mutableStateOf(false) }
    var showSortimentSwitch by remember { mutableStateOf(false) }
    var showFreierPreis by remember { mutableStateOf(false) }
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

    LaunchedEffect(uiState is KassenUiState.Ready) {
        if (uiState !is KassenUiState.Ready) showSortimentSwitch = false
    }

    val readyState = uiState as? KassenUiState.Ready
    val collectionName = readyState?.sortiment?.name ?: ""

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
                            text = if (sumUpLoggedIn) "✅ Terminal bereit" else "❌ Nicht verbunden",
                            color = if (sumUpLoggedIn) Color(0xFF90EE90) else Color(0xFFFF6B6B),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (collectionName.isNotBlank()) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "📁 $collectionName",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(enabled = readyState != null) {
                                    showSortimentSwitch = true
                                }
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
                        text = if (wifiName != null) "📶 $wifiName" else "📵 Kein WLAN",
                        color = if (wifiName != null) Color.White else Color(0xFFFF6B6B),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    val etaSuffix = if (!batteryCharging && batteryEta != null) " ($batteryEta)" else ""
                    Text(
                        text = if (batteryCharging) "⚡ $batteryPercent%" else "🔋 $batteryPercent%$etaSuffix",
                        color = batteryColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    if (currentTime.isNotBlank()) {
                        Text(
                            text = "🕐 $currentTime",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clickable { showTransactionHistory = true }
                        )
                    }
                    TextButton(onClick = onNavigateToSettings) {
                        Text("⚙️ Einstellungen", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Green900
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is KassenUiState.Loading, is KassenUiState.NotPaired -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is KassenUiState.NoSortimente -> {
                    Text(
                        text = "Noch keine Sortimente angelegt. Bitte im Admin-Portal ein Sortiment erstellen.",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        textAlign = TextAlign.Center
                    )
                }

                is KassenUiState.ChooseSortiment -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Sortiment auswählen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        state.sortimente.forEach { s ->
                            Button(onClick = { viewModel.selectSortiment(s.id) }) {
                                Text(s.name)
                            }
                        }
                    }
                }

                is KassenUiState.Ready -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        val articlesWithFreierPreis = state.articles + MainViewModel.FreierPreisSentinel
                        BoxWithConstraints(modifier = Modifier.weight(1f)) {
                            val spacing = 8.dp
                            val padding = 8.dp
                            val availW = maxWidth - padding * 2
                            val availH = maxHeight - padding * 2
                            val refCardW = (availW - spacing * 4) / 5
                            val totalItems = articlesWithFreierPreis.size

                            var bestCols = 5
                            var bestCardW = refCardW
                            for (cols in 1..8) {
                                val rows = (totalItems + cols - 1) / cols
                                val cardW = (availW - spacing * (cols - 1)) / cols
                                val cardH = (availH - spacing * (rows - 1)) / rows
                                val size = if (cardW < cardH) cardW else cardH
                                if (size >= refCardW && size > bestCardW) {
                                    bestCardW = size
                                    bestCols = cols
                                }
                            }
                            val scale = (bestCardW / refCardW).coerceIn(1f, 2.5f)

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(bestCols),
                                contentPadding = PaddingValues(padding),
                                horizontalArrangement = Arrangement.spacedBy(spacing),
                                verticalArrangement = Arrangement.spacedBy(spacing)
                            ) {
                                items(articlesWithFreierPreis, key = { it.id }) { artikel ->
                                    if (artikel.id == MainViewModel.FREIER_PREIS_SENTINEL_ID) {
                                        FreierPreisCard(scale = scale, onClick = { showFreierPreis = true })
                                    } else {
                                        ArtikelCard(
                                            artikel = artikel,
                                            scale = scale,
                                            onClick = { viewModel.addToCart(artikel) }
                                        )
                                    }
                                }
                            }
                        }

                        CartPanel(
                            cart = cart,
                            totalCent = viewModel.cartTotalCent,
                            paymentInProgress = paymentInProgress,
                            onRemove = { viewModel.removeFromCart(it) },
                            onClear = { viewModel.clearCart() },
                            onCashPayment = { viewModel.cashPayment() },
                            onCheckout = { viewModel.checkout() },
                            modifier = Modifier.width(360.dp)
                        )
                    }
                }
            }

            // Liegt ueber dem gesamten Kassenbereich — zuletzt in der Box, also oben.
            paymentFeedback?.let { feedback ->
                PaymentFeedbackOverlay(
                    feedback = feedback,
                    onDismiss = { viewModel.dismissPaymentFeedback() }
                )
            }
        }
    }

    if (showSortimentSwitch && readyState != null) {
        SwitchSortimentDialog(
            current = readyState.sortiment,
            all = readyState.allSortimente,
            onSelect = { viewModel.selectSortiment(it) },
            onDismiss = { showSortimentSwitch = false }
        )
    }

    if (showTransactionHistory) {
        TransactionHistoryDialog(
            viewModel = viewModel,
            onDismiss = { showTransactionHistory = false }
        )
    }

    if (showFreierPreis) {
        FreierPreisDialog(
            onDismiss = { showFreierPreis = false },
            onConfirm = { name, preisCent, taxRate ->
                viewModel.addFreierPreis(name, preisCent, taxRate)
                showFreierPreis = false
            },
        )
    }
}

@Composable
private fun FreierPreisCard(scale: Float, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding((16 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size((56 * scale).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(MainViewModel.FREIER_PREIS_EMOJI, fontSize = (40 * scale).sp)
            }
            Spacer(modifier = Modifier.height((8 * scale).dp))
            AutoSizeText(
                text = MainViewModel.FREIER_PREIS_DEFAULT_NAME,
                fontWeight = FontWeight.Bold,
                maxFontSize = (16 * scale).sp,
                minFontSize = 10.sp
            )
            Text(
                "Preis wählen",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = (14 * scale).sp
            )
        }
    }
}

@Composable
private fun ArtikelCard(artikel: Artikel, scale: Float, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding((16 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArtikelImageOrEmoji(artikel, scale = scale)
            Spacer(modifier = Modifier.height((8 * scale).dp))
            AutoSizeText(
                text = artikel.name,
                fontWeight = FontWeight.Bold,
                maxFontSize = (16 * scale).sp,
                minFontSize = 10.sp
            )
            Text(
                artikel.preisCent.centsToEuroString(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = (14 * scale).sp
            )
        }
    }
}

@Composable
private fun ArtikelImageOrEmoji(artikel: Artikel, scale: Float) {
    val imagePath = artikel.imagePath
    val boxSize = (56 * scale).dp
    val emojiSize = (40 * scale).sp

    Box(
        modifier = Modifier.size(boxSize),
        contentAlignment = Alignment.Center
    ) {
        if (imagePath.isNullOrBlank()) {
            Text(artikel.emoji ?: "📦", fontSize = emojiSize)
            return@Box
        }

        val urlState = produceState<String?>(initialValue = null, key1 = imagePath) {
            value = try {
                Firebase.storage.reference.child(imagePath).downloadUrl.await().toString()
            } catch (_: Exception) {
                null
            }
        }
        val url = urlState.value
        var loadFailed by remember(imagePath) { mutableStateOf(false) }

        if (url == null || loadFailed) {
            Text(artikel.emoji ?: "📦", fontSize = emojiSize)
            return@Box
        }

        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = artikel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            onError = { loadFailed = true },
        )
    }
}

@Composable
private fun CartPanel(
    cart: List<CartItem>,
    totalCent: Long,
    paymentInProgress: Boolean,
    onRemove: (Artikel) -> Unit,
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
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(cart, key = { _, item -> item.artikel.id }) { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Mindesthoehe: eine Warenkorb-Zeile ist auch ein
                            // Touch-Ziel (Antippen entfernt eine Position).
                            .heightIn(min = 56.dp)
                            .background(if (index % 2 == 0) Color(0xFFE3F2FD) else Color.Transparent)
                            .clickable { onRemove(item.artikel) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${item.quantity}× ${item.artikel.name}",
                            modifier = Modifier.weight(1f),
                            fontSize = 18.sp
                        )
                        Text(
                            (item.artikel.preisCent * item.quantity).centsToEuroString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "−",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                "Gesamt: ${totalCent.centsToEuroString()}",
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            // Grosse, gut treffbare Zahl-Buttons: das Tablet wird im Stehen,
            // oft in Handschuhen bedient. "Kassieren" ist die Hauptaktion und
            // darum am groessten.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCheckout,
                    // Waehrend eine Kartenzahlung laeuft, keinen zweiten Checkout
                    // zulassen — Doppel-Tap hiess sonst doppelt kassieren.
                    enabled = !isEmpty && !paymentInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    contentPadding = PaddingValues(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Green900)
                ) {
                    Text("💳 Kassieren", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onCashPayment,
                    enabled = !isEmpty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    contentPadding = PaddingValues(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("💵 Barzahlung", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onClear,
                    enabled = !isEmpty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentPadding = PaddingValues(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("🗑️ Leeren", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Grossflaechige Rueckmeldung nach einer Zahlung. Ein Toast geht im
 * Verkaufsstress unter — darum fuellt diese Einblendung den Kassenbereich
 * und zeigt ein Symbol, das man aus zwei Metern erkennt. Erfolg und
 * Fehlschlag schliessen sich nach einem Countdown von selbst; die beiden
 * "unklar"-Zustaende bleiben stehen — waehrend der Pruefung laesst sich die
 * Einblendung bewusst gar nicht wegtippen, damit niemand vorschnell erneut
 * kassiert.
 */
@Composable
private fun PaymentFeedbackOverlay(feedback: PaymentFeedback, onDismiss: () -> Unit) {
    val background = when (feedback) {
        is PaymentFeedback.Success -> Color(0xFF1B7F3B)
        is PaymentFeedback.Failed -> Color(0xFFB3261E)
        is PaymentFeedback.Verifying, is PaymentFeedback.Unverified -> Color(0xFFB26A00)
    }
    val title = when (feedback) {
        is PaymentFeedback.Success -> feedback.title
        is PaymentFeedback.Failed -> "Zahlung fehlgeschlagen"
        is PaymentFeedback.Verifying -> "Zahlungsstatus wird geprüft…"
        is PaymentFeedback.Unverified -> "Zahlungsstatus unklar"
    }
    val detail = when (feedback) {
        is PaymentFeedback.Success -> feedback.detail
        is PaymentFeedback.Failed -> feedback.reason
        is PaymentFeedback.Verifying -> feedback.detail
        is PaymentFeedback.Unverified -> feedback.detail
    }
    val warning = when (feedback) {
        is PaymentFeedback.Verifying -> "NICHT erneut kassieren!"
        is PaymentFeedback.Unverified -> "NICHT erneut kassieren!\nZahlung im SumUp-Dashboard prüfen."
        else -> null
    }
    val autoDismiss = feedback is PaymentFeedback.Success || feedback is PaymentFeedback.Failed
    val dismissible = feedback !is PaymentFeedback.Verifying

    // Der Countdown ist zugleich die Uhr: laeuft er ab, schliesst sich die
    // Einblendung — so kann Anzeige und tatsaechliche Dauer nicht auseinanderlaufen.
    var secondsLeft by remember(feedback) { mutableStateOf(FEEDBACK_SECONDS) }
    if (autoDismiss) {
        LaunchedEffect(feedback) {
            while (secondsLeft > 0) {
                delay(1_000L)
                secondsLeft--
            }
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .clickable(
                // Kein Ripple/Highlight: die Flaeche ist eine Meldung, kein Button.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = dismissible,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                when (feedback) {
                    is PaymentFeedback.Verifying -> CircularProgressIndicator(
                        color = background,
                        strokeWidth = 10.dp,
                        modifier = Modifier.size(130.dp)
                    )
                    else -> Icon(
                        imageVector = when (feedback) {
                            is PaymentFeedback.Success -> Icons.Filled.Check
                            is PaymentFeedback.Failed -> Icons.Filled.Close
                            else -> Icons.Filled.Warning
                        },
                        contentDescription = null,
                        tint = background,
                        modifier = Modifier.size(150.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            detail?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            warning?.let {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            if (autoDismiss) {
                Spacer(modifier = Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$secondsLeft",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = when {
                    autoDismiss -> "schließt automatisch · zum Schließen tippen"
                    dismissible -> "zum Schließen tippen"
                    else -> "bitte warten…"
                },
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 20.sp
            )
        }
    }
}

private const val FEEDBACK_SECONDS = 3

@Composable
private fun AutoSizeText(
    text: String,
    fontWeight: FontWeight,
    maxFontSize: androidx.compose.ui.unit.TextUnit,
    minFontSize: androidx.compose.ui.unit.TextUnit,
    maxLines: Int = 1
) {
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }

    Text(
        text = text,
        fontWeight = fontWeight,
        fontSize = fontSize,
        lineHeight = maxFontSize * 1.2f,
        maxLines = maxLines,
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
private fun SwitchSortimentDialog(
    current: Sortiment,
    all: List<Sortiment>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sortiment wechseln") },
        text = {
            Column {
                all.forEach { s ->
                    TextButton(
                        onClick = { onSelect(s.id); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = (if (s.id == current.id) "✓ " else "") + s.name,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

@Composable
private fun TransactionHistoryDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val transactions by viewModel.todayTransactions.collectAsState()
    var refundTarget by remember { mutableStateOf<net.maerkl.kassierapp.data.local.Transaction?>(null) }
    val refundInProgress by viewModel.refundInProgress.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaktionen heute", fontWeight = FontWeight.Bold) },
        text = {
            if (transactions.isEmpty()) {
                Text("Noch keine Transaktionen heute.", color = Color.Gray)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(transactions, key = { it.transaction.id }) { txWithSales ->
                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(txWithSales.transaction.timestamp))
                        val methodIcon = if (txWithSales.transaction.paymentMethod == "sumup") "💳" else "💵"

                        val isRefunded = txWithSales.transaction.refunded
                        val textAlpha = if (isRefunded) 0.4f else 1f
                        val textDecoration = if (isRefunded) TextDecoration.LineThrough else TextDecoration.None

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$time  $methodIcon",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = textDecoration,
                                    modifier = Modifier.alpha(textAlpha)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = String.format("%.2f €", txWithSales.transaction.totalAmount),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = textDecoration,
                                        modifier = Modifier.alpha(textAlpha)
                                    )
                                    if (isRefunded) {
                                        Text(
                                            text = "  Storniert",
                                            fontSize = 12.sp,
                                            color = Color.Red,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            txWithSales.sales.forEach { sale ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${sale.articleEmoji} ${sale.quantity}× ${sale.articleName}",
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        textDecoration = textDecoration,
                                        modifier = Modifier.alpha(textAlpha)
                                    )
                                    Text(
                                        text = String.format("%.2f €", sale.articlePrice * sale.quantity),
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        textDecoration = textDecoration,
                                        modifier = Modifier.alpha(textAlpha)
                                    )
                                }
                            }
                            if (!isRefunded) {
                                Button(
                                    onClick = { refundTarget = txWithSales.transaction },
                                    enabled = !refundInProgress,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Stornieren", fontSize = 12.sp)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schliessen") }
        }
    )

    refundTarget?.let { transaction ->
        val isCard = transaction.paymentMethod == "sumup"
        AlertDialog(
            onDismissRequest = { refundTarget = null },
            title = { Text("Transaktion stornieren?") },
            text = {
                Text(
                    if (isCard)
                        "Möchtest du diese Transaktion wirklich stornieren? Der Betrag wird auf die Karte zurückerstattet."
                    else
                        "Möchtest du diese Transaktion wirklich stornieren?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.refundTransaction(transaction)
                        refundTarget = null
                    }
                ) {
                    Text("Stornieren", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { refundTarget = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
