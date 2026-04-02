# Manual Price Article Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Freier Preis" article to every collection that opens a numpad dialog for manual price entry before adding to cart.

**Architecture:** Convention-based recognition via `Article.name == "__MANUAL_PRICE__"`. The article is auto-inserted into all collections. A new `ManualPriceDialog` composable provides numpad input. Cart items from manual price articles are always separate line items.

**Tech Stack:** Kotlin, Jetpack Compose, Room, MVVM

---

### Task 1: Article Extension Property & Constants

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt`

- [ ] **Step 1: Add the `isManualPrice` extension property and constant**

```kotlin
package net.maerkl.kassierapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

const val MANUAL_PRICE_ARTICLE_NAME = "__MANUAL_PRICE__"

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

val Article.isManualPrice: Boolean
    get() = name == MANUAL_PRICE_ARTICLE_NAME
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt
git commit -m "feat: add isManualPrice extension property to Article"
```

---

### Task 2: DAO Query for Manual Price Article Existence Check

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/ArticleDao.kt`

- [ ] **Step 1: Add query to check if manual price article exists in a collection**

Add this method to `ArticleDao`:

```kotlin
@Query("SELECT COUNT(*) FROM articles WHERE name = :name AND collectionId = :collectionId")
suspend fun countByNameAndCollection(name: String, collectionId: Long): Int
```

- [ ] **Step 2: Add query to get max sortOrder for a collection**

Add this method to `ArticleDao`:

```kotlin
@Query("SELECT COALESCE(MAX(sortOrder), -1) FROM articles WHERE collectionId = :collectionId")
suspend fun getMaxSortOrder(collectionId: Long): Int
```

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/ArticleDao.kt
git commit -m "feat: add DAO queries for manual price article support"
```

---

### Task 3: Auto-Insert Manual Price Article into Default Articles & Existing Collections

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt`

- [ ] **Step 1: Add manual price article to `insertDefaultArticles()`**

Add one more `dao.insert()` call at the end of the `insertDefaultArticles()` method, after the Bratwurst line (line 81):

```kotlin
dao.insert(Article(name = MANUAL_PRICE_ARTICLE_NAME, price = 0.0, emoji = "\u270F\uFE0F", sortOrder = 13, collectionId = collectionId))
```

Also add the import at the top of the file:

```kotlin
import net.maerkl.kassierapp.data.local.MANUAL_PRICE_ARTICLE_NAME
```

- [ ] **Step 2: Add startup migration to ensure all collections have the manual price article**

Add a new method to the `AppDatabase` companion object:

```kotlin
suspend fun ensureManualPriceArticles(articleDao: ArticleDao, collectionDao: ArticleCollectionDao) {
    val collections = collectionDao.getAllOnce()
    for (collection in collections) {
        val count = articleDao.countByNameAndCollection(MANUAL_PRICE_ARTICLE_NAME, collection.id)
        if (count == 0) {
            val maxSort = articleDao.getMaxSortOrder(collection.id)
            articleDao.insert(
                Article(
                    name = MANUAL_PRICE_ARTICLE_NAME,
                    price = 0.0,
                    emoji = "\u270F\uFE0F",
                    sortOrder = maxSort + 1,
                    collectionId = collection.id
                )
            )
        }
    }
}
```

- [ ] **Step 3: Add `getAllOnce()` to `ArticleCollectionDao`**

This is a non-Flow version needed for the startup check. Add to `ArticleCollectionDao.kt`:

```kotlin
@Query("SELECT * FROM article_collections ORDER BY id ASC")
suspend fun getAllOnce(): List<ArticleCollection>
```

- [ ] **Step 4: Call `ensureManualPriceArticles` in the `PrepopulateCallback.onCreate` and add an `onOpen` callback**

Replace the `PrepopulateCallback` class in `AppDatabase.kt`:

```kotlin
private class PrepopulateCallback : Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        INSTANCE?.let { database ->
            CoroutineScope(Dispatchers.IO).launch {
                val id = database.articleCollectionDao().insert(ArticleCollection(name = "Standard"))
                insertDefaultArticles(database.articleDao(), collectionId = id)
            }
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        INSTANCE?.let { database ->
            CoroutineScope(Dispatchers.IO).launch {
                ensureManualPriceArticles(database.articleDao(), database.articleCollectionDao())
            }
        }
    }
}
```

- [ ] **Step 5: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt \
       app/src/main/java/net/maerkl/kassierapp/data/local/ArticleDao.kt \
       app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollectionDao.kt
git commit -m "feat: auto-insert manual price article into all collections"
```

---

### Task 4: ManualPriceDialog Composable

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/ui/components/ManualPriceDialog.kt`

- [ ] **Step 1: Create the numpad dialog composable**

