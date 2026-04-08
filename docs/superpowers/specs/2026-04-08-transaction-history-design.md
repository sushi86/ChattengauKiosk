# Transaction History & TX_CODE Storage

## Summary

Add a `Transaction` entity to group sales into logical payment events, store the SumUp `TX_CODE` for card payments, and provide a transaction history modal accessible via the clock in the top bar.

## Data Model

### New Entity: `Transaction`

```kotlin
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val paymentMethod: String,  // "BAR" or "KARTE"
    val totalAmount: Double,
    val txCode: String? = null, // SumUp TX_CODE, null for cash
    val collectionId: Long = 1
)
```

### Modified Entity: `Sale`

Add `transactionId` field:

```kotlin
val transactionId: Long = 0  // FK to Transaction.id
```

Existing sales (from before the migration) will have `transactionId = 0` (no associated transaction). This is acceptable — old data still works for statistics, just won't appear in the transaction history modal.

## Database Migration (v4 -> v5)

```sql
CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    timestamp INTEGER NOT NULL,
    paymentMethod TEXT NOT NULL,
    totalAmount REAL NOT NULL,
    txCode TEXT,
    collectionId INTEGER NOT NULL DEFAULT 1
)

ALTER TABLE sales ADD COLUMN transactionId INTEGER NOT NULL DEFAULT 0
```

## Flow Changes

### Payment Flow

1. **`saveSales(paymentMethod, txCode?)`** now:
   - Creates a `Transaction` first (insert, get generated ID)
   - Creates `Sale` records with `transactionId` set to the new transaction's ID

2. **`cashPayment()`** calls `saveSales("BAR", null)`

3. **`onPaymentSuccess(txCode: String?)`** receives the TX_CODE from MainActivity and calls `saveSales("KARTE", txCode)`

### MainActivity Changes

Extract `TX_CODE` from SumUp response in `onActivityResult`:

```kotlin
val txCode = data?.extras?.getString(SumUpAPI.Response.TX_CODE)
mainViewModel?.onPaymentSuccess(txCode)
```

## UI: Transaction History Modal

### Trigger

Click on the clock display in the TopBar (the existing time text).

### Modal Content

- Title: "Transaktionen heute"
- List, sorted chronologically descending (newest first)
- Each row shows:
  - Time (HH:mm)
  - Payment method icon (cash or card emoji, matching existing style)
  - Total amount (formatted as "X.XX EUR")
- Clicking a row expands it inline to show the cart items (article emoji, name, quantity, line total)
- Only shows today's transactions

### Data Access

New DAO: `TransactionDao`
- `getTodayTransactions(collectionId, startOfDay)`: returns `Flow<List<Transaction>>`

Extended `SaleDao`:
- `getSalesByTransactionId(transactionId)`: returns `List<Sale>` (suspend, not Flow — loaded on expand)

## Out of Scope

- Receipt sending (email/SMS) — future phase
- Cash payment receipts
- `receiptSentTo` field on Transaction — will be added when receipt sending is implemented
