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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.ArticleCollection
import net.maerkl.kassierapp.ui.components.ArticleDialog
import net.maerkl.kassierapp.ui.theme.Green900
import sh.calvin.reorderable.ReorderableColumn

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleManagementScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val articles by viewModel.allArticles.collectAsState(initial = emptyList())
    val collections by viewModel.allCollections.collectAsState(initial = emptyList())
    val activeCollectionId by viewModel.activeCollectionId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingArticle by remember { mutableStateOf<Article?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<Article?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    var showAddCollectionDialog by remember { mutableStateOf(false) }
    var showRenameCollectionDialog by remember { mutableStateOf<ArticleCollection?>(null) }
    var showDeleteCollectionDialog by remember { mutableStateOf<ArticleCollection?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Artikelverwaltung", color = Color.White) },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("\u2190 Zur\u00FCck", color = Color.White)
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

            // Article list
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
                                    "${article.emoji} ${article.name} \u2014 ${String.format("%.2f", article.price)} \u20AC",
                                    modifier = Modifier.weight(1f)
                                )
                                if (article.stockQuantity != null) {
                                    Text(
                                        "Menge: ${article.stockQuantity}",
                                        fontSize = 13.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
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
                                Text("+ Hinzuf\u00FCgen")
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
        }
    }

    // Dialogs
    if (showAddDialog) {
        ArticleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, price, emoji, isActive, stockQuantity ->
                viewModel.addArticle(name, price, emoji, isActive, articles.size, stockQuantity)
                showAddDialog = false
            }
        )
    }

    editingArticle?.let { article ->
        ArticleDialog(
            article = article,
            onDismiss = { editingArticle = null },
            onSave = { name, price, emoji, isActive, stockQuantity ->
                viewModel.updateArticle(article, name, price, emoji, isActive, stockQuantity)
                editingArticle = null
            }
        )
    }

    showDeleteDialog?.let { article ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Artikel l\u00F6schen?") },
            text = { Text("\"${article.name}\" wirklich l\u00F6schen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteArticle(article)
                    showDeleteDialog = null
                }) { Text("L\u00F6schen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Abbrechen") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Artikel zur\u00FCcksetzen?") },
            text = { Text("Alle Artikel werden gel\u00F6scht und durch die Standard-Artikel ersetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetArticlesToDefaults()
                    showResetDialog = false
                }) { Text("Zur\u00FCcksetzen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Abbrechen") }
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
            title = { Text("Collection l\u00F6schen?") },
            text = { Text("\"${collection.name}\" und alle zugeh\u00F6rigen Artikel werden gel\u00F6scht.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCollection(collection)
                    showDeleteCollectionDialog = null
                }) { Text("L\u00F6schen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCollectionDialog = null }) { Text("Abbrechen") }
            }
        )
    }
}
