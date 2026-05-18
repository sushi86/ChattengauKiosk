# Katalog-Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace local Room-based article/collection management with live Firestore sync; remove manual SumUp mode entirely so backend pairing is the only supported mode.

**Architecture:** Two new Firestore-backed repositories (`ArtikelRepository`, `SortimentRepository`) expose `Flow<CatalogState<T>>` via `addSnapshotListener` + `callbackFlow`. A new `MainViewModel` composes those flows with `EncryptedSharedPreferences`-persisted sortiment selection through a pure `deriveState` function into a `KassenUiState`. Old AuthMode resolver, manual SumUp credentials, local Article/ArticleCollection Room entities, and stock/manual-price UI are deleted. Room is destructively re-created (no migration since no live data).

**Tech Stack:** Kotlin, Jetpack Compose, Firebase Firestore (Kotlin SDK), Firebase Auth, AndroidX Security `EncryptedSharedPreferences`, Room (still for `sales` + `transactions`), JUnit + Mockk + kotlinx-coroutines-test for unit tests.

**Authoritative spec:** `docs/superpowers/specs/2026-05-18-katalog-sync-design.md`

**Reference document (lives in backend repo, not this repo):** `docs/tablet-app-integration.md` section "Direkt-Firestore: Artikel, Sortimente, Transaktionen".

**Convention for every "commit" step:** Use a HEREDOC commit body ending with `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`. Run `./gradlew :app:assembleDebug` before each commit to catch compile breaks.

---

## Phase 1 — New data layer (additive, no breakage)

### Task 1: `CatalogState` + `Artikel` + Mapping function

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/data/remote/CatalogState.kt`
- Create: `app/src/main/java/net/maerkl/kassierapp/data/remote/Artikel.kt`
- Test: `app/src/test/java/net/maerkl/kassierapp/data/remote/ArtikelMapperTest.kt`

- [ ] **Step 1: Create `CatalogState.kt`**

```kotlin
package net.maerkl.kassierapp.data.remote

sealed class CatalogState<out T> {
    data object Loading : CatalogState<Nothing>()
    data class Data<T>(val items: List<T>) : CatalogState<T>()
    data object PermissionDenied : CatalogState<Nothing>()
}
```

- [ ] **Step 2: Create `Artikel.kt` with data class + mapper**

```kotlin
package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot

data class Artikel(
    val id: String,
    val name: String,
    val emoji: String?,
    val preisCent: Long,
    val taxRate: Int,
    val aktiv: Boolean,
)

