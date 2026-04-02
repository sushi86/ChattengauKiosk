package net.maerkl.kassierapp.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.Sale

data class CartItem(val article: Article, val quantity: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val articleDao = app.database.articleDao()
    private val saleDao = app.database.saleDao()
    private val settings = app.settingsDataStore

    private val activeCollectionId = settings.activeCollectionId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val articles = activeCollectionId.flatMapLatest { collectionId ->
        articleDao.getActiveArticles(collectionId)
    }

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _checkoutAmount = MutableSharedFlow<Double>()
    val checkoutAmount = _checkoutAmount.asSharedFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    val cartTotal: Double
        get() = _cart.value.sumOf { it.article.price * it.quantity }

    fun addToCart(article: Article) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.article.id == article.id }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = current[index].quantity + 1)
        } else {
            current.add(CartItem(article, 1))
        }
        _cart.value = current
    }

    fun removeFromCart(article: Article) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.article.id == article.id }
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

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun checkout() {
        val total = cartTotal
        if (total > 0) {
            viewModelScope.launch {
                _checkoutAmount.emit(total)
            }
        }
    }

    fun cashPayment() {
        if (_cart.value.isEmpty()) return
        saveSales("BAR")
        clearCart()
        viewModelScope.launch {
            _snackbarMessage.emit("Barzahlung erfasst")
        }
    }

    fun onPaymentSuccess() {
        saveSales("KARTE")
        clearCart()
        viewModelScope.launch {
            _snackbarMessage.emit("Zahlung erfolgreich")
        }
    }

    fun onPaymentFailed() {
        viewModelScope.launch {
            _snackbarMessage.emit("Zahlung fehlgeschlagen")
        }
    }

    private fun saveSales(paymentMethod: String) {
        val now = System.currentTimeMillis()
        val collectionId = activeCollectionId.value
        val sales = _cart.value.map { item ->
            Sale(
                articleName = item.article.name,
                articleEmoji = item.article.emoji,
                articlePrice = item.article.price,
                quantity = item.quantity,
                paymentMethod = paymentMethod,
                timestamp = now,
                collectionId = collectionId
            )
        }
        viewModelScope.launch {
            saleDao.insertAll(sales)
        }
    }
}
