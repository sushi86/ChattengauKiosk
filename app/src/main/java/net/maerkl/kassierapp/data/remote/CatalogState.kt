package net.maerkl.kassierapp.data.remote

sealed class CatalogState<out T> {
    data object Loading : CatalogState<Nothing>()
    data class Data<T>(val items: List<T>) : CatalogState<T>()
    data object PermissionDenied : CatalogState<Nothing>()
}
