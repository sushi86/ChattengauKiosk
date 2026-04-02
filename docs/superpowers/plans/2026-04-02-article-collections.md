# Article Collections Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Separate articles and sales by collection (one per sports venue) so each venue has its own article list and independent sales accounting.

**Architecture:** New `ArticleCollection` entity with FK on `Article` and `Sale`. Active collection stored in DataStore. All article/sale queries filtered by `collectionId`. Settings UI gets a collection picker above the article list.

**Tech Stack:** Room (migration 2→3), Jetpack DataStore, Compose Material3

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `data/local/ArticleCollection.kt` | Create | Entity |
| `data/local/ArticleCollectionDao.kt` | Create | DAO for collections |
| `data/local/Article.kt` | Modify | Add `collectionId` field |
| `data/local/Sale.kt` | Modify | Add `collectionId` field |
| `data/local/ArticleDao.kt` | Modify | Filter queries by `collectionId` |
| `data/local/SaleDao.kt` | Modify | Filter queries by `collectionId` |
| `data/local/AppDatabase.kt` | Modify | Register new entity + DAO, migration 2→3 |
| `data/preferences/SettingsDataStore.kt` | Modify | Add `activeCollectionId` |
| `ui/settings/SettingsViewModel.kt` | Modify | Collection CRUD, pass `collectionId` to article ops |
| `ui/settings/SettingsScreen.kt` | Modify | Collection picker UI, remove old flat list |
| `ui/main/MainViewModel.kt` | Modify | Filter articles + sales by active collection |
| `ui/statistics/StatisticsViewModel.kt` | Modify | Filter stats by active collection |

---