object ArtikelMapper {
    fun fromDocument(doc: DocumentSnapshot): Artikel? {
        val name = doc.getString("name") ?: return null
        val preis = doc.getLong("preis") ?: return null
        return Artikel(
            id = doc.id,
            name = name,
            emoji = doc.getString("emoji"),
            preisCent = preis,
            taxRate = doc.getLong("taxRate")?.toInt() ?: 0,
            aktiv = doc.getBoolean("aktiv") ?: false,
        )
    }
}
```

- [ ] **Step 3: Write failing tests**

```kotlin
package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtikelMapperTest {

    private fun doc(
        id: String = "a1",
        name: String? = "Bratwurst",
        emoji: String? = "🌭",
        preis: Long? = 250L,
        taxRate: Long? = 7L,
        aktiv: Boolean? = true,
    ): DocumentSnapshot {
        val d = mockk<DocumentSnapshot>()
        every { d.id } returns id
        every { d.getString("name") } returns name
        every { d.getString("emoji") } returns emoji
        every { d.getLong("preis") } returns preis
        every { d.getLong("taxRate") } returns taxRate
        every { d.getBoolean("aktiv") } returns aktiv
        return d
    }

    @Test
    fun `maps complete document`() {
        val a = ArtikelMapper.fromDocument(doc())!!
        assertEquals("a1", a.id)
        assertEquals("Bratwurst", a.name)
        assertEquals("🌭", a.emoji)
        assertEquals(250L, a.preisCent)
        assertEquals(7, a.taxRate)
        assertEquals(true, a.aktiv)
    }

    @Test
    fun `returns null when name missing`() {
        assertNull(ArtikelMapper.fromDocument(doc(name = null)))
    }

    @Test
    fun `returns null when preis missing`() {
        assertNull(ArtikelMapper.fromDocument(doc(preis = null)))
    }

    @Test
    fun `defaults taxRate to 0 when missing`() {
        val a = ArtikelMapper.fromDocument(doc(taxRate = null))!!
        assertEquals(0, a.taxRate)
    }

    @Test
    fun `defaults aktiv to false when missing`() {
        val a = ArtikelMapper.fromDocument(doc(aktiv = null))!!
        assertEquals(false, a.aktiv)
    }

    @Test
    fun `emoji may be null`() {
        val a = ArtikelMapper.fromDocument(doc(emoji = null))!!
        assertNull(a.emoji)
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "net.maerkl.kassierapp.data.remote.ArtikelMapperTest"`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/net/maerkl/kassierapp/data/remote/CatalogState.kt \
        app/src/main/java/net/maerkl/kassierapp/data/remote/Artikel.kt \
        app/src/test/java/net/maerkl/kassierapp/data/remote/ArtikelMapperTest.kt
git commit -m "$(cat <<'EOF'
feat: add Artikel data class and CatalogState sealed type

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `ArtikelRepository`

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/data/remote/ArtikelRepository.kt`

- [ ] **Step 1: Create repository**

```kotlin
package net.maerkl.kassierapp.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class ArtikelRepository(
    private val firestore: FirebaseFirestore,
) {
    fun observeAktive(vereinId: String): Flow<CatalogState<Artikel>> = callbackFlow {
        trySend(CatalogState.Loading)
        val reg = firestore.collection("vereine").document(vereinId)
            .collection("artikel")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w("ArtikelRepo", "PERMISSION_DENIED on artikel listener: ${err.message}")
                        trySend(CatalogState.PermissionDenied)
                    } else {
                        Log.w("ArtikelRepo", "Snapshot error: ${err.code} ${err.message}", err)
                    }
                    return@addSnapshotListener
                }
                val items = snap?.documents.orEmpty()
                    .mapNotNull { ArtikelMapper.fromDocument(it) }
                    .filter { it.aktiv }
                trySend(CatalogState.Data(items))
            }
        awaitClose { reg.remove() }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/remote/ArtikelRepository.kt
git commit -m "$(cat <<'EOF'
feat: add ArtikelRepository with Firestore snapshot listener

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `Sortiment` + Mapping function

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/data/remote/Sortiment.kt`
- Test: `app/src/test/java/net/maerkl/kassierapp/data/remote/SortimentMapperTest.kt`

- [ ] **Step 1: Create `Sortiment.kt`**

```kotlin
package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot

data class Sortiment(
    val id: String,
    val name: String,
    val articleIds: List<String>,
)

object SortimentMapper {
    fun fromDocument(doc: DocumentSnapshot): Sortiment? {
        val name = doc.getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val articleIds = doc.get("articleIds") as? List<String> ?: emptyList()
        return Sortiment(id = doc.id, name = name, articleIds = articleIds)
    }
}
```

- [ ] **Step 2: Write failing tests**

```kotlin
package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SortimentMapperTest {

    private fun doc(
        id: String = "s1",
        name: String? = "Hauptsortiment",
        articleIds: Any? = listOf("a1", "a2"),
    ): DocumentSnapshot {
        val d = mockk<DocumentSnapshot>()
        every { d.id } returns id
        every { d.getString("name") } returns name
        every { d.get("articleIds") } returns articleIds
        return d
    }

    @Test
    fun `maps complete document`() {
        val s = SortimentMapper.fromDocument(doc())!!
        assertEquals("s1", s.id)
        assertEquals("Hauptsortiment", s.name)
        assertEquals(listOf("a1", "a2"), s.articleIds)
    }

    @Test
    fun `returns null when name missing`() {
        assertNull(SortimentMapper.fromDocument(doc(name = null)))
    }

    @Test
    fun `defaults articleIds to empty list when missing`() {
        val s = SortimentMapper.fromDocument(doc(articleIds = null))!!
        assertEquals(emptyList<String>(), s.articleIds)
    }

    @Test
    fun `preserves articleIds order`() {
        val s = SortimentMapper.fromDocument(doc(articleIds = listOf("c", "a", "b")))!!
        assertEquals(listOf("c", "a", "b"), s.articleIds)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "net.maerkl.kassierapp.data.remote.SortimentMapperTest"`
Expected: All 4 tests PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/remote/Sortiment.kt \
        app/src/test/java/net/maerkl/kassierapp/data/remote/SortimentMapperTest.kt
git commit -m "$(cat <<'EOF'
feat: add Sortiment data class with mapper

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `SortimentRepository`

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/data/remote/SortimentRepository.kt`

- [ ] **Step 1: Create repository**

```kotlin
package net.maerkl.kassierapp.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SortimentRepository(
    private val firestore: FirebaseFirestore,
) {
    fun observe(vereinId: String): Flow<CatalogState<Sortiment>> = callbackFlow {
        trySend(CatalogState.Loading)
        val reg = firestore.collection("vereine").document(vereinId)
            .collection("sortimente")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w("SortimentRepo", "PERMISSION_DENIED on sortimente listener: ${err.message}")
                        trySend(CatalogState.PermissionDenied)
                    } else {
                        Log.w("SortimentRepo", "Snapshot error: ${err.code} ${err.message}", err)
                    }
                    return@addSnapshotListener
                }
                val items = snap?.documents.orEmpty()
                    .mapNotNull { SortimentMapper.fromDocument(it) }
                    .sortedBy { it.name }
                trySend(CatalogState.Data(items))
            }
        awaitClose { reg.remove() }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/remote/SortimentRepository.kt
git commit -m "$(cat <<'EOF'
feat: add SortimentRepository with Firestore snapshot listener

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `SelectedSortimentStore`

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/data/local/SelectedSortimentStore.kt`
- Test: `app/src/test/java/net/maerkl/kassierapp/data/local/SelectedSortimentStoreTest.kt`

Note: We use `EncryptedSharedPreferences` (per spec) but expose a small interface so tests can use a fake. Verify the existing `DeviceSessionStore.kt` pattern first — it does exactly this.

- [ ] **Step 1: Create the store with interface**

```kotlin
package net.maerkl.kassierapp.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SelectedSortimentStore {
    val selectedSortimentId: StateFlow<String?>
    fun set(id: String?)
}

class EncryptedSelectedSortimentStore(context: Context) : SelectedSortimentStore {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "selected_sortiment",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(prefs.getString(KEY, null))
    override val selectedSortimentId: StateFlow<String?> = _state.asStateFlow()

    override fun set(id: String?) {
        prefs.edit().apply {
            if (id == null) remove(KEY) else putString(KEY, id)
        }.apply()
        _state.value = id
    }

    companion object {
        private const val KEY = "selectedSortimentId"
    }
}
```

- [ ] **Step 2: Write failing tests using an in-memory fake**

```kotlin
package net.maerkl.kassierapp.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedSortimentStoreTest {

    private class InMemoryStore(initial: String? = null) : SelectedSortimentStore {
        private val _state = MutableStateFlow(initial)
        override val selectedSortimentId: StateFlow<String?> = _state.asStateFlow()
        override fun set(id: String?) { _state.value = id }
    }

    @Test
    fun `initial value is null`() {
        val s = InMemoryStore()
        assertNull(s.selectedSortimentId.value)
    }

    @Test
    fun `set persists value`() {
        val s = InMemoryStore()
        s.set("sort-1")
        assertEquals("sort-1", s.selectedSortimentId.value)
    }

    @Test
    fun `set null clears value`() {
        val s = InMemoryStore("sort-1")
        s.set(null)
        assertNull(s.selectedSortimentId.value)
    }
}
```

(These tests exercise the interface contract; the encrypted impl is thin glue over `SharedPreferences` and not unit-testable without Android context.)

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "net.maerkl.kassierapp.data.local.SelectedSortimentStoreTest"`
Expected: All 3 tests PASS.

- [ ] **Step 4: Commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/net/maerkl/kassierapp/data/local/SelectedSortimentStore.kt \
        app/src/test/java/net/maerkl/kassierapp/data/local/SelectedSortimentStoreTest.kt
git commit -m "$(cat <<'EOF'
feat: add SelectedSortimentStore backed by EncryptedSharedPreferences

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 2 — Pure `deriveState` function

### Task 6: `KassenUiState` + `deriveState` + tests

**Files:**
- Create: `app/src/main/java/net/maerkl/kassierapp/ui/main/KassenUiState.kt`
- Test: `app/src/test/java/net/maerkl/kassierapp/ui/main/DeriveStateTest.kt`

- [ ] **Step 1: Create the state types and pure function**

```kotlin
package net.maerkl.kassierapp.ui.main

import net.maerkl.kassierapp.data.remote.Artikel
import net.maerkl.kassierapp.data.remote.CatalogState
import net.maerkl.kassierapp.data.remote.Sortiment

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

fun deriveState(
    artikelState: CatalogState<Artikel>,
    sortimentState: CatalogState<Sortiment>,
    selectedId: String?,
): KassenUiState {
    if (artikelState is CatalogState.PermissionDenied || sortimentState is CatalogState.PermissionDenied) {
        return KassenUiState.Loading
    }
    val artikel = (artikelState as? CatalogState.Data)?.items ?: return KassenUiState.Loading
    val sortimente = (sortimentState as? CatalogState.Data)?.items ?: return KassenUiState.Loading

    if (sortimente.isEmpty()) return KassenUiState.NoSortimente

    val effectiveId = when {
        selectedId != null && sortimente.any { it.id == selectedId } -> selectedId
        sortimente.size == 1 -> sortimente.single().id
        else -> null
    }
    val chosen = effectiveId?.let { id -> sortimente.first { it.id == id } }
        ?: return KassenUiState.ChooseSortiment(sortimente)

    val byId = artikel.associateBy { it.id }
    val orderedArticles = chosen.articleIds.mapNotNull { byId[it] }

    return KassenUiState.Ready(
        sortiment = chosen,
        articles = orderedArticles,
        allSortimente = sortimente,
    )
}
```

- [ ] **Step 2: Write failing tests**

```kotlin
package net.maerkl.kassierapp.ui.main

import net.maerkl.kassierapp.data.remote.Artikel
import net.maerkl.kassierapp.data.remote.CatalogState
import net.maerkl.kassierapp.data.remote.Sortiment
import org.junit.Assert.assertEquals
import org.junit.Test

class DeriveStateTest {

    private fun art(id: String, aktiv: Boolean = true) =
        Artikel(id = id, name = id, emoji = null, preisCent = 100, taxRate = 0, aktiv = aktiv)
    private fun sort(id: String, vararg ids: String) =
        Sortiment(id = id, name = id, articleIds = ids.toList())

    @Test
    fun `loading when artikel still loading`() {
        val s = deriveState(CatalogState.Loading, CatalogState.Data(emptyList()), null)
        assertEquals(KassenUiState.Loading, s)
    }

    @Test
    fun `loading when sortimente still loading`() {
        val s = deriveState(CatalogState.Data(emptyList()), CatalogState.Loading, null)
        assertEquals(KassenUiState.Loading, s)
    }

    @Test
    fun `loading when artikel permission denied`() {
        val s = deriveState(CatalogState.PermissionDenied, CatalogState.Data(emptyList()), null)
        assertEquals(KassenUiState.Loading, s)
    }

    @Test
    fun `loading when sortimente permission denied`() {
        val s = deriveState(CatalogState.Data(emptyList()), CatalogState.PermissionDenied, null)
        assertEquals(KassenUiState.Loading, s)
    }

    @Test
    fun `no sortimente when list empty`() {
        val s = deriveState(CatalogState.Data(listOf(art("a1"))), CatalogState.Data(emptyList()), null)
        assertEquals(KassenUiState.NoSortimente, s)
    }

    @Test
    fun `auto-selects single sortiment`() {
        val only = sort("s1", "a1")
        val s = deriveState(
            CatalogState.Data(listOf(art("a1"))),
            CatalogState.Data(listOf(only)),
            null,
        )
        assertEquals(KassenUiState.Ready(only, listOf(art("a1")), listOf(only)), s)
    }

    @Test
    fun `prompts choose when multiple sortimente and none selected`() {
        val list = listOf(sort("s1"), sort("s2"))
        val s = deriveState(CatalogState.Data(emptyList()), CatalogState.Data(list), null)
        assertEquals(KassenUiState.ChooseSortiment(list), s)
    }

    @Test
    fun `prompts choose when selected id unknown`() {
        val list = listOf(sort("s1"), sort("s2"))
        val s = deriveState(CatalogState.Data(emptyList()), CatalogState.Data(list), "ghost")
        assertEquals(KassenUiState.ChooseSortiment(list), s)
    }

    @Test
    fun `uses selected sortiment when valid`() {
        val s1 = sort("s1", "a1")
        val s2 = sort("s2", "a2")
        val s = deriveState(
            CatalogState.Data(listOf(art("a1"), art("a2"))),
            CatalogState.Data(listOf(s1, s2)),
            "s2",
        )
        assertEquals(KassenUiState.Ready(s2, listOf(art("a2")), listOf(s1, s2)), s)
    }

    @Test
    fun `preserves articleIds order`() {
        val s1 = sort("s1", "c", "a", "b")
        val s = deriveState(
            CatalogState.Data(listOf(art("a"), art("b"), art("c"))),
            CatalogState.Data(listOf(s1)),
            "s1",
        ) as KassenUiState.Ready
        assertEquals(listOf("c", "a", "b"), s.articles.map { it.id })
    }

    @Test
    fun `silently skips unknown article ids in sortiment`() {
        val s1 = sort("s1", "a", "ghost", "b")
        val s = deriveState(
            CatalogState.Data(listOf(art("a"), art("b"))),
            CatalogState.Data(listOf(s1)),
            "s1",
        ) as KassenUiState.Ready
        assertEquals(listOf("a", "b"), s.articles.map { it.id })
    }

    @Test
    fun `inactive articles never appear because repo already filters them`() {
        // Repo filters aktiv == true; deriveState trusts that. This test documents the contract.
        val s1 = sort("s1", "a")
        val s = deriveState(
            CatalogState.Data(emptyList()), // repo would have filtered out inactive "a"
            CatalogState.Data(listOf(s1)),
            "s1",
        ) as KassenUiState.Ready
        assertEquals(emptyList<Artikel>(), s.articles)
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --tests "net.maerkl.kassierapp.ui.main.DeriveStateTest"`
Expected: All 12 tests PASS.

- [ ] **Step 4: Commit**

```bash
./gradlew :app:assembleDebug
git add app/src/main/java/net/maerkl/kassierapp/ui/main/KassenUiState.kt \
        app/src/test/java/net/maerkl/kassierapp/ui/main/DeriveStateTest.kt
git commit -m "$(cat <<'EOF'
feat: add KassenUiState and pure deriveState function

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 3 — Wiring + MainViewModel rewrite

### Task 7: Wire new singletons in `KassierApplication`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/KassierApplication.kt`

- [ ] **Step 1: Read the current file**

Run: `grep -n "lateinit\|onCreate\|firestore\|FirebaseFirestore" app/src/main/java/net/maerkl/kassierapp/KassierApplication.kt`

The file already exposes `firestore: FirebaseFirestore`. Confirm before editing.

- [ ] **Step 2: Add three new singleton fields**

Add these `lateinit var` declarations alongside existing ones:

```kotlin
lateinit var artikelRepository: ArtikelRepository
lateinit var sortimentRepository: SortimentRepository
lateinit var selectedSortimentStore: SelectedSortimentStore
```

Add imports:

```kotlin
import net.maerkl.kassierapp.data.local.EncryptedSelectedSortimentStore
import net.maerkl.kassierapp.data.local.SelectedSortimentStore
import net.maerkl.kassierapp.data.remote.ArtikelRepository
import net.maerkl.kassierapp.data.remote.SortimentRepository
```

In `onCreate()`, after `firestore` is initialized, add:

```kotlin
artikelRepository = ArtikelRepository(firestore)
sortimentRepository = SortimentRepository(firestore)
selectedSortimentStore = EncryptedSelectedSortimentStore(this)
```

(`authModeResolver` stays for now — it'll be removed in Task 14.)

- [ ] **Step 3: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/KassierApplication.kt
git commit -m "$(cat <<'EOF'
feat: wire ArtikelRepository, SortimentRepository, SelectedSortimentStore

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Rewrite `MainViewModel`

This is the largest single change. The viewmodel switches from Room to Firestore for the catalog, while still writing the local `Transaction`/`Sale` records (for refund + statistics) and calling `TransaktionRepository` for the Firestore write.

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt` (full rewrite)

**Important context for the implementer:**
- Existing `Sale` entity has `collectionId: Long` — keep writing `0` for now. Task 17 will drop the field.
- Existing `Transaction` entity has `collectionId: Long` — same: write `0` for now.
- `TransaktionRepository.recordTransaktion` signature unchanged. We just pass real `taxRate` and `artikel.id` strings.
- `refundTransaction` keeps lookup of sumup token via `app.sumupTokenRepository` (Backend mode is now the only mode — drop the `when (mode)` switch).
- Cart was `List<CartItem>` over `Article`. Now over `Artikel`. Existing UI references like `_cart.value.sumOf { it.article.price * it.quantity }` change to `preisCent`-based math.

- [ ] **Step 1: Replace the file with the new content**

```kotlin
package net.maerkl.kassierapp.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.local.Sale
import net.maerkl.kassierapp.data.local.Transaction
import net.maerkl.kassierapp.data.local.TransactionWithSales
import net.maerkl.kassierapp.data.remote.Artikel
import net.maerkl.kassierapp.data.remote.CatalogState
import net.maerkl.kassierapp.data.remote.RecordTransaktionResult
import net.maerkl.kassierapp.data.remote.TransaktionItem
import net.maerkl.kassierapp.data.repository.PairingState

data class CartItem(val artikel: Artikel, val quantity: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val saleDao = app.database.saleDao()
    private val transactionDao = app.database.transactionDao()
    private val sessionRepo = app.deviceSessionRepository
    private val artikelRepo = app.artikelRepository
    private val sortimentRepo = app.sortimentRepository
    private val selectedSortimentStore = app.selectedSortimentStore

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<KassenUiState> = sessionRepo.pairingState
        .flatMapLatest { pair ->
            val paired = pair as? PairingState.Paired
                ?: return@flatMapLatest flowOf(KassenUiState.NotPaired)
            combine(
                artikelRepo.observeAktive(paired.vereinId),
                sortimentRepo.observe(paired.vereinId),
                selectedSortimentStore.selectedSortimentId,
            ) { a, s, selectedId -> deriveState(a, s, selectedId) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KassenUiState.Loading)

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _checkoutAmount = MutableSharedFlow<Double>()
    val checkoutAmount = _checkoutAmount.asSharedFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    val cartTotalCent: Long
        get() = _cart.value.sumOf { it.artikel.preisCent * it.quantity }

    val todayTransactions: StateFlow<List<TransactionWithSales>> =
        transactionDao.getTodayTransactionsWithSales(startOfToday())
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Side-effect: PERMISSION_DENIED in either catalog flow → unpair
        viewModelScope.launch {
            sessionRepo.pairingState
                .flatMapLatest { pair ->
                    val paired = pair as? PairingState.Paired
                        ?: return@flatMapLatest flowOf(false)
                    combine(
                        artikelRepo.observeAktive(paired.vereinId),
                        sortimentRepo.observe(paired.vereinId),
                    ) { a, s ->
                        a is CatalogState.PermissionDenied || s is CatalogState.PermissionDenied
                    }
                }
                .distinctUntilChanged()
                .filter { it }
                .collect { handleRevocation() }
        }
    }

    private suspend fun handleRevocation() {
        Log.w("MainViewModel", "PERMISSION_DENIED on catalog listener → unpairing")
        selectedSortimentStore.set(null)
        _cart.value = emptyList()
        sessionRepo.unpair()
        _snackbarMessage.emit("Gerät entkoppelt – bitte neu pairen")
    }

    fun selectSortiment(id: String) {
        selectedSortimentStore.set(id)
    }

    fun addToCart(artikel: Artikel) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.artikel.id == artikel.id }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = current[index].quantity + 1)
        } else {
            current.add(CartItem(artikel, 1))
        }
        _cart.value = current
    }

    fun removeFromCart(artikel: Artikel) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.artikel.id == artikel.id }
        if (index >= 0) {
            val item = current[index]
            if (item.quantity > 1) {
                current[index] = item.copy(quantity = item.quantity - 1)
            } else {
                current.removeAt(index)
            }
            _cart.value = current
        }
    }

    fun clearCart() { _cart.value = emptyList() }

    fun checkout() {
        val totalCent = cartTotalCent
        if (totalCent > 0) {
            viewModelScope.launch {
                _checkoutAmount.emit(totalCent / 100.0)
            }
        }
    }

    fun cashPayment() {
        if (_cart.value.isEmpty()) return
        saveSales("bar", null)
        clearCart()
        viewModelScope.launch { _snackbarMessage.emit("Barzahlung erfasst") }
    }

    fun onPaymentSuccess(txCode: String? = null) {
        saveSales("sumup", txCode)
        clearCart()
        viewModelScope.launch { _snackbarMessage.emit("Zahlung erfolgreich") }
    }

    fun onPaymentFailed() {
        viewModelScope.launch { _snackbarMessage.emit("Zahlung fehlgeschlagen") }
    }

    private val _refundInProgress = MutableStateFlow(false)
    val refundInProgress: StateFlow<Boolean> = _refundInProgress.asStateFlow()

    fun refundTransaction(transaction: Transaction) {
        if (_refundInProgress.value) return
        _refundInProgress.value = true
        viewModelScope.launch {
            try {
                if (transaction.paymentMethod == "sumup") {
                    if (transaction.txCode == null) {
                        _snackbarMessage.emit("Kartenstorno nicht möglich: Kein Transaktionscode vorhanden")
                        return@launch
                    }
                    val token = try {
                        app.sumupTokenRepository.getAccessToken()
                    } catch (e: Exception) {
                        _snackbarMessage.emit("SumUp-Token-Fehler: ${e.message}")
                        return@launch
                    }
                    if (token.isBlank()) {
                        _snackbarMessage.emit("Kein SumUp-Token vorhanden")
                        return@launch
                    }
                    val errorMessage = withContext(Dispatchers.IO) {
                        callSumUpRefund(transaction.txCode, token)
                    }
                    if (errorMessage != null) {
                        _snackbarMessage.emit("SumUp-Rückerstattung fehlgeschlagen: $errorMessage")
                        return@launch
                    }
                }
                transactionDao.markRefunded(transaction.id)
                _snackbarMessage.emit("Transaktion storniert")
            } catch (e: Exception) {
                _snackbarMessage.emit("Fehler: ${e.message}")
            } finally {
                _refundInProgress.value = false
            }
        }
    }

    private fun callSumUpRefund(txCode: String, token: String): String? {
        val url = URL("https://api.sumup.com/v0.1/me/refund/$txCode")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.doOutput = false
        return try {
            if (conn.responseCode in 200..299) null
            else {
                val errorBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                if (errorBody != null) {
                    try { org.json.JSONObject(errorBody).optString("message", errorBody) }
                    catch (_: Exception) { errorBody }
                } else "HTTP ${conn.responseCode}"
            }
        } finally { conn.disconnect() }
    }

    private fun saveSales(paymentMethod: String, txCode: String?) {
        val now = System.currentTimeMillis()
        val cartSnapshot = _cart.value
        val totalEuro = cartTotalCent / 100.0

        viewModelScope.launch {
            val transactionId = transactionDao.insert(
                Transaction(
                    timestamp = now,
                    paymentMethod = paymentMethod,
                    totalAmount = totalEuro,
                    txCode = txCode,
                    collectionId = 0L,
                )
            )
            val sales = cartSnapshot.map { item ->
                Sale(
                    articleName = item.artikel.name,
                    articleEmoji = item.artikel.emoji,
                    articlePrice = item.artikel.preisCent / 100.0,
                    quantity = item.quantity,
                    paymentMethod = paymentMethod,
                    timestamp = now,
                    collectionId = 0L,
                    transactionId = transactionId,
                )
            }
            saleDao.insertAll(sales)

            val transaktionItems = cartSnapshot.map { item ->
                TransaktionItem(
                    artikelId = item.artikel.id,
                    name = item.artikel.name,
                    anzahl = item.quantity,
                    einzelpreis = item.artikel.preisCent / 100.0,
                    taxRate = item.artikel.taxRate,
                )
            }
            val result = app.transaktionRepository.recordTransaktion(
                items = transaktionItems,
                zahlungsart = paymentMethod,
                sumupTransactionId = txCode,
            )
            when (result) {
                is RecordTransaktionResult.PermissionDeniedUnpaired ->
                    _snackbarMessage.emit("Gerät entkoppelt – bitte neu pairen")
                is RecordTransaktionResult.Failure ->
                    _snackbarMessage.emit("Sync-Fehler: ${result.cause.message ?: "unbekannt"}")
                RecordTransaktionResult.Success -> { /* no-op */ }
            }
        }
    }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault())
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
```

- [ ] **Step 2: Update `TransactionDao` signature for `getTodayTransactionsWithSales`**

Right now the DAO has `getTodayTransactionsWithSales(collectionId: Long, startOfDay: Long)`. Drop the `collectionId` param so the new `MainViewModel` compiles.

Open `app/src/main/java/net/maerkl/kassierapp/data/local/TransactionDao.kt` and change:

```kotlin
@Transaction
@Query("""
    SELECT * FROM transactions
    WHERE timestamp >= :startOfDay
    ORDER BY timestamp DESC
""")
fun getTodayTransactionsWithSales(startOfDay: Long): Flow<List<TransactionWithSales>>
```

(Also drop the old single-arg `getTodayTransactions` if it had `collectionId` — but keep its existence; rename if needed. Inspect the file first and remove only the `collectionId = :collectionId AND` fragment.)

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If it fails, it's most likely because `MainScreen` still calls the old API (`articles`, `activeCollectionName`, etc.). Defer those errors — Task 9 rewrites MainScreen.

If you cannot build until Task 9, that's OK: combine Tasks 8 and 9 into a single commit, run the build once at the end. Note that in the commit message below.

- [ ] **Step 4: Commit (only if build is green)**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt \
        app/src/main/java/net/maerkl/kassierapp/data/local/TransactionDao.kt
git commit -m "$(cat <<'EOF'
refactor: rewrite MainViewModel to source catalog from Firestore

Catalog (articles + sortimente) now streams from Firestore via
ArtikelRepository + SortimentRepository. Selected sortiment persists
in EncryptedSharedPreferences. PERMISSION_DENIED on either listener
triggers unpair.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

If the build is red until MainScreen is rewritten, skip this commit and combine with Task 9.

---

## Phase 4 — MainScreen rewrite

### Task 9: Rewrite `MainScreen` to consume `KassenUiState`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt` (substantial rewrite)
- Create: `app/src/main/java/net/maerkl/kassierapp/ui/main/PriceFormat.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/navigation/AppNavigation.kt` (only if route signatures changed — verify with grep)