```kotlin
package net.maerkl.kassierapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.maerkl.kassierapp.ui.theme.Green900

@Composable
fun ManualPriceDialog(
    onDismiss: () -> Unit,
    onConfirm: (price: Double, name: String) -> Unit
) {
    var priceInput by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    val displayPrice = formatPriceInput(priceInput)
    val priceValue = priceInput.toDoubleOrNull()?.let { it / 100.0 } ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Freier Preis") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Price display
                Text(
                    text = displayPrice,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )

                // Numpad grid
                val buttons = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "\u232B")
                )

                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { label ->
                            OutlinedButton(
                                onClick = {
                                    when (label) {
                                        "C" -> priceInput = ""
                                        "\u232B" -> {
                                            if (priceInput.isNotEmpty()) {
                                                priceInput = priceInput.dropLast(1)
                                            }
                                        }
                                        else -> {
                                            if (priceInput.length < 7) {
                                                priceInput += label
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Text(label, fontSize = 20.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Optional name field
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Bezeichnung (optional)") },
                    placeholder = { Text("Freier Preis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = customName.trim().ifEmpty { "Freier Preis" }
                    onConfirm(priceValue, name)
                },
                enabled = priceValue > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Green900)
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun formatPriceInput(input: String): String {
    if (input.isEmpty()) return "0,00 \u20AC"
    val cents = input.toLongOrNull() ?: 0L
    val euros = cents / 100
    val remainingCents = cents % 100
    return String.format("%d,%02d \u20AC", euros, remainingCents)
}
```

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/components/ManualPriceDialog.kt
git commit -m "feat: add ManualPriceDialog with numpad input"
```

---

### Task 5: MainViewModel — Manual Price Cart Logic

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt`

- [ ] **Step 1: Add `addManualPriceToCart` method**

Add this import at the top of `MainViewModel.kt`:

```kotlin
import net.maerkl.kassierapp.data.local.MANUAL_PRICE_ARTICLE_NAME
```

Add this new method to `MainViewModel`:

```kotlin
fun addManualPriceToCart(price: Double, name: String, originalArticle: Article) {
    val cartArticle = originalArticle.copy(
        id = -System.nanoTime(),
        name = name,
        price = price
    )
    val current = _cart.value.toMutableList()
    current.add(CartItem(cartArticle, 1))
    _cart.value = current
}
```

The negative `id` based on `nanoTime()` ensures each manual price entry gets a unique ID so cart items never collide or get merged. The `id` is never persisted — `saveSales()` only uses `article.name`, `article.emoji`, and `article.price`.

- [ ] **Step 2: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt
git commit -m "feat: add manual price cart logic to MainViewModel"
```

---

### Task 6: MainScreen — Integrate Manual Price Dialog & Customize Article Card

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt`

- [ ] **Step 1: Add imports and dialog state**

Add these imports to `MainScreen.kt`:

```kotlin
import net.maerkl.kassierapp.data.local.isManualPrice
import net.maerkl.kassierapp.ui.components.ManualPriceDialog
```

Inside the `MainScreen` composable, after the `val cart by ...` line (line 69), add:

```kotlin
var manualPriceArticle by remember { mutableStateOf<Article?>(null) }
```

- [ ] **Step 2: Change the article click handler in the grid**

Replace the `items` block in the `LazyVerticalGrid` (lines 115–118):

```kotlin
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
```

- [ ] **Step 3: Add the dialog invocation**

Right before the closing `}` of the `MainScreen` composable (before line 132), add:

```kotlin
manualPriceArticle?.let { article ->
    ManualPriceDialog(
        onDismiss = { manualPriceArticle = null },
        onConfirm = { price, name ->
            viewModel.addManualPriceToCart(price, name, article)
            manualPriceArticle = null
        }
    )
}
```

- [ ] **Step 4: Customize the ArticleCard for manual price articles**

Replace the `ArticleCard` composable (lines 134–164) with:

```kotlin
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
```

- [ ] **Step 5: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt
git commit -m "feat: integrate manual price dialog into MainScreen"
```

---

### Task 7: Settings Screen — Filter Out Manual Price Articles

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Add import and filter in SettingsViewModel**

Add this import to `SettingsViewModel.kt`:

```kotlin
import kotlinx.coroutines.flow.map
import net.maerkl.kassierapp.data.local.MANUAL_PRICE_ARTICLE_NAME
```

Replace the `allArticles` definition (lines 30–33):

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
val allArticles = activeCollectionId.flatMapLatest { collectionId ->
    dao.getAllArticles(collectionId).map { articles ->
        articles.filter { it.name != MANUAL_PRICE_ARTICLE_NAME }
    }
}
```

- [ ] **Step 2: Include manual price article in `addCollection` and `resetArticlesToDefaults`**

No changes needed — both methods already call `AppDatabase.insertDefaultArticles()` which now includes the manual price article (from Task 3).

- [ ] **Step 3: Verify the project compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt
git commit -m "feat: filter manual price articles from settings UI"
```

---

### Task 8: Final Integration Verification

- [ ] **Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all changes are committed**

Run: `git status`
Expected: clean working tree

- [ ] **Step 3: Commit plan as complete**

```bash
git add docs/superpowers/plans/2026-04-02-manual-price-article.md
git commit -m "docs: add manual price article implementation plan"
```