### Task 1: Create `ArticleCollection` entity and DAO

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollection.kt`
- Create: `app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollectionDao.kt`

- [ ] **Step 1: Create the entity**

```kotlin
// ArticleCollection.kt
package net.maerkl.kassierapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "article_collections")
data class ArticleCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
```

- [ ] **Step 2: Create the DAO**

```kotlin
// ArticleCollectionDao.kt
package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleCollectionDao {
    @Query("SELECT * FROM article_collections ORDER BY id ASC")
    fun getAll(): Flow<List<ArticleCollection>>

    @Query("SELECT COUNT(*) FROM article_collections")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(collection: ArticleCollection): Long

    @Update
    suspend fun update(collection: ArticleCollection)

    @Delete
    suspend fun delete(collection: ArticleCollection)
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollection.kt app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollectionDao.kt
git commit -m "feat: add ArticleCollection entity and DAO"
```

---

### Task 2: Add `collectionId` to `Article` and update `ArticleDao`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/ArticleDao.kt`

- [ ] **Step 1: Add `collectionId` field to `Article`**

In `Article.kt`, add `collectionId` parameter with default 1:

```kotlin
@Entity(tableName = "articles")
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val emoji: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val collectionId: Long = 1
)
```

- [ ] **Step 2: Update `ArticleDao` queries to filter by `collectionId`**

```kotlin
@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE isActive = 1 AND collectionId = :collectionId ORDER BY sortOrder ASC")
    fun getActiveArticles(collectionId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    fun getAllArticles(collectionId: Long): Flow<List<Article>>

    @Insert
    suspend fun insert(article: Article)

    @Update
    suspend fun update(article: Article)

    @Delete
    suspend fun delete(article: Article)

    @Query("UPDATE articles SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM articles WHERE collectionId = :collectionId")
    suspend fun deleteAllByCollection(collectionId: Long)

    @Query("DELETE FROM articles")
    suspend fun deleteAll()
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt app/src/main/java/net/maerkl/kassierapp/data/local/ArticleDao.kt
git commit -m "feat: add collectionId to Article and filter queries"
```

---

### Task 3: Add `collectionId` to `Sale` and update `SaleDao`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt`

- [ ] **Step 1: Add `collectionId` to `Sale`**

```kotlin
@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleName: String,
    val articleEmoji: String,
    val articlePrice: Double,
    val quantity: Int,
    val paymentMethod: String,
    val timestamp: Long,
    val collectionId: Long = 1
)
```

`DailySummary` and `ArticleDaySummary` stay unchanged.

- [ ] **Step 2: Update `SaleDao` queries to filter by `collectionId`**

```kotlin
@Dao
interface SaleDao {
    @Insert
    suspend fun insertAll(sales: List<Sale>)

    @Query("""
        SELECT (timestamp / 86400000) * 86400000 AS dayTimestamp,
               SUM(articlePrice * quantity) AS totalRevenue,
               SUM(quantity) AS totalItems
        FROM sales
        WHERE collectionId = :collectionId
        GROUP BY timestamp / 86400000
        ORDER BY dayTimestamp DESC
    """)
    fun getDailySummaries(collectionId: Long): Flow<List<DailySummary>>

    @Query("""
        SELECT articleName, articleEmoji,
               SUM(CASE WHEN paymentMethod = 'BAR' THEN quantity ELSE 0 END) AS cashQuantity,
               SUM(CASE WHEN paymentMethod = 'BAR' THEN articlePrice * quantity ELSE 0.0 END) AS cashRevenue,
               SUM(CASE WHEN paymentMethod = 'KARTE' THEN quantity ELSE 0 END) AS cardQuantity,
               SUM(CASE WHEN paymentMethod = 'KARTE' THEN articlePrice * quantity ELSE 0.0 END) AS cardRevenue
        FROM sales
        WHERE collectionId = :collectionId AND timestamp >= :startOfDay AND timestamp < :endOfDay
        GROUP BY articleName, articleEmoji
        ORDER BY articleName ASC
    """)
    fun getArticleSummariesForDay(collectionId: Long, startOfDay: Long, endOfDay: Long): Flow<List<ArticleDaySummary>>
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt
git commit -m "feat: add collectionId to Sale and filter queries"
```

---

### Task 4: Database migration 2→3 and register new entity/DAO

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt`

- [ ] **Step 1: Update `@Database` annotation**

Change version to 3, add `ArticleCollection` to entities:

```kotlin
@Database(entities = [Article::class, Sale::class, ArticleCollection::class], version = 3)
```

- [ ] **Step 2: Add abstract DAO method**

Add below `abstract fun saleDao()`:

```kotlin
abstract fun articleCollectionDao(): ArticleCollectionDao
```

- [ ] **Step 3: Add migration 2→3**

Add inside `companion object`, after `MIGRATION_1_2`:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create collections table
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS article_collections (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL
            )
        """.trimIndent())
        // Insert default collection
        db.execSQL("INSERT INTO article_collections (id, name) VALUES (1, 'Standard')")
        // Add collectionId to articles
        db.execSQL("ALTER TABLE articles ADD COLUMN collectionId INTEGER NOT NULL DEFAULT 1")
        // Add collectionId to sales
        db.execSQL("ALTER TABLE sales ADD COLUMN collectionId INTEGER NOT NULL DEFAULT 1")
    }
}
```

- [ ] **Step 4: Register migration in builder**

Change `.addMigrations(MIGRATION_1_2)` to:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3)
```

- [ ] **Step 5: Update `insertDefaultArticles` to accept `collectionId`**

```kotlin
suspend fun insertDefaultArticles(dao: ArticleDao, collectionId: Long = 1) {
    dao.insert(Article(name = "Hütt Luxus Pils", price = 2.00, emoji = "\uD83C\uDF7A", sortOrder = 0, collectionId = collectionId))
    dao.insert(Article(name = "Martini Edelpils", price = 2.00, emoji = "\uD83C\uDF7A", sortOrder = 1, collectionId = collectionId))
    dao.insert(Article(name = "Hütt Naturtrüb Radler", price = 2.00, emoji = "\uD83C\uDF7B", sortOrder = 2, collectionId = collectionId))
    dao.insert(Article(name = "Hütt Hefeweizen", price = 3.00, emoji = "\uD83C\uDF7A", sortOrder = 3, collectionId = collectionId))
    dao.insert(Article(name = "Hütt Hefeweizen alkoholfrei", price = 3.00, emoji = "\uD83C\uDF7A", sortOrder = 4, collectionId = collectionId))
    dao.insert(Article(name = "Bier (Kiste)", price = 35.00, emoji = "\uD83D\uDCE6", sortOrder = 5, collectionId = collectionId))
    dao.insert(Article(name = "Coca Cola", price = 2.00, emoji = "\uD83E\uDD64", sortOrder = 6, collectionId = collectionId))
    dao.insert(Article(name = "Fanta", price = 2.00, emoji = "\uD83C\uDF4A", sortOrder = 7, collectionId = collectionId))
    dao.insert(Article(name = "Sprite", price = 2.00, emoji = "\uD83E\uDDCB", sortOrder = 8, collectionId = collectionId))
    dao.insert(Article(name = "Mineralwasser", price = 1.50, emoji = "\uD83D\uDCA7", sortOrder = 9, collectionId = collectionId))
    dao.insert(Article(name = "Kaffee", price = 1.50, emoji = "\u2615", sortOrder = 10, collectionId = collectionId))
    dao.insert(Article(name = "Kuchen", price = 1.50, emoji = "\uD83C\uDF70", sortOrder = 11, collectionId = collectionId))
    dao.insert(Article(name = "Bratwurst mit Brötchen", price = 3.50, emoji = "\uD83C\uDF2D", sortOrder = 12, collectionId = collectionId))
}
```

- [ ] **Step 6: Update `PrepopulateCallback`**

The callback also needs to create the default collection before inserting articles:

```kotlin
private class PrepopulateCallback : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        INSTANCE?.let { database ->
            CoroutineScope(Dispatchers.IO).launch {
                database.articleCollectionDao().insert(ArticleCollection(name = "Standard"))
                insertDefaultArticles(database.articleDao(), collectionId = 1)
            }
        }
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt
git commit -m "feat: database migration 2→3 with collection support"
```

---

### Task 5: Add `activeCollectionId` to DataStore

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/preferences/SettingsDataStore.kt`