- [ ] **Step 1: Create price formatter**

```kotlin
package net.maerkl.kassierapp.ui.main

import java.text.NumberFormat
import java.util.Locale

private val euroFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)

fun Long.centsToEuroString(): String = euroFormat.format(this / 100.0)
```

- [ ] **Step 2: Rewrite `MainScreen.kt`**

Read the current file first (`Read` the full file). Then rewrite the top-level `MainScreen` composable so it branches on `uiState`:

Skeleton (adapt existing styling/spacing — preserve the cart panel, snackbar host, scaffold, and transaction-history components that currently work):

```kotlin
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onCheckout: (Double) -> Unit,
    viewModel: MainViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.checkoutAmount.collect { onCheckout(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // ... existing topBar with settings/stats buttons
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (val s = uiState) {
                is KassenUiState.Loading -> CenteredProgress()
                is KassenUiState.NotPaired -> NotPairedView(onNavigateToPairing)
                is KassenUiState.NoSortimente -> NoSortimenteView()
                is KassenUiState.ChooseSortiment -> ChooseSortimentView(
                    sortimente = s.sortimente,
                    onSelect = viewModel::selectSortiment,
                )
                is KassenUiState.Ready -> ReadyView(
                    state = s,
                    cart = cart,
                    onAdd = viewModel::addToCart,
                    onRemove = viewModel::removeFromCart,
                    onClearCart = viewModel::clearCart,
                    onCash = viewModel::cashPayment,
                    onCheckout = viewModel::checkout,
                    onSelectSortiment = viewModel::selectSortiment,
                    todayTransactions = viewModel.todayTransactions,
                    refundInProgress = viewModel.refundInProgress,
                    onRefund = viewModel::refundTransaction,
                )
            }
        }
    }
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun NotPairedView(onNavigateToPairing: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Tablet ist nicht aktiviert", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Bitte im Admin-Portal einen Aktivierungscode anlegen und am Tablet eingeben.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNavigateToPairing) { Text("Zur Aktivierung") }
    }
}

@Composable
private fun NoSortimenteView() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Keine Sortimente vorhanden", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text("Bitte im Admin-Portal ein Sortiment erstellen.", textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChooseSortimentView(sortimente: List<Sortiment>, onSelect: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Sortiment auswählen", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        sortimente.forEach { s ->
            Button(onClick = { onSelect(s.id) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(s.name)
            }
        }
    }
}
```

