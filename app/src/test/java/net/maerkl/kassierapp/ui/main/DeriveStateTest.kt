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
        val s1 = sort("s1", "a")
        val s = deriveState(
            CatalogState.Data(emptyList()),
            CatalogState.Data(listOf(s1)),
            "s1",
        ) as KassenUiState.Ready
        assertEquals(emptyList<Artikel>(), s.articles)
    }
}
