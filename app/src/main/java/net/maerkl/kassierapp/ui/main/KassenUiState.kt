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
