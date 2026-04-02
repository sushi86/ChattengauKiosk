# Manual Price Article — Design Spec

## Overview

Every article collection automatically includes a "Freier Preis" (manual price) article. When the cashier taps it, a numpad dialog opens where they enter a price and optionally a custom name. The article is then added to the cart with that price as a separate line item.

## Recognition

- The manual price article is identified by convention: `Article.name == "__MANUAL_PRICE__"`
- An extension property `Article.isManualPrice` provides a clean check
- Display in the article grid: emoji "✏️", label "Freier Preis", price not shown
- The article always appears as the **last** card in the grid (highest `sortOrder`)

## Numpad Dialog

When the user taps the manual price article, a `ManualPriceDialog` composable opens:

- **Price display** at the top showing the entered amount formatted as EUR
- **Numpad keys**: digits 0–9, comma (decimal separator), backspace/delete
- **Optional name field**: text input with placeholder "Freier Preis"
- **OK button**: enabled only when price > 0; confirms and adds to cart
- **Abbrechen button**: dismisses the dialog without action

Price entry uses a standard text input field with `keyboardType = KeyboardType.Decimal`. The numpad buttons provide an alternative large-button input method that appends digits to the field. The comma key inserts the decimal separator. Validation ensures max 2 decimal places and price > 0.

## Cart Behavior

After the user confirms the dialog:

1. A **copy** of the `__MANUAL_PRICE__` article is created with:
   - `price` = the entered price
   - `name` = the entered name, or "Freier Preis" if left blank
2. A new `CartItem(article = copiedArticle, quantity = 1)` is added to the cart
3. Each manual price entry is **always a separate line** in the cart — no quantity aggregation, even if name and price match a previous entry

In the cart panel, these items display like any other: "1× Freier Preis — 5,00 €".

## Sale Recording

Sales are recorded normally via `saveSales()`. The `Sale` record captures:
- `articleName` = the custom name (or "Freier Preis")
- `articleEmoji` = "✏️"
- `articlePrice` = the entered price
- `quantity` = 1
- `collectionId` = active collection

These appear in statistics with their individual names, just like regular articles.

## Automatic Insertion

### New Collections
- `addCollection()` in `SettingsViewModel` inserts the `__MANUAL_PRICE__` article after the default articles, with `sortOrder` = last position
- `resetArticlesToDefaults()` also includes it

### Existing Collections (Migration)
- On app startup, the app checks every collection for the presence of a `__MANUAL_PRICE__` article
- If missing, it inserts one with `sortOrder` = max existing sortOrder + 1
- This runs as startup logic (e.g., in `AppDatabase` open callback or ViewModel init)

### Default Article List
- `AppDatabase.insertDefaultArticles()` is updated to include the manual price article as the last entry

## Settings UI Protection

- The `__MANUAL_PRICE__` article is **filtered out** of the article list in SettingsScreen
- It cannot be edited, reordered, or deleted by the user
- It does not appear in the article management UI at all

## Files Affected

| File | Change |
|------|--------|
| `Article.kt` | Add `isManualPrice` extension property |
| `MainScreen.kt` | Show manual price article differently (no price label); handle tap to open dialog |
| `MainViewModel.kt` | `addToCart()` branching for manual price; always add as separate cart item |
| `ManualPriceDialog.kt` | New composable: numpad dialog with optional name field |
| `SettingsViewModel.kt` | Include manual price article in `addCollection()` and `resetArticlesToDefaults()` |
| `SettingsScreen.kt` | Filter out `__MANUAL_PRICE__` articles from article list |
| `AppDatabase.kt` | Add manual price article to `insertDefaultArticles()`; add startup migration check |
