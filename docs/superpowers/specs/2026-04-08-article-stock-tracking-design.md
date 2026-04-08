# Article Stock Tracking

## Summary

Add optional stock quantity tracking per article. When a stock quantity is set, the remaining amount is displayed on the article card in the cashier view. The remaining stock is calculated by subtracting sold quantities (per collection) from the configured stock.

## Data Model

### Article Entity Change

Add one nullable field to `Article`:

```kotlin
val stockQuantity: Int? = null
```

- `null` = no stock limit (current behavior, unchanged)
- Any `Int` value = initial stock for this article in its collection

### Database Migration (Version 3 → 4)

```sql
ALTER TABLE articles ADD COLUMN stockQuantity INTEGER DEFAULT NULL
```

## Remaining Stock Calculation

Remaining stock per article is computed as:

```
remainingStock = article.stockQuantity - SUM(sales.quantity)
    WHERE sales.articleName = article.name
    AND sales.collectionId = article.collectionId
    AND sales.timestamp >= startOfToday
```

This is implemented as a reactive Flow in `MainViewModel` combining the article list with today's sales data.

### DAO Query

New query in `SaleDao` to get sold quantities per article name for a given collection today:

```kotlin
@Query("""
    SELECT articleName, SUM(quantity) as totalSold
    FROM sales
    WHERE collectionId = :collectionId AND timestamp >= :startOfDay
    GROUP BY articleName
""")
fun getSoldQuantitiesToday(collectionId: Long, startOfDay: Long): Flow<List<SoldQuantity>>
```

With a supporting data class:

```kotlin
data class SoldQuantity(
    val articleName: String,
    val totalSold: Int
)
```

## UI Changes

### Article Card (MainScreen)

When `stockQuantity` is set on an article:

- Display remaining stock below the price in small text
- Format: "Noch 47" (when stock > 0)
- Format: "Ausverkauft" in red (when stock <= 0)
- When no `stockQuantity` is set: no change to current card layout

The article remains clickable even when stock is 0 (warning only, no blocking).

### Article Dialog (ArticleManagementScreen)

Add an optional number input field labeled "Menge" to the existing `ArticleDialog`:

- Numeric keyboard input
- Empty = no stock limit (null)
- Positioned after the existing fields (name, emoji, price)

## Scope

### In Scope
- `stockQuantity` field on Article entity
- DB migration 3→4
- Remaining stock calculation from sales
- Display on article card
- Input in article dialog

### Out of Scope
- Blocking sales when stock is 0
- Stock adjustment/correction UI
- Stock history/logging
- Notifications when stock is low
