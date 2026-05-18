# Katalog-Sync: Firestore-Anbindung für Artikel & Sortimente

**Status:** Design
**Datum:** 2026-05-18

## Ziel

Das Tablet zieht seinen Verkaufskatalog (Artikel + Sortimente) live aus Firestore statt aus einer lokalen Room-Datenbank. Die Vereinskassen-Webapp wird damit zur einzigen Quelle der Wahrheit für den Katalog; das Tablet ist reiner Read-Consumer für Katalog-Daten und schreibt weiterhin nur Transaktionen.

Gleichzeitig wird der **Manual-Mode** (legacy SumUp-OAuth ohne Pairing) komplett entfernt. Backend-Pairing ist der einzige unterstützte Modus.

## Nicht-Ziele

- **Stock-Tracking** wird in diesem Schritt entfernt. Sobald das Firestore-Artikel-Schema ein `stockQuantity`-Feld liefert, wird die Anzeige separat reimplementiert.
- **Manual Price Article** („Freier Betrag") wird entfernt. Falls später gewünscht: eigenes Folge-Feature.
- **Datenmigration:** Es gibt keine produktiv-relevanten Daten. Bestehende Room-Tabellen `articles` / `article_collections` werden destruktiv gedroppt (`fallbackToDestructiveMigration`).

## Architektur-Überblick

Hard Switch: Im neuen Modell gibt es nur noch zwei Zustände — **gepaired** oder **nicht gepaired**. Ohne Pairing ist die Kassen-UI nicht benutzbar; der User wird auf die `PairingScreen` geschickt mit der Aufforderung, im Admin-Portal einen Aktivierungscode anzulegen.

Datenfluss (Backend-Mode):
```
Firestore vereine/{vereinId}/artikel       ─┐
Firestore vereine/{vereinId}/sortimente    ─┼──► MainViewModel ──► MainScreen
EncryptedSharedPreferences (selectedSortimentId) ─┘
```

Firestore-SDK übernimmt das Caching. Beim Start ohne Verbindung emittiert der Snapshot-Listener zuerst Cache-Daten, später Server-Daten — keine Fehler-UI für Offline-Betrieb.

## Cleanup

### Zu entfernende Files

- `data/repository/AuthModeResolver.kt`
- `data/local/Article.kt`, `ArticleDao.kt`, `ArticleCollection.kt`, `ArticleCollectionDao.kt`
- `ui/settings/ArticleManagementScreen.kt`
- `ui/components/ArticleDialog.kt`
- `ui/components/ManualPriceDialog.kt`

### Zu bereinigende Files

- `ui/settings/SettingsScreen.kt`: gesamter „Manuell"-Block (Affiliate Key + OAuth-Token), „Aktiver Modus"-Anzeige entfernen
- `ui/settings/SettingsViewModel.kt`: `authModeResolver`-Verwendung weg
- `data/preferences/SettingsDataStore.kt`: `affiliateKey`, `oauthToken`, `activeCollectionId` entfernen
- `KassierApplication.kt`: `authModeResolver`-Field weg; neue Singletons `artikelRepository`, `sortimentRepository`, `selectedSortimentStore` hinzu
- `MainActivity.kt`: AuthMode-Verzweigungen entfernen
- `ui/main/MainViewModel.kt`: kompletter Rewrite (siehe unten)
- `ui/main/MainScreen.kt`: neue UI-States (siehe unten)
- `ui/statistics/StatisticsViewModel.kt`: `activeCollectionId`-Logik weg

### Room-DB

- `AppDatabase`: Version hochziehen, `fallbackToDestructiveMigration()` aktivieren
- Entities `Article` und `ArticleCollection` aus DB-Konfiguration entfernen
- `Sale.collectionId` und `Transaction.collectionId` entfernen
- Alle `:collectionId`-Parameter aus `SaleDao` und `TransactionDao` entfernen, Queries vereinfachen
- `SaleDao.deleteAllByCollection` ersatzlos entfernen (einziger Aufrufer war der entfallende Article-Management-Pfad in `SettingsViewModel`)

### SumUp-Refund

`MainViewModel.refundTransaction`: Drei AuthMode-Zweige fallen weg, nur noch `sumupTokenRepository.getAccessToken()` (Backend-Mode).

## Neue Datenschicht

### `data/remote/ArtikelRepository.kt`

```kotlin
data class Artikel(
    val id: String,           // Firestore doc id
    val name: String,
    val emoji: String?,
    val preisCent: Long,
    val taxRate: Int,         // 0 | 7 | 19
    val aktiv: Boolean,
)

sealed class CatalogState<out T> {
    data object Loading : CatalogState<Nothing>()
    data class Data<T>(val items: List<T>) : CatalogState<T>()
    data object PermissionDenied : CatalogState<Nothing>()
}

class ArtikelRepository(
    private val firestore: FirebaseFirestore,
) {
    fun observeAktive(vereinId: String): Flow<CatalogState<Artikel>>
}
```

Implementierung via `callbackFlow` + `addSnapshotListener`:
- Initial `CatalogState.Loading` emittieren
- Bei Snapshot: Mappen → client-side auf `aktiv == true` filtern → `CatalogState.Data`
- Bei `FirebaseFirestoreException` mit `Code.PERMISSION_DENIED`: `CatalogState.PermissionDenied`
- `awaitClose { registration.remove() }` für sauberes Cleanup

### `data/remote/SortimentRepository.kt`

```kotlin
data class Sortiment(
    val id: String,
    val name: String,
    val articleIds: List<String>,
)

class SortimentRepository(
    private val firestore: FirebaseFirestore,
) {
    fun observe(vereinId: String): Flow<CatalogState<Sortiment>>
}
```

Analog zu `ArtikelRepository`. Sortimente werden nach `name` aufsteigend sortiert (kein `createdAt`-Feld im Modell, da die Render-Reihenfolge nicht davon abhängt).

## Sortiment-Auswahl-Persistenz

### `data/local/SelectedSortimentStore.kt`

```kotlin
class SelectedSortimentStore(context: Context) {
    private val prefs: SharedPreferences  // EncryptedSharedPreferences
    val selectedSortimentId: StateFlow<String?>
    fun set(id: String?)
}
```

Nutzt `EncryptedSharedPreferences` mit `AES256_SIV` / `AES256_GCM` analog zum bestehenden `DeviceSessionStore`. Backing-`StateFlow` für reaktive Kombination im ViewModel.

Beim Unpair wird `set(null)` gerufen.

## MainViewModel-Umbau

### UI-State

```kotlin
sealed class KassenUiState {
    data object Loading : KassenUiState()
    data object NotPaired : KassenUiState()
    data object NoSortimente : KassenUiState()
    data class ChooseSortiment(val sortimente: List<Sortiment>) : KassenUiState()
    data class Ready(
        val sortiment: Sortiment,
        val articles: List<Artikel>,
        val allSortimente: List<Sortiment>,
    ) : KassenUiState()
}
```

### Flow-Komposition

```kotlin
val uiState: StateFlow<KassenUiState> = sessionRepo.pairingState
    .flatMapLatest { pair ->
        when (pair) {
            !is PairingState.Paired -> flowOf(KassenUiState.NotPaired)
            else -> combine(
                artikelRepo.observeAktive(pair.vereinId),
                sortimentRepo.observe(pair.vereinId),
                selectedSortimentStore.selectedSortimentId,
            ) { a, s, selectedId -> deriveState(a, s, selectedId) }
        }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KassenUiState.Loading)
```

### `deriveState`-Regeln

- Einer der beiden Catalog-Flows in `Loading` → `KassenUiState.Loading`
- Einer der beiden in `PermissionDenied` → `KassenUiState.Loading` (Side-Effect-Channel triggert `unpair()`, danach kippt `pairingState` und liefert `NotPaired`)
- Sortimente-Liste leer → `NoSortimente`
- `selectedId == null` und genau 1 Sortiment vorhanden → Auto-Select, `Ready`
- `selectedId == null` oder ID nicht in Liste → `ChooseSortiment`
- Sonst → `Ready` mit Artikeln in `sortiment.articleIds`-Reihenfolge. Unbekannte oder inaktive IDs werden lautlos übersprungen.

### Side-Effect-Channel für PermissionDenied

```kotlin
viewModelScope.launch {
    artikelStateFlow.combine(sortimentStateFlow) { a, s ->
        a is CatalogState.PermissionDenied || s is CatalogState.PermissionDenied
    }.distinctUntilChanged()
     .filter { it }
     .collect {
        selectedSortimentStore.set(null)
        _cart.value = emptyList()
        sessionRepo.unpair()
     }
}
```

`sessionRepo.unpair()` macht bereits `FirebaseAuth.signOut()` + `DeviceSessionStore.clear()`. Damit wird `pairingState` zu nicht-paired, `uiState` zu `NotPaired`, und die Navigation schickt den User zur `PairingScreen`.

### Cart und Checkout

```kotlin
data class CartItem(val artikel: Artikel, val quantity: Int)
val cartTotalCent: Long get() = _cart.value.sumOf { it.artikel.preisCent * it.quantity }
```

Beim Checkout (Aufrufseite an `TransaktionRepository`):
- `TransaktionItem.artikelId` = `artikel.id` (Firestore-Doc-ID, String)
- `einzelpreis` = `preisCent / 100.0` (Double, Repository-Signatur bleibt unverändert)
- `taxRate` = `artikel.taxRate` (statt hardcoded 0 — echter Bugfix als Nebeneffekt)

`Sale.articleName` bleibt denormalisiert für Statistik-Joins.

### API

- `selectSortiment(id: String)` → persistiert via `SelectedSortimentStore`
- `addToCart(artikel)`, `removeFromCart(artikel)`, `clearCart()` — auf neuen `Artikel`-Typ angepasst
- `checkout()`, `cashPayment()`, `onPaymentSuccess(txCode)` — unverändert in der Logik

## Kassen-UI

`MainScreen.kt` rendert je nach `uiState`:

| State | UI |
|---|---|
| `Loading` | `CircularProgressIndicator` |
| `NotPaired` | Text „Tablet ist nicht aktiviert. Bitte im Admin-Portal einen Aktivierungscode anlegen und am Tablet eingeben." + Button → `PairingScreen` |
| `NoSortimente` | Text „Noch keine Sortimente angelegt. Bitte im Admin-Portal ein Sortiment erstellen." |
| `ChooseSortiment` | Liste von Sortiment-Buttons |
| `Ready` | Header (`sortiment.name` + „Sortiment wechseln"-Button) + Artikel-Grid + Cart-Panel |

**Format-Helper** `Long.centsToEuroString()` → `"€ 1,50"` (deutsche Locale).

**„Sortiment wechseln"-Dialog** zeigt `allSortimente`, ruft `selectSortiment(id)` und persistiert.

**Entfernt** aus `MainScreen`: `ManualPriceDialog`, Stock-Anzeige pro Artikel, `updateStockQuantity`-Aufrufe.

**Erhalten:** Cart-UI, Bar-/Karten-Buttons, Snackbar, Refund-Flow aus `todayTransactions` (greift weiter auf Room `transactions` + `sales` zu).

## Statistik

`StatisticsViewModel`:
- `activeCollectionId`-Logik komplett weg
- `dailySummaries` und `articleSummariesForDay` ohne Sortiment-Filter — Statistik zeigt das gesamte Tablet-Journal

DAO-Queries werden vereinfacht (kein `WHERE collectionId = ?` mehr).

## Sicherheits-Constraints (verifiziert)

- Tablet schreibt nie in `artikel` oder `sortimente` (Repos haben keine Write-Methoden)
- Tablet liest nie aus `transaktionen` (kein Read-Repo dafür; UI-Bedarf gedeckt aus lokalem Room)
- App Check ist beim App-Start initialisiert (Voraussetzung für bereits funktionierendes Pairing)
- `selectedSortimentId` in `EncryptedSharedPreferences` (kein Klartext-Disk-Write)
- Keine Disk-Persistenz von Firebase-ID-Token, SumUp-Access-Token oder Custom-Token (bleibt wie heute)

## Test-Plan

### Unit-Tests

- `ArtikelRepositoryTest`: Snapshot-Mapping, Filter `aktiv == true`, `PERMISSION_DENIED` → `CatalogState.PermissionDenied`
- `SortimentRepositoryTest`: Snapshot-Mapping, Sortierung
- `MainViewModelTest` (testet `deriveState` als reine Funktion):
  - Loading-Propagation
  - Auto-Select bei genau einem Sortiment
  - Unbekannte `selectedId` → `ChooseSortiment`
  - Filter unbekannter / inaktiver Artikel-IDs in `Ready`
  - Empty Sortimente → `NoSortimente`

### Manuelle Smoke-Tests (Akzeptanzkriterien)

1. Admin ändert Preis in Webapp → Tablet zeigt neuen Preis ohne App-Neustart
2. Admin setzt Artikel auf `aktiv = false` → Artikel verschwindet aus Kassen-UI
3. Admin löscht aktives Sortiment → Tablet zeigt `ChooseSortiment` oder `NoSortimente`
4. Backend revoziert Gerät (`isGeraet`-Claim weg) → erstes Snapshot-Event nach Revocation → Unpair, Pairing-Screen
5. Tablet startet offline → letzter Cache-Stand wird angezeigt, keine Fehlermeldung
6. Verkauf mit gemischten Steuersätzen (z.B. 7% + 19%) → `TransaktionRepository` schreibt korrekte Steueraufschlüsselung (durch echte `taxRate`-Werte statt hardcoded 0)

## Offene Punkte für Folge-Features

- Stock-Tracking neu auf Basis Firestore-Backend
- „Freier Betrag"-Button (ersetzt entfernten Manual Price Article)
