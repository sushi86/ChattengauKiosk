package net.maerkl.kassierapp.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maerkl.kassierapp.R
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.isManualPrice
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
    val articles by viewModel.articles.collectAsState(initial = emptyList())
    val cart by viewModel.cart.collectAsState()
    var manualPriceArticle by remember { mutableStateOf<Article?>(null) }

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
                    }
                },
                actions = {
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
                        onClick = {
                            if (article.isManualPrice) {
                                manualPriceArticle = article
                            } else {
                                viewModel.addToCart(article)
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
}

@Composable
private fun ArticleCard(article: Article, onClick: () -> Unit) {
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
                    String.format("%.2f \u20AC", article.price),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
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
                items(cart, key = { it.article.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${item.quantity}\u00D7 ${item.article.name}",
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp
                        )
                        Text(
                            "${String.format("%.2f", item.article.price * item.quantity)} \u20AC",
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { onRemove(item.article) }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Entfernen",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