- [ ] **Step 1: Add preference key and accessors**

Add to companion object:

```kotlin
val ACTIVE_COLLECTION_ID = longPreferencesKey("active_collection_id")
```

Add import:

```kotlin
import androidx.datastore.preferences.core.longPreferencesKey
```

Add flow and save method to the class body:

```kotlin
val activeCollectionId: Flow<Long> = context.dataStore.data.map { it[ACTIVE_COLLECTION_ID] ?: 1L }

suspend fun saveActiveCollectionId(id: Long) {
    context.dataStore.edit { it[ACTIVE_COLLECTION_ID] = id }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/preferences/SettingsDataStore.kt
git commit -m "feat: add activeCollectionId to DataStore"
```

---

### Task 6: Update `MainViewModel` to use active collection

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt`

- [ ] **Step 1: Read `activeCollectionId` and filter articles**

Replace the `articles` property and update `saveSales`:

```kotlin
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val articleDao = app.database.articleDao()
    private val saleDao = app.database.saleDao()
    private val settings = app.settingsDataStore

    private val activeCollectionId = settings.activeCollectionId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val articles = activeCollectionId.flatMapLatest { collectionId ->
        articleDao.getActiveArticles(collectionId)
    }

    // ... _cart, _checkoutAmount, _snackbarMessage stay unchanged ...

    private fun saveSales(paymentMethod: String) {
        val now = System.currentTimeMillis()
        val collectionId = activeCollectionId.value
        val sales = _cart.value.map { item ->
            Sale(
                articleName = item.article.name,
                articleEmoji = item.article.emoji,
                articlePrice = item.article.price,
                quantity = item.quantity,
                paymentMethod = paymentMethod,
                timestamp = now,
                collectionId = collectionId
            )
        }
        viewModelScope.launch {
            saleDao.insertAll(sales)
        }
    }
}
```

New imports needed:

```kotlin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt
git commit -m "feat: filter MainViewModel articles and sales by active collection"
```

---

### Task 7: Update `StatisticsViewModel` to use active collection

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/statistics/StatisticsViewModel.kt`

- [ ] **Step 1: Filter stats by active collection**

```kotlin
class StatisticsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val saleDao = app.database.saleDao()
    private val settings = app.settingsDataStore

    private val activeCollectionId = settings.activeCollectionId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val dailySummaries: Flow<List<DailySummary>> = activeCollectionId.flatMapLatest { collectionId ->
        saleDao.getDailySummaries(collectionId)
    }

    fun getArticleSummaries(dayTimestamp: Long): Flow<List<ArticleDaySummary>> {
        return saleDao.getArticleSummariesForDay(activeCollectionId.value, dayTimestamp, dayTimestamp + 86_400_000)
    }

    // formatDate and exportCsv stay unchanged
}
```

New imports needed:

```kotlin
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/statistics/StatisticsViewModel.kt
git commit -m "feat: filter statistics by active collection"
```

---

### Task 8: Update `SettingsViewModel` with collection management

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Add collection state and CRUD operations**

Full replacement of SettingsViewModel:

```kotlin
package net.maerkl.kassierapp.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.local.AppDatabase
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.ArticleCollection

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val dao = app.database.articleDao()
    private val collectionDao = app.database.articleCollectionDao()
    private val settings = app.settingsDataStore

    val allCollections = collectionDao.getAll()

    val activeCollectionId = settings.activeCollectionId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val allArticles = activeCollectionId.flatMapLatest { collectionId ->
        dao.getAllArticles(collectionId)
    }

    val affiliateKey = settings.affiliateKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val oauthToken = settings.oauthToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val pin = settings.pin.stateIn(viewModelScope, SharingStarted.Eagerly, "0000")

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    fun selectCollection(id: Long) {
        viewModelScope.launch {
            settings.saveActiveCollectionId(id)
        }
    }

    fun addCollection(name: String) {
        viewModelScope.launch {
            val id = collectionDao.insert(ArticleCollection(name = name))
            settings.saveActiveCollectionId(id)
            AppDatabase.insertDefaultArticles(dao, id)
            _snackbarMessage.emit("Collection \"$name\" erstellt")
        }
    }

    fun renameCollection(collection: ArticleCollection, newName: String) {
        viewModelScope.launch {
            collectionDao.update(collection.copy(name = newName))
        }
    }

    fun deleteCollection(collection: ArticleCollection) {
        viewModelScope.launch {
            val count = collectionDao.getCount()
            if (count <= 1) {
                _snackbarMessage.emit("Letzte Collection kann nicht gelöscht werden")
                return@launch
            }
            dao.deleteAllByCollection(collection.id)
            collectionDao.delete(collection)
            if (activeCollectionId.value == collection.id) {
                // Switch to first remaining collection
                // getAll() is a Flow, so we read it fresh via a one-shot query isn't available
                // Instead, just set to 1 or handle in UI when collections update
                settings.saveActiveCollectionId(1L)
            }
            _snackbarMessage.emit("\"${collection.name}\" gelöscht")
        }
    }

    fun addArticle(name: String, price: Double, emoji: String, isActive: Boolean, currentCount: Int) {
        viewModelScope.launch {
            dao.insert(Article(name = name, price = price, emoji = emoji, isActive = isActive, sortOrder = currentCount, collectionId = activeCollectionId.value))
        }
    }

    fun updateArticle(article: Article, name: String, price: Double, emoji: String, isActive: Boolean) {
        viewModelScope.launch {
            dao.update(article.copy(name = name, price = price, emoji = emoji, isActive = isActive))
        }
    }

    fun deleteArticle(article: Article) {
        viewModelScope.launch {
            dao.delete(article)
        }
    }

    fun reorderArticles(articles: List<Article>) {
        viewModelScope.launch {
            articles.forEachIndexed { index, article ->
                dao.updateSortOrder(article.id, index)
            }
        }
    }

    fun saveSumUpConfig(affiliateKey: String, oauthToken: String) {
        viewModelScope.launch {
            settings.saveAffiliateKey(affiliateKey)
            settings.saveOauthToken(oauthToken)
            _snackbarMessage.emit("Gespeichert")
        }
    }

    fun changePin(newPin: String) {
        viewModelScope.launch {
            settings.savePin(newPin)
            _snackbarMessage.emit("PIN geändert")
        }
    }

    fun resetArticlesToDefaults() {
        viewModelScope.launch {
            val collectionId = activeCollectionId.value
            dao.deleteAllByCollection(collectionId)
            AppDatabase.insertDefaultArticles(dao, collectionId)
            _snackbarMessage.emit("Artikel zurückgesetzt")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt
git commit -m "feat: add collection management to SettingsViewModel"
```

---

### Task 9: Update `SettingsScreen` UI with collection picker

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Add collection state and dialogs**

At the top of `SettingsScreen`, add new state:

```kotlin
val collections by viewModel.allCollections.collectAsState(initial = emptyList())
val activeCollectionId by viewModel.activeCollectionId.collectAsState()

var showAddCollectionDialog by remember { mutableStateOf(false) }
var showRenameCollectionDialog by remember { mutableStateOf<ArticleCollection?>(null) }
var showDeleteCollectionDialog by remember { mutableStateOf<ArticleCollection?>(null) }
```

Add import for `ArticleCollection`:

```kotlin
import net.maerkl.kassierapp.data.local.ArticleCollection
```

- [ ] **Step 2: Replace the "Artikel-Verwaltung" section**

Replace the entire article management section (the title item and the card item) with a collection picker and article list. The new "Artikelverwaltung" section:

```kotlin
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

// Article list for active collection (same as before)
item {
    Card(modifier = Modifier.fillMaxWidth()) {
        // ... existing ReorderableColumn + buttons, unchanged ...
    }
}
```

New imports needed:

```kotlin
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
```

- [ ] **Step 3: Add collection dialogs at the bottom of the composable**

After the existing dialogs, add:

```kotlin
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
```

- [ ] **Step 4: Add `CollectionNameDialog` composable**

Add at the bottom of the file:

```kotlin
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
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsScreen.kt
git commit -m "feat: add collection picker UI to settings screen"
```

---

### Task 10: Build and verify

- [ ] **Step 1: Build the project**

```bash
./gradlew assembleDebug
```

Fix any compilation errors.

- [ ] **Step 2: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve compilation issues"
```