`ReadyView` is the existing Kassen-UI layout. Convert the parts that need it:
- Grid renders `state.articles` (now `List<Artikel>`). For each: emoji + name + `it.preisCent.centsToEuroString()`. `onClick` = `onAdd(it)`.
- Header shows `state.sortiment.name` + a "Sortiment wechseln" `TextButton` that opens a `SwitchSortimentDialog` with `state.allSortimente`.
- Cart panel: total = `cart.sumOf { it.artikel.preisCent * it.quantity }.centsToEuroString()`. Item rows show `cartItem.artikel.name` and price.
- Remove all `ManualPriceDialog` references.
- Remove stock-quantity badge / overlay.
- Keep the today-transactions list and refund flow.

```kotlin
@Composable
private fun SwitchSortimentDialog(
    current: Sortiment,
    all: List<Sortiment>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sortiment wechseln") },
        text = {
            Column {
                all.forEach { s ->
                    TextButton(
                        onClick = { onSelect(s.id); onDismiss() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            (if (s.id == current.id) "✓ " else "") + s.name,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}
```

- [ ] **Step 3: Update `AppNavigation` if MainScreen's params changed**

Confirm with `grep -n "MainScreen(" app/src/main/java/net/maerkl/kassierapp/ui/navigation/AppNavigation.kt`. Add `onNavigateToPairing` if it wasn't already wired.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

