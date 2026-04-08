# Transaction History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store a TX_CODE per card payment, group sales into transactions, and show a transaction history modal when tapping the clock.

**Architecture:** New `Transaction` entity groups `Sale` records. Card payments store the SumUp `TX_CODE`. A modal dialog accessible from the TopBar clock shows today's transactions with expandable cart details.

**Tech Stack:** Room (migration v4→v5), Jetpack Compose, Kotlin Coroutines/Flow

---

## File Map

| Action | File | Responsibility |
|--------|------|---------------|
| Create | `data/local/Transaction.kt` | Transaction entity + TransactionWithSales data class |
| Create | `data/local/TransactionDao.kt` | DAO for transactions |
| Modify | `data/local/Sale.kt` | Add `transactionId` field |
| Modify | `data/local/SaleDao.kt` | Add `getSalesByTransactionId` query |
| Modify | `data/local/AppDatabase.kt` | Register Transaction entity, add TransactionDao, migration v4→v5 |
| Modify | `ui/main/MainViewModel.kt` | Create transactions in payment flow, expose today's transactions |
| Modify | `ui/main/MainScreen.kt` | Make clock clickable, add TransactionHistoryDialog |
| Modify | `MainActivity.kt` | Pass TX_CODE from SumUp response to ViewModel |

---

### Task 1: Transaction Entity & DAO

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/data/local/Transaction.kt`
- Create: `app/src/main/java/net/maerkl/kassierapp/data/local/TransactionDao.kt`

- [ ] **Step 1: Create Transaction entity**

Create `app/src/main/java/net/maerkl/kassierapp/data/local/Transaction.kt`:

```kotlin
package net.maerkl.kassierapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val paymentMethod: String,  // "BAR" or "KARTE"
    val totalAmount: Double,
    val txCode: String? = null,
    val collectionId: Long = 1
)
```

- [ ] **Step 2: Create TransactionDao**

Create `app/src/main/java/net/maerkl/kassierapp/data/local/TransactionDao.kt`:

```kotlin
package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Query("""
        SELECT * FROM transactions
        WHERE collectionId = :collectionId AND timestamp >= :startOfDay
        ORDER BY timestamp DESC
    """)
    fun getTodayTransactions(collectionId: Long, startOfDay: Long): Flow<List<Transaction>>
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Transaction.kt \
       app/src/main/java/net/maerkl/kassierapp/data/local/TransactionDao.kt
git commit -m "feat: add Transaction entity and DAO"
```

---

### Task 2: Modify Sale Entity & DAO

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt` (line 7-16)
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt` (add query after line 46)

- [ ] **Step 1: Add transactionId to Sale**

In `Sale.kt`, add `transactionId` field to the `Sale` data class. The field goes after `collectionId`:

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
    val collectionId: Long = 1,
    val transactionId: Long = 0
)
```

- [ ] **Step 2: Add getSalesByTransactionId to SaleDao**

In `SaleDao.kt`, add this query at the end of the interface (before the closing `}`):

```kotlin
@Query("SELECT * FROM sales WHERE transactionId = :transactionId")
suspend fun getSalesByTransactionId(transactionId: Long): List<Sale>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt \
       app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt
git commit -m "feat: add transactionId to Sale, add query for sales by transaction"
```

---

### Task 3: Database Migration v4 → v5

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt`

- [ ] **Step 1: Update database version and register Transaction**

In `AppDatabase.kt`, change the `@Database` annotation (line 13):

```kotlin
@Database(entities = [Article::class, Sale::class, ArticleCollection::class, Transaction::class], version = 5)
```

- [ ] **Step 2: Add TransactionDao accessor**

Add after `abstract fun articleCollectionDao(): ArticleCollectionDao` (line 17):

```kotlin
abstract fun transactionDao(): TransactionDao
```

- [ ] **Step 3: Add MIGRATION_4_5**

Add after the `MIGRATION_3_4` block (after line 54):

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                timestamp INTEGER NOT NULL,
                paymentMethod TEXT NOT NULL,
                totalAmount REAL NOT NULL,
                txCode TEXT,
                collectionId INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("ALTER TABLE sales ADD COLUMN transactionId INTEGER NOT NULL DEFAULT 0")
    }
}
```

- [ ] **Step 4: Register the migration**

In the `getDatabase` method (line 66), add `MIGRATION_4_5` to the builder:

```kotlin
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt
git commit -m "feat: add database migration v4->v5 for transactions table"
```

---

### Task 4: Update Payment Flow in MainViewModel

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt`

- [ ] **Step 1: Add transactionDao field**

In `MainViewModel`, add after `private val saleDao = app.database.saleDao()` (line 29):

```kotlin
private val transactionDao = app.database.transactionDao()
```

- [ ] **Step 2: Add today's transactions Flow**

Add after the `remainingStock` StateFlow block (after line 62):

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
val todayTransactions: StateFlow<List<Transaction>> = activeCollectionId.flatMapLatest { collectionId ->
    transactionDao.getTodayTransactions(collectionId, startOfToday())
}.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

suspend fun getSalesForTransaction(transactionId: Long): List<Sale> {
    return saleDao.getSalesByTransactionId(transactionId)
}
```

Add `import net.maerkl.kassierapp.data.local.Transaction` to the imports.

- [ ] **Step 3: Update onPaymentSuccess to accept txCode**

Change `onPaymentSuccess()` (line 143-149) to:

