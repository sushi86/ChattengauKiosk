# Article Stock Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional stock quantity tracking per article, showing remaining stock on article cards in the cashier view.

**Architecture:** Add `stockQuantity: Int?` field to the `Article` entity with a Room migration. Compute remaining stock reactively by combining article data with today's sales. Display remaining stock on `ArticleCard` and allow input via `ArticleDialog`.

**Tech Stack:** Room (migration), Kotlin Coroutines/Flow, Jetpack Compose

---

### File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt` | Modify | Add `stockQuantity: Int?` field |
| `app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt` | Modify | Add `SoldQuantity` data class |
| `app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt` | Modify | Add `getSoldQuantitiesToday()` query |
| `app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt` | Modify | Add `MIGRATION_3_4`, bump version to 4 |
| `app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt` | Modify | Compute `remainingStock` map from articles + sales |
| `app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt` | Modify | Pass remaining stock to `ArticleCard`, display it |
| `app/src/main/java/net/maerkl/kassierapp/ui/components/ArticleDialog.kt` | Modify | Add optional "Menge" input field |
| `app/src/main/java/net/maerkl/kassierapp/ui/settings/ArticleManagementScreen.kt` | Modify | Pass `stockQuantity` through add/edit callbacks |
| `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt` | Modify | Accept `stockQuantity` in add/update methods |

---

### Task 1: Add `stockQuantity` field to Article entity

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt`

- [ ] **Step 1: Add the field**

In `Article.kt`, add `stockQuantity` as the last field in the data class:

```kotlin
@Entity(tableName = "articles")
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val emoji: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val collectionId: Long = 1,
    val stockQuantity: Int? = null
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt
git commit -m "feat: add stockQuantity field to Article entity"
```

---

### Task 2: Add database migration 3→4

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt`

- [ ] **Step 1: Add MIGRATION_3_4**

In `AppDatabase.kt`, add a new migration after `MIGRATION_2_3`:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE articles ADD COLUMN stockQuantity INTEGER DEFAULT NULL")
    }
}
```

- [ ] **Step 2: Bump database version to 4**

Change the `@Database` annotation:

```kotlin
@Database(entities = [Article::class, Sale::class, ArticleCollection::class], version = 4)
```

- [ ] **Step 3: Register the migration**

In `getDatabase()`, update `.addMigrations()`:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt
git commit -m "feat: add database migration 3→4 for stockQuantity column"
```

---

### Task 3: Add `SoldQuantity` data class and DAO query

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt`

- [ ] **Step 1: Add SoldQuantity data class**

In `Sale.kt`, add after `ArticleDaySummary`:

```kotlin
data class SoldQuantity(
    val articleName: String,
    val totalSold: Int
)
```

- [ ] **Step 2: Add DAO query**

In `SaleDao.kt`, add this query:

```kotlin
@Query("""
    SELECT articleName, SUM(quantity) AS totalSold
    FROM sales
    WHERE collectionId = :collectionId AND timestamp >= :startOfDay
    GROUP BY articleName
""")
fun getSoldQuantitiesToday(collectionId: Long, startOfDay: Long): Flow<List<SoldQuantity>>
```

Add the `Flow` import if not already present (it is already imported).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt
git commit -m "feat: add SoldQuantity query for stock tracking"
```

---

