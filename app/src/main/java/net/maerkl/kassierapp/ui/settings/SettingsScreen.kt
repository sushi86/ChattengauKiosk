package net.maerkl.kassierapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.ArticleCollection
import net.maerkl.kassierapp.ui.components.ArticleDialog
import net.maerkl.kassierapp.ui.theme.Green900
import sh.calvin.reorderable.ReorderableColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenCardReader: () -> Unit,
    onNavigateToStatistics: () -> Unit
) {
    val articles by viewModel.allArticles.collectAsState(initial = emptyList())
    val affiliateKey by viewModel.affiliateKey.collectAsState()
    val oauthToken by viewModel.oauthToken.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingArticle by remember { mutableStateOf<Article?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Article?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    val collections by viewModel.allCollections.collectAsState(initial = emptyList())
    val activeCollectionId by viewModel.activeCollectionId.collectAsState()

    var showAddCollectionDialog by remember { mutableStateOf(false) }
    var showRenameCollectionDialog by remember { mutableStateOf<ArticleCollection?>(null) }
    var showDeleteCollectionDialog by remember { mutableStateOf<ArticleCollection?>(null) }

    var editAffiliateKey by remember(affiliateKey) { mutableStateOf(affiliateKey) }
    var editOauthToken by remember(oauthToken) { mutableStateOf(oauthToken) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("\u2699\uFE0F Einstellungen", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("\u2190 Zurück", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green900)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Artikelverwaltung Section
            item {
                Text("Artikelverwaltung", style = MaterialTheme.typography.titleLarge)
            }

            // Collection picker
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Collections", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            collections.forEach { collection ->
                                val isActive = collection.id == activeCollectionId
                                FilterChip(
                                    selected = isActive,
                                    onClick = { viewModel.selectCollection(collection.id) },
                                    label = { Text(collection.name) },
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = { showRenameCollectionDialog = collection }, modifier = Modifier.size(24.dp)) {
                                                Text("\u270F\uFE0F", fontSize = 12.sp)
                                            }
                                            IconButton(onClick = { showDeleteCollectionDialog = collection }, modifier = Modifier.size(24.dp)) {
                                                Text("\uD83D\uDDD1\uFE0F", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                )
                            }
                            AssistChip(
                                onClick = { showAddCollectionDialog = true },
                                label = { Text("+ Neu") }
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        ReorderableColumn(
                            list = articles,
                            onSettle = { fromIndex, toIndex ->
                                val mutable = articles.toMutableList()
                                val item = mutable.removeAt(fromIndex)
                                mutable.add(toIndex, item)
                                viewModel.reorderArticles(mutable)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { _, article, _ ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (!article.isActive) 0.5f else 1f)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {},
                                    modifier = Modifier.draggableHandle()
                                ) {
                                    Text("\u2630", fontSize = 18.sp)
                                }
                                Text(
                                    "${article.emoji} ${article.name} — ${String.format("%.2f", article.price)} €",
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { editingArticle = article }) {
                                    Text("\u270F\uFE0F", fontSize = 16.sp)
                                }
                                IconButton(onClick = { showDeleteDialog = article }) {
                                    Text("\uD83D\uDDD1\uFE0F", fontSize = 16.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showAddDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Green900)
                            ) {
                                Text("+ Hinzufügen")
                            }
                            Button(
                                onClick = { showResetDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Standard wiederherstellen")
                            }
                        }
                    }
                }
            }

            // SumUp Configuration Section
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("SumUp-Konfiguration", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = editAffiliateKey,
                            onValueChange = { editAffiliateKey = it },
                            label = { Text("Affiliate Key") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editOauthToken,
                            onValueChange = { editOauthToken = it },
                            label = { Text("OAuth Token") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.saveSumUpConfig(editAffiliateKey, editOauthToken) },
                                colors = ButtonDefaults.buttonColors(containerColor = Green900)
                            ) { Text("Speichern") }
                            Button(onClick = onLogin) { Text("Login") }
                            Button(onClick = onOpenCardReader) { Text("Kartenleser") }
                        }
                    }
                }
            }

            // Statistics Section
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Statistik", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Button(
                    onClick = onNavigateToStatistics,
                    colors = ButtonDefaults.buttonColors(containerColor = Green900)
                ) { Text("\uD83D\uDCCA Verkaufsstatistik") }
            }

            // PIN Change Section
            item {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("PIN ändern", style = MaterialTheme.typography.titleLarge)
            }

            item {
                Button(
                    onClick = { showPinChangeDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Green900)
                ) { Text("PIN ändern") }
            }
        }
    }

    // Dialogs
    if (showAddDialog) {
        ArticleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, price, emoji, isActive ->
                viewModel.addArticle(name, price, emoji, isActive, articles.size)
                showAddDialog = false
            }
        )
    }

    editingArticle?.let { article ->
        ArticleDialog(
            article = article,
            onDismiss = { editingArticle = null },
            onSave = { name, price, emoji, isActive ->
                viewModel.updateArticle(article, name, price, emoji, isActive)
                editingArticle = null
            }
        )
    }

    showDeleteDialog?.let { article ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Artikel löschen?") },
            text = { Text("\"${article.name}\" wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteArticle(article)
                    showDeleteDialog = null
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Abbrechen") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Artikel zurücksetzen?") },
            text = { Text("Alle Artikel werden gelöscht und durch die Standard-Artikel ersetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetArticlesToDefaults()
                    showResetDialog = false
                }) { Text("Zurücksetzen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showPinChangeDialog) {
        PinChangeDialog(
            onDismiss = { showPinChangeDialog = false },
            onSave = { newPin ->
                viewModel.changePin(newPin)
                showPinChangeDialog = false
            }
        )
    }

    if (showAddCollectionDialog) {
        CollectionNameDialog(
            title = "Neue Collection",
            onDismiss = { showAddCollectionDialog = false },
            onSave = { name ->
                viewModel.addCollection(name)
                showAddCollectionDialog = false
            }
        )
    }

    showRenameCollectionDialog?.let { collection ->
        CollectionNameDialog(
            title = "Collection umbenennen",
            initialName = collection.name,
            onDismiss = { showRenameCollectionDialog = null },
            onSave = { name ->
                viewModel.renameCollection(collection, name)
                showRenameCollectionDialog = null
            }
        )
    }

    showDeleteCollectionDialog?.let { collection ->
        AlertDialog(
            onDismissRequest = { showDeleteCollectionDialog = null },
            title = { Text("Collection löschen?") },
            text = { Text("\"${collection.name}\" und alle zugehörigen Artikel werden gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection(collection)
                    showDeleteCollectionDialog = null
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCollectionDialog = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun PinChangeDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN ändern") },
        text = {
            Column {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                    label = { Text("Neuer PIN") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("PIN bestätigen") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    newPin.length != 4 -> error = "PIN muss 4 Ziffern haben"
                    newPin != confirmPin -> error = "PINs stimmen nicht überein"
                    else -> onSave(newPin)
                }
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun CollectionNameDialog(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