```kotlin
fun onPaymentSuccess(txCode: String? = null) {
    saveSales("KARTE", txCode)
    clearCart()
    viewModelScope.launch {
        _snackbarMessage.emit("Zahlung erfolgreich")
    }
}
```

- [ ] **Step 4: Update cashPayment to use saveSales with null txCode**

Change `cashPayment()` (line 134-141) to:

```kotlin
fun cashPayment() {
    if (_cart.value.isEmpty()) return
    saveSales("BAR", null)
    clearCart()
    viewModelScope.launch {
        _snackbarMessage.emit("Barzahlung erfasst")
    }
}
```

- [ ] **Step 5: Rewrite saveSales to create Transaction first**

Replace the `saveSales` method (lines 170-187) with:

```kotlin
private fun saveSales(paymentMethod: String, txCode: String?) {
    val now = System.currentTimeMillis()
    val collectionId = activeCollectionId.value
    val total = cartTotal

    viewModelScope.launch {
        val transactionId = transactionDao.insert(
            Transaction(
                timestamp = now,
                paymentMethod = paymentMethod,
                totalAmount = total,
                txCode = txCode,
                collectionId = collectionId
            )
        )

        val sales = _cart.value.map { item ->
            Sale(
                articleName = item.article.name,
                articleEmoji = item.article.emoji,
                articlePrice = item.article.price,
                quantity = item.quantity,
                paymentMethod = paymentMethod,
                timestamp = now,
                collectionId = collectionId,
                transactionId = transactionId
            )
        }
        saleDao.insertAll(sales)
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt
git commit -m "feat: create Transaction on each payment, store txCode for card payments"
```

---

### Task 5: Pass TX_CODE from MainActivity to ViewModel

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/MainActivity.kt` (lines 295-301)

- [ ] **Step 1: Extract TX_CODE and pass to ViewModel**

In `onActivityResult`, change the `REQUEST_CODE_CHECKOUT` branch (lines 295-301) to:

```kotlin
REQUEST_CODE_CHECKOUT -> {
    val resultCode = data?.extras?.getInt(SumUpAPI.Response.RESULT_CODE)
    if (resultCode == SumUpAPI.Response.ResultCode.SUCCESSFUL) {
        val txCode = data?.extras?.getString(SumUpAPI.Response.TX_CODE)
        mainViewModel?.onPaymentSuccess(txCode)
    } else {
        mainViewModel?.onPaymentFailed()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/MainActivity.kt
git commit -m "feat: pass SumUp TX_CODE to ViewModel on successful card payment"
```

---

### Task 6: Transaction History Dialog UI

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt`

- [ ] **Step 1: Add state and make clock clickable**

In `MainScreen`, add state variable after `var currentTime` (near line 97):

```kotlin
var showTransactionHistory by remember { mutableStateOf(false) }
```

Wrap the clock Text (lines 211-218) with a `.clickable` modifier:

```kotlin
if (currentTime.isNotBlank()) {
    Text(
        text = "\uD83D\uDD50 $currentTime",
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(end = 4.dp)
            .clickable { showTransactionHistory = true }
    )
}
```

- [ ] **Step 2: Add TransactionHistoryDialog composable**

Add this composable function at the bottom of `MainScreen.kt` (after the `StockEditDialog` composable):

```kotlin
@Composable
private fun TransactionHistoryDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val transactions by viewModel.todayTransactions.collectAsState()
    var expandedTransactionId by remember { mutableStateOf<Long?>(null) }
    var expandedSales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

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
                    items(transactions, key = { it.id }) { transaction ->
                        val isExpanded = expandedTransactionId == transaction.id
                        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(transaction.timestamp))
                        val methodIcon = if (transaction.paymentMethod == "KARTE") "\uD83D\uDCB3" else "\uD83D\uDCB5"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isExpanded) {
                                        expandedTransactionId = null
                                        expandedSales = emptyList()
                                    } else {
                                        expandedTransactionId = transaction.id
                                        coroutineScope.launch {
                                            expandedSales = viewModel.getSalesForTransaction(transaction.id)
                                        }
                                    }
                                }
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
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = String.format("%.2f \u20AC", transaction.totalAmount),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(4.dp))
                                expandedSales.forEach { sale ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${sale.articleEmoji} ${sale.quantity}\u00D7 ${sale.articleName}",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = String.format("%.2f \u20AC", sale.articlePrice * sale.quantity),
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
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
}
```

- [ ] **Step 3: Show the dialog when state is true**

In `MainScreen`, add after the `stockEditArticle?.let` block (after line 294):

```kotlin
if (showTransactionHistory) {
    TransactionHistoryDialog(
        viewModel = viewModel,
        onDismiss = { showTransactionHistory = false }
    )
}
```

- [ ] **Step 4: Add missing imports**

Add these imports at the top of `MainScreen.kt`:

```kotlin
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.data.local.Sale
import net.maerkl.kassierapp.data.local.Transaction
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt
git commit -m "feat: add transaction history dialog accessible via clock tap"
```

---

### Task 7: Build & Verify

- [ ] **Step 1: Build the project**

```bash
cd /Users/sascha/Code/fsg/Kassierapp && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Fix any compilation errors**

If there are compilation errors, fix them and rebuild.

- [ ] **Step 3: Commit any fixes**

```bash
git add -A && git commit -m "fix: resolve compilation issues"
```

(Only if there were fixes needed.)