### Task 4: Compute remaining stock in MainViewModel

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt`

- [ ] **Step 1: Add imports**

Add at the top of `MainViewModel.kt`:

```kotlin
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import java.util.TimeZone
```

- [ ] **Step 2: Add helper function for start of today**

Add a private function inside `MainViewModel`:

```kotlin
private fun startOfToday(): Long {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
```

- [ ] **Step 3: Add remainingStock StateFlow**

Add after the `articles` property:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
val remainingStock: StateFlow<Map<String, Int>> = activeCollectionId.flatMapLatest { collectionId ->
    val soldFlow = saleDao.getSoldQuantitiesToday(collectionId, startOfToday())
    val articlesFlow = articleDao.getActiveArticles(collectionId)
    combine(articlesFlow, soldFlow) { articleList, soldList ->
        val soldMap = soldList.associate { it.articleName to it.totalSold }
        articleList
            .filter { it.stockQuantity != null }
            .associate { article ->
                val sold = soldMap[article.name] ?: 0
                article.name to (article.stockQuantity!! - sold)
            }
    }
}.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt
git commit -m "feat: compute remaining stock in MainViewModel"
```

---

### Task 5: Display remaining stock on ArticleCard

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt`

- [ ] **Step 1: Collect remainingStock in MainScreen**

In `MainScreen()`, add after `val cart by viewModel.cart.collectAsState()`:

```kotlin
val remainingStock by viewModel.remainingStock.collectAsState()
```

- [ ] **Step 2: Pass remainingStock to ArticleCard**

Update the `ArticleCard` call inside the grid:

```kotlin
ArticleCard(
    article = article,
    remainingStock = remainingStock[article.name],
    onClick = {
        if (article.isManualPrice) {
            manualPriceArticle = article
        } else {
            viewModel.addToCart(article)
        }
    }
)
```

- [ ] **Step 3: Update ArticleCard signature and display**

Replace the `ArticleCard` composable:

```kotlin
@Composable
private fun ArticleCard(article: Article, remainingStock: Int?, onClick: () -> Unit) {
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
                    String.format("%.2f €", article.price),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (remainingStock != null) {
                Text(
                    text = if (remainingStock > 0) "Noch $remainingStock" else "Ausverkauft",
                    color = if (remainingStock > 0) Color.Gray else Color.Red,
                    fontSize = 11.sp,
                    fontWeight = if (remainingStock <= 0) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt
git commit -m "feat: display remaining stock on article cards"
```

---

### Task 6: Add stock quantity input to ArticleDialog

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/components/ArticleDialog.kt`

- [ ] **Step 1: Update ArticleDialog signature**

Change the `onSave` callback to include `stockQuantity`:

```kotlin
@Composable
fun ArticleDialog(
    article: Article? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, price: Double, emoji: String, isActive: Boolean, stockQuantity: Int?) -> Unit
)
```

- [ ] **Step 2: Add state for stockQuantity**

After the `isActive` state, add:

```kotlin
var stockText by remember { mutableStateOf(article?.stockQuantity?.toString() ?: "") }
```

- [ ] **Step 3: Add the text field**

In the `Column` inside the dialog, after the emoji field and its spacer, add before the `Row` with the checkbox:

```kotlin
Spacer(modifier = Modifier.height(8.dp))
OutlinedTextField(
    value = stockText,
    onValueChange = { stockText = it },
    label = { Text("Menge (optional)") },
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
)
```

- [ ] **Step 4: Update onSave call**

Change the confirm button's `onClick`:

```kotlin
TextButton(
    onClick = {
        val price = priceText.replace(",", ".").toDoubleOrNull()
        if (name.isNotBlank() && price != null && price > 0) {
            val stock = stockText.toIntOrNull()
            onSave(name, price, emoji, isActive, stock)
        }
    }
) { Text("Speichern") }
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/components/ArticleDialog.kt
git commit -m "feat: add stock quantity input to ArticleDialog"
```

---

### Task 7: Wire stock quantity through settings layer

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/ArticleManagementScreen.kt`

- [ ] **Step 1: Update SettingsViewModel.addArticle**

Change `addArticle` to accept `stockQuantity`:

```kotlin
fun addArticle(name: String, price: Double, emoji: String, isActive: Boolean, currentCount: Int, stockQuantity: Int?) {
    viewModelScope.launch {
        val collectionId = activeCollectionId.value
        dao.insert(Article(name = name, price = price, emoji = emoji, isActive = isActive, sortOrder = currentCount, collectionId = collectionId, stockQuantity = stockQuantity))
        bumpManualPriceToEnd(collectionId)
    }
}
```

- [ ] **Step 2: Update SettingsViewModel.updateArticle**

Change `updateArticle` to accept `stockQuantity`:

```kotlin
fun updateArticle(article: Article, name: String, price: Double, emoji: String, isActive: Boolean, stockQuantity: Int?) {
    viewModelScope.launch {
        dao.update(article.copy(name = name, price = price, emoji = emoji, isActive = isActive, stockQuantity = stockQuantity))
    }
}
```

- [ ] **Step 3: Update ArticleManagementScreen add dialog callback**

In `ArticleManagementScreen.kt`, update the `showAddDialog` block:

```kotlin
if (showAddDialog) {
    ArticleDialog(
        onDismiss = { showAddDialog = false },
        onSave = { name, price, emoji, isActive, stockQuantity ->
            viewModel.addArticle(name, price, emoji, isActive, articles.size, stockQuantity)
            showAddDialog = false
        }
    )
}
```

- [ ] **Step 4: Update ArticleManagementScreen edit dialog callback**

Update the `editingArticle` block:

```kotlin
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
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt app/src/main/java/net/maerkl/kassierapp/ui/settings/ArticleManagementScreen.kt
git commit -m "feat: wire stock quantity through settings layer"
```

---

### Task 8: Build and verify

- [ ] **Step 1: Build the project**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Final commit if any fixes needed**

If build errors required changes, commit those fixes.