If MainActivity still references things like `viewModel.articles` or `activeCollectionName`, fix those compile errors too — those are removed/renamed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/main/MainScreen.kt \
        app/src/main/java/net/maerkl/kassierapp/ui/main/PriceFormat.kt \
        app/src/main/java/net/maerkl/kassierapp/ui/navigation/AppNavigation.kt
git commit -m "$(cat <<'EOF'
refactor: rewrite MainScreen to render KassenUiState

State-driven rendering: Loading / NotPaired / NoSortimente /
ChooseSortiment / Ready. Adds centsToEuroString helper. Removes
manual-price dialog and stock-quantity overlay.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 5 — Strip legacy paths

### Task 10: Simplify `MainActivity` SumUp paths to Backend-only

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/MainActivity.kt`

- [ ] **Step 1: Edit `autoLoginSumUp`**

Replace the `when (mode)` block in `MainActivity.kt:221-238` so only the Backend branch remains:

```kotlin
private fun autoLoginSumUp() {
    if (SumUpAPI.isLoggedIn()) {
        sumUpLoggedIn = true
        return
    }
    val app = application as KassierApplication
    if (app.deviceSessionRepository.pairingState.value !is PairingState.Paired) return
    lifecycleScope.launch {
        val login = try {
            val token = app.sumupTokenRepository.getAccessToken()
            SumUpLogin.builder(Config.SUMUP_AFFILIATE_KEY).accessToken(token).build()
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "SumUp-Token-Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            return@launch
        }
        SumUpAPI.openLoginActivity(this@MainActivity, login, REQUEST_CODE_LOGIN)
    }
}
```

(Add `import net.maerkl.kassierapp.data.repository.PairingState` and `import net.maerkl.kassierapp.Config` if not already present.)

- [ ] **Step 2: Edit `startSumUpLogin`**

Same treatment for `MainActivity.kt:272-303`: drop the Manual + None branches. If not paired, show toast and return.

```kotlin
private fun startSumUpLogin() {
    if (SumUpAPI.isLoggedIn()) {
        Toast.makeText(this, "Bereits bei SumUp eingeloggt", Toast.LENGTH_SHORT).show()
        return
    }
    val app = application as KassierApplication
    if (app.deviceSessionRepository.pairingState.value !is PairingState.Paired) {
        Toast.makeText(this, "Bitte zuerst Tablet aktivieren", Toast.LENGTH_LONG).show()
        return
    }
    lifecycleScope.launch {
        val login = try {
            val token = app.sumupTokenRepository.getAccessToken()
            SumUpLogin.builder(Config.SUMUP_AFFILIATE_KEY).accessToken(token).build()
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "SumUp-Token-Fehler: ${e.message}", Toast.LENGTH_LONG).show()
            return@launch
        }
        SumUpAPI.openLoginActivity(this@MainActivity, login, REQUEST_CODE_LOGIN)
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/MainActivity.kt
git commit -m "$(cat <<'EOF'
refactor: drop manual SumUp auth path in MainActivity

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 11: Strip manual block from `SettingsScreen`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Read the file**

The "Aktiver Modus" line lives around line 117. The "Manuell" block with Affiliate Key + OAuth Token fields lives around lines 170+. Verify with `grep -n "Manuell\|affiliateKey\|oauthToken\|Aktiver Modus" app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsScreen.kt`.

- [ ] **Step 2: Delete the "Aktiver Modus" Text and the manual block**

- Remove the Text composable that prints `"Aktiver Modus: …"` and the surrounding state collection.
- Remove the entire `Card` / `Column` block that renders the manual Affiliate Key and OAuth Token fields (both the "AKTIV/inaktiv" pill and the OutlinedTextFields).
- Remove the `Kartenleser` button's reliance on `authMode` — keep the button but make it always rely on `pairingState is Paired` (use existing `viewModel.pairingState` or similar; if not exposed, leave the button visible to paired devices only, similar to the existing logic but without the `AuthMode.Backend` check).
- Also remove the "ArticleManagement" navigation button if present (Task 15 deletes the screen).

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsScreen.kt
git commit -m "$(cat <<'EOF'
refactor: remove manual SumUp credentials section from settings

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 12: Clean up `SettingsViewModel`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: Remove fields and methods**

In `SettingsViewModel.kt`:
- Remove `authModeResolver`, `authMode`
- Remove any method/state that read `affiliateKey` / `oauthToken`
- Remove any article-management methods (the ones calling `dao.deleteAllByCollection`, the article CRUD, the import/export helpers tied to articles).
- Remove unused imports.

Verify with: `grep -n "authMode\|affiliateKey\|oauthToken\|deleteAllByCollection\|articleDao\|collectionDao" app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt` before and after — should return nothing after.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/settings/SettingsViewModel.kt
git commit -m "$(cat <<'EOF'
refactor: drop authMode and article-management from SettingsViewModel

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 13: Clean up `SettingsDataStore`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/preferences/SettingsDataStore.kt`

- [ ] **Step 1: Remove three preferences**

Open the file. Remove these keys and their accessor flows / setter functions:
- `affiliateKey`
- `oauthToken`
- `activeCollectionId`

Don't leave dead `Preferences.Key` constants.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

If any other file (e.g., `StatisticsViewModel`) still references `settingsDataStore.activeCollectionId`, fix that in Task 17 (statistics cleanup). For now, expect this build to fail only if a non-cleanup-scoped file still uses these — if so, surface that to the user before continuing.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/preferences/SettingsDataStore.kt
git commit -m "$(cat <<'EOF'
refactor: drop affiliateKey, oauthToken, activeCollectionId from prefs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 14: Delete `AuthModeResolver` and wiring

**Files:**
- Delete: `app/src/main/java/net/maerkl/kassierapp/data/repository/AuthModeResolver.kt`
- Delete: `app/src/test/java/net/maerkl/kassierapp/data/repository/AuthModeResolverTest.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/KassierApplication.kt`

- [ ] **Step 1: Verify no remaining usages**

```bash
grep -rn "AuthMode\|authMode\|authModeResolver" app/src --include="*.kt"
```

Expected: only the two files about to be deleted, plus possibly stale imports — fix any leftover imports inline.

- [ ] **Step 2: Delete the two files**

```bash
git rm app/src/main/java/net/maerkl/kassierapp/data/repository/AuthModeResolver.kt
git rm app/src/test/java/net/maerkl/kassierapp/data/repository/AuthModeResolverTest.kt
```

- [ ] **Step 3: Remove `authModeResolver` field and init from `KassierApplication`**

Delete the `lateinit var authModeResolver: AuthModeResolver` declaration and its initialization in `onCreate()`.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/KassierApplication.kt
git commit -m "$(cat <<'EOF'
refactor: remove AuthModeResolver — backend pairing is the only mode

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 15: Delete article-management UI files

**Files:**
- Delete: `app/src/main/java/net/maerkl/kassierapp/ui/settings/ArticleManagementScreen.kt`
- Delete: `app/src/main/java/net/maerkl/kassierapp/ui/components/ArticleDialog.kt`
- Delete: `app/src/main/java/net/maerkl/kassierapp/ui/components/ManualPriceDialog.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/navigation/AppNavigation.kt` (remove route if present)

- [ ] **Step 1: Verify no remaining usages**

```bash
grep -rn "ArticleManagementScreen\|ArticleDialog\|ManualPriceDialog" app/src --include="*.kt"
```

Note any usages outside the three files; resolve them inline.

- [ ] **Step 2: Delete the files**

```bash
git rm app/src/main/java/net/maerkl/kassierapp/ui/settings/ArticleManagementScreen.kt
git rm app/src/main/java/net/maerkl/kassierapp/ui/components/ArticleDialog.kt
git rm app/src/main/java/net/maerkl/kassierapp/ui/components/ManualPriceDialog.kt
```

- [ ] **Step 3: Remove `articleManagement` route from `AppNavigation` if present**

Inspect `AppNavigation.kt`; drop the `composable("articleManagement") { ... }` entry and any nav-action helper that referenced it.

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/ui/navigation/AppNavigation.kt
git commit -m "$(cat <<'EOF'
refactor: remove article management and manual price dialog UI

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 16: Delete `Article` / `ArticleCollection` Room entities + DAOs

**Files:**
- Delete: `app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt`
- Delete: `app/src/main/java/net/maerkl/kassierapp/data/local/ArticleDao.kt`
- Delete: `app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollection.kt`
- Delete: `app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollectionDao.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt`

- [ ] **Step 1: Verify no remaining usages**

```bash
grep -rn "Article\b\|ArticleDao\|ArticleCollection\b\|ArticleCollectionDao" app/src --include="*.kt"
```

If anything outside these four files still references them, fix that inline (likely only `AppDatabase` should still mention them — that's the next step).

- [ ] **Step 2: Update `AppDatabase`**

- Remove `Article` and `ArticleCollection` from the `entities = [...]` array.
- Remove the `articleDao()` and `articleCollectionDao()` abstract methods.
- Bump `@Database(version = X)` to the next integer.
- Ensure the builder uses `.fallbackToDestructiveMigration()`. If it isn't already there, add it.

- [ ] **Step 3: Delete the four files**

```bash
git rm app/src/main/java/net/maerkl/kassierapp/data/local/Article.kt \
       app/src/main/java/net/maerkl/kassierapp/data/local/ArticleDao.kt \
       app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollection.kt \
       app/src/main/java/net/maerkl/kassierapp/data/local/ArticleCollectionDao.kt
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt
git commit -m "$(cat <<'EOF'
refactor: drop Article and ArticleCollection Room entities

DB version bumped with fallbackToDestructiveMigration. No live data
to preserve.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 17: Drop `collectionId` from `Sale` and `Transaction`

**Files:**
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/Transaction.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/TransactionDao.kt`
- Modify: `app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt` (bump version again)
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt` (drop `collectionId = 0L`)
- Modify: `app/src/main/java/net/maerkl/kassierapp/ui/statistics/StatisticsViewModel.kt`

- [ ] **Step 1: Remove `collectionId` field from `Sale`**

```kotlin
@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleName: String,
    val articleEmoji: String?,
    val articlePrice: Double,
    val quantity: Int,
    val paymentMethod: String,
    val timestamp: Long,
    val transactionId: Long? = null,
)
```

(Remove the `collectionId` field. Keep everything else.)

- [ ] **Step 2: Remove `collectionId` field from `Transaction`**

Same treatment.

- [ ] **Step 3: Strip `collectionId` from all `SaleDao` queries**

For each `@Query`, drop `WHERE s.collectionId = :collectionId AND` and the matching parameter from the function signature. Also delete `deleteAllByCollection` entirely.

The shape becomes:
- `getDailySummaries(): Flow<List<DailySummary>>`
- `getArticleSummariesForDay(startOfDay: Long, endOfDay: Long): Flow<List<ArticleDaySummary>>`
- `getSoldQuantitiesToday(startOfDay: Long): Flow<List<SoldQuantity>>` (if still used)
- `getSoldQuantityToday(articleName: String, startOfDay: Long): Int` (if still used)

Delete `getSoldQuantitiesToday` and `getSoldQuantityToday` if grep shows no callers (stock-tracking is gone).

```bash
grep -rn "getSoldQuantitiesToday\|getSoldQuantityToday" app/src --include="*.kt"
```

- [ ] **Step 4: Strip `collectionId` from `TransactionDao` queries**

Similar to step 3. Function signatures should no longer take `collectionId`.

- [ ] **Step 5: Update `MainViewModel.saveSales`**

In the rewritten `MainViewModel`, remove the `collectionId = 0L` lines from the `Transaction(...)` and `Sale(...)` constructor calls.

- [ ] **Step 6: Update `StatisticsViewModel`**

Remove:
- `activeCollectionId` private val
- `flatMapLatest { collectionId -> ... }` wrapper
- All `:collectionId` parameter passes

The viewmodel should expose:
- `dailySummaries: Flow<List<DailySummary>> = saleDao.getDailySummaries()`
- `getArticleSummariesForDay(dayTimestamp)` calling the simplified DAO.

- [ ] **Step 7: Bump `AppDatabase` version**

Increase `@Database(version = X+1)`. `fallbackToDestructiveMigration()` already covers this from Task 16.

- [ ] **Step 8: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run all tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: All tests PASS. Pay attention to any existing tests touching `Sale`/`Transaction` constructors — adjust them.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/net/maerkl/kassierapp/data/local/Sale.kt \
        app/src/main/java/net/maerkl/kassierapp/data/local/Transaction.kt \
        app/src/main/java/net/maerkl/kassierapp/data/local/SaleDao.kt \
        app/src/main/java/net/maerkl/kassierapp/data/local/TransactionDao.kt \
        app/src/main/java/net/maerkl/kassierapp/data/local/AppDatabase.kt \
        app/src/main/java/net/maerkl/kassierapp/ui/main/MainViewModel.kt \
        app/src/main/java/net/maerkl/kassierapp/ui/statistics/StatisticsViewModel.kt
git commit -m "$(cat <<'EOF'
refactor: drop collectionId from Sale, Transaction, statistics

Sales journal is now per-device with no sortiment filter. DAOs and
StatisticsViewModel simplified. Stock-tracking DAO methods removed.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Phase 6 — Final verification

### Task 18: Full test sweep + manual smoke-test checklist

**Files:** none (verification only)

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Run lint**

Run: `./gradlew :app:lintDebug`
Expected: No new errors. (Warnings about unused resources are OK; flag anything unexpected.)

- [ ] **Step 3: Install on a paired test tablet and run the manual smoke tests**

For each item, observe the result, mark PASS/FAIL, capture screenshots if anything misbehaves. Don't skip any item.

1. **Live price update.** In the admin webapp, change an article's price. The Kassen-UI updates within seconds without app restart.
2. **Deactivate article.** Set an article to `aktiv = false` in the admin webapp. The article disappears from the Kassen-UI live.
3. **Delete sortiment.** Delete the currently active sortiment in the admin. Tablet either shows `ChooseSortiment` (other sortimente present) or `NoSortimente` (none present).
4. **Sortiment switching.** Tap "Sortiment wechseln" in the header. Pick another sortiment. Selection persists across app restart.
5. **Offline start.** Force-stop the app, disable WiFi, launch the app. The last cached catalog renders. No error toast.
6. **Revocation.** In the admin webapp, revoke the device (remove `isGeraet` claim or delete the geräte doc). Within a few seconds the Kassen-UI returns to `NotPaired` and the snackbar shows "Gerät entkoppelt – bitte neu pairen".
7. **Mixed-tax checkout.** Add articles with `taxRate = 7` and `taxRate = 19` to the cart. Pay (cash). Check the created `vereine/{vereinId}/transaktionen/{newDoc}` in Firestore Console: `steuern.netto7`, `steuern.mwst7`, `steuern.netto19`, `steuern.mwst19` are all populated correctly and `gesamtbetrag` equals the cart total. Items array contains `artikelId` strings matching the Firestore article doc IDs.
8. **NotPaired UI.** Unpair the device from settings (or fresh install). Kassen-UI shows the "Tablet ist nicht aktiviert" message with a button to the pairing screen. No legacy "Manuell" settings block visible.
9. **NoSortimente UI.** Pair a device whose verein has zero sortimente. Kassen-UI shows "Keine Sortimente vorhanden".
10. **Auto-select single sortiment.** With exactly one sortiment in the verein, the Kassen-UI lands on `Ready` without prompting.

- [ ] **Step 4: Verify no Firebase writes to forbidden paths**

While exercising the app, open Firestore Console rules audit or filter the device's logcat for `Firestore.*write` events. Confirm: writes only go to `vereine/{vereinId}/transaktionen` — never to `artikel` or `sortimente`.

- [ ] **Step 5: Final cleanup commit (if needed)**

If steps 1–4 surfaced minor fixes, commit each fix as its own `fix:` commit referencing the smoke-test number.

---

## Self-review summary (filled by plan author)

Spec coverage:
- ✅ Section 1 (Cleanup) → Tasks 10–17
- ✅ Section 2 (New data layer) → Tasks 1–4
- ✅ Section 3 (Sortiment persistence) → Task 5
- ✅ Section 4 (MainViewModel) → Tasks 6, 8
- ✅ Section 5 (Error handling / unpair) → embedded in Task 8 (`handleRevocation`), verified in Smoke Test 6
- ✅ Section 6 (UI) → Task 9
- ✅ Section 7 (Statistics / wiring) → Tasks 7, 17

No placeholders. Types are consistent across tasks (`Artikel`, `Sortiment`, `CatalogState<T>`, `KassenUiState`, `selectedSortimentId`, `centsToEuroString`).
