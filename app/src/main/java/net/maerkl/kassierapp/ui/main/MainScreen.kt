package net.maerkl.kassierapp.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.ui.theme.Green900

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToSettings: () -> Unit
) {
    val articles by viewModel.articles.collectAsState(initial = emptyList())
    val cart by viewModel.cart.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("\uD83C\uDF7A Sportverein Kasse", color = Color.White) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(articles, key = { it.id }) { article ->
                    ArticleCard(article = article, onClick = { viewModel.addToCart(article) })
                }
            }

            AnimatedVisibility(visible = cart.isNotEmpty()) {
                CartPanel(
                    cart = cart,
                    total = viewModel.cartTotal,
                    onRemove = { viewModel.removeFromCart(it) },
                    onClear = { showClearDialog = true },
                    onCheckout = { viewModel.checkout() }
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Warenkorb leeren?") },
            text = { Text("Möchten Sie den Warenkorb wirklich leeren?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCart()
                    showClearDialog = false
                }) { Text("Ja") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Nein") }
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
            Text(article.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                String.format("%.2f \u20AC", article.price),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CartPanel(
    cart: List<CartItem>,
    total: Double,
    onRemove: (Article) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            LazyColumn(modifier = Modifier.height(if (cart.size > 3) 120.dp else (cart.size * 40).dp)) {
                items(cart, key = { it.article.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${item.quantity}\u00D7 ${item.article.name} \u2014 ${String.format("%.2f", item.article.price * item.quantity)} \u20AC",
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemove(item.article) }) {
                            Text("\u2796", fontSize = 18.sp)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Gesamt: ${String.format("%.2f", total)} \u20AC",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row {
                    Button(
                        onClick = onClear,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("\uD83D\uDDD1\uFE0F Leeren")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onCheckout,
                        colors = ButtonDefaults.buttonColors(containerColor = Green900)
                    ) {
                        Text("\uD83D\uDCB3 Kassieren")
                    }
                }
            }
        }
    }
}
