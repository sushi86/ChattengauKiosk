# Artikelverwaltung mit Collections

## Zusammenfassung

Artikel und Verkäufe werden pro Collection (Sportplatz) getrennt verwaltet. Die bisherige flache Artikelliste in den Einstellungen wird durch eine Collection-basierte Verwaltung ersetzt.

## Datenmodell

### Neue Entity: `ArticleCollection`

```kotlin
@Entity(tableName = "article_collections")
data class ArticleCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
```

### Änderung: `Article`

Neues Feld `collectionId: Long` — Foreign Key auf `ArticleCollection.id`.

### Änderung: `Sale`

Neues Feld `collectionId: Long` — Foreign Key auf `ArticleCollection.id`.

### Migration 2 → 3

1. Tabelle `article_collections` erstellen
2. Default-Collection "Standard" einfügen (id = 1)
3. Spalte `collectionId` zu `articles` hinzufügen (DEFAULT 1)
4. Spalte `collectionId` zu `sales` hinzufügen (DEFAULT 1)

### Neue DAOs

**ArticleCollectionDao:**
- `getAll(): Flow<List<ArticleCollection>>`
- `insert(collection): Long`
- `update(collection)`
- `delete(collection)`

**ArticleDao — geänderte Queries:**
- `getActiveArticles(collectionId: Long): Flow<List<Article>>`
- `getAllArticles(collectionId: Long): Flow<List<Article>>`
- `deleteAll(collectionId: Long)`

**SaleDao — geänderte Queries:**
- Alle bestehenden Queries bekommen `collectionId`-Filter

### DataStore

Neuer Wert: `activeCollectionId: Long` (default = 1)

## UI

### Einstellungen → Artikelverwaltung

1. **Collection-Leiste** oben: Alle Collections als Chips, aktive hervorgehoben. "+"-Button zum Anlegen. Long-Press oder Icon zum Umbenennen/Löschen.
2. **Artikelliste** darunter: Artikel der aktuell ausgewählten Collection. Identische Funktionalität wie bisher (Drag-Reorder, Edit, Delete, Hinzufügen, Standard wiederherstellen).

Die bisherige direkte Artikelliste wird entfernt und durch diesen Aufbau ersetzt.

### MainScreen

- Liest `activeCollectionId` aus DataStore
- Zeigt nur Artikel der aktiven Collection
- Neue Sales werden mit `collectionId` gespeichert

### StatisticsScreen

- Filtert Sales nach `activeCollectionId`

### Restliche Einstellungen

SumUp-Konfiguration, Statistik-Link, PIN ändern — keine Änderungen.

## Einschränkungen

- Löschen einer Collection löscht alle zugehörigen Artikel und Sales (CASCADE)
- Die letzte verbleibende Collection kann nicht gelöscht werden
- Beim Löschen der aktiven Collection wird automatisch auf die erste verbleibende gewechselt
