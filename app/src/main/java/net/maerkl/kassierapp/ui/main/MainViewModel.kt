package net.maerkl.kassierapp.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.Sale
import net.maerkl.kassierapp.data.local.Transaction
import net.maerkl.kassierapp.data.local.TransactionWithSales
import net.maerkl.kassierapp.data.remote.RecordTransaktionResult
import net.maerkl.kassierapp.data.remote.TransaktionItem

data class CartItem(val article: Article, val quantity: Int)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val articleDao = app.database.articleDao()
    private val saleDao = app.database.saleDao()
    private val transactionDao = app.database.transactionDao()
    private val collectionDao = app.database.articleCollectionDao()
    private val settings = app.settingsDataStore
    private var nextManualPriceId = -1L

    private val activeCollectionId = settings.activeCollectionId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeCollectionName: StateFlow<String> = activeCollectionId.flatMapLatest { collectionId ->
        collectionDao.getAll().map { collections ->
            collections.find { it.id == collectionId }?.name ?: ""
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val articles = activeCollectionId.flatMapLatest { collectionId ->
        articleDao.getActiveArticles(collectionId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val remainingStock: StateFlow<Map<String, Int>> = activeCollectionId.flatMapLatest { collectionId ->
        val soldFlow = saleDao.getSoldQuantitiesToday(collectionId, startOfToday())
        val articlesFlow = articleDao.getActiveArticles(collectionId)
        combine(articlesFlow, soldFlow) { articleList, soldList ->
            val soldMap = soldList.associate { it.articleName to it.totalSold }
            articleList
                .filter { it.stockQuantity != null }
                .associate { article ->
                    val sold = soldMap[article.name] ?: 0
                    article.name to (article.stockQuantity!! - sold)
                }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayTransactions: StateFlow<List<TransactionWithSales>> = activeCollectionId.flatMapLatest { collectionId ->
        transactionDao.getTodayTransactionsWithSales(collectionId, startOfToday())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun startOfToday(): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
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

    fun addManualPriceToCart(price: Double, name: String, originalArticle: Article) {
        val cartArticle = originalArticle.copy(
            id = nextManualPriceId--,
            name = name,
            price = price
        )
        val current = _cart.value.toMutableList()
        current.add(CartItem(cartArticle, 1))
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
        saveSales("bar", null)
        clearCart()
        viewModelScope.launch {
            _snackbarMessage.emit("Barzahlung erfasst")
        }
    }

    fun onPaymentSuccess(txCode: String? = null) {
        saveSales("sumup", txCode)
        clearCart()
        viewModelScope.launch {
            _snackbarMessage.emit("Zahlung erfolgreich")
        }
    }

    fun updateStockQuantity(article: Article, newRemaining: Int?) {
        viewModelScope.launch {
            if (newRemaining == null) {
                articleDao.update(article.copy(stockQuantity = null))
            } else {
                val soldToday = saleDao.getSoldQuantityToday(
                    article.collectionId, article.name, startOfToday()
                )
                articleDao.update(article.copy(stockQuantity = newRemaining + soldToday))
            }
        }
    }

    fun onPaymentFailed() {
        viewModelScope.launch {
            _snackbarMessage.emit("Zahlung fehlgeschlagen")
        }
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
                    val mode = app.authModeResolver.authMode.first()
                    val token = when (mode) {
                        net.maerkl.kassierapp.data.repository.AuthMode.Backend -> try {
                            app.sumupTokenRepository.getAccessToken()
                        } catch (e: Exception) {
                            _snackbarMessage.emit("SumUp-Token-Fehler: ${e.message}")
                            return@launch
                        }
                        net.maerkl.kassierapp.data.repository.AuthMode.Manual -> settings.oauthToken.first()
                        net.maerkl.kassierapp.data.repository.AuthMode.None -> {
                            _snackbarMessage.emit("Keine SumUp-Authentifizierung konfiguriert")
                            return@launch
                        }
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
            if (conn.responseCode in 200..299) {
                null
            } else {
                val errorBody = try {
                    conn.errorStream?.bufferedReader()?.readText()
                } catch (_: Exception) { null }
                val message = if (errorBody != null) {
                    try {
                        org.json.JSONObject(errorBody).optString("message", errorBody)
                    } catch (_: Exception) { errorBody }
                } else {
                    "HTTP ${conn.responseCode}"
                }
                message
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun saveSales(paymentMethod: String, txCode: String?) {
        val now = System.currentTimeMillis()
        val collectionId = activeCollectionId.value
        val total = cartTotal
        val cartSnapshot = _cart.value

        viewModelScope.launch {
            val transactionId = transactionDao.insert(
                Transaction(
                    timestamp = now,
                    paymentMethod = paymentMethod,
                    totalAmount = total,
                    txCode = txCode,
                    collectionId = collectionId
                )
            )

            val sales = cartSnapshot.map { item ->
                Sale(
                    articleName = item.article.name,
                    articleEmoji = item.article.emoji,
                    articlePrice = item.article.price,
                    quantity = item.quantity,
                    paymentMethod = paymentMethod,
                    timestamp = now,
                    collectionId = collectionId,
                    transactionId = transactionId
                )
            }
            saleDao.insertAll(sales)

            val transaktionItems = cartSnapshot.map { item ->
                TransaktionItem(
                    artikelId = item.article.id.toString(),
                    name = item.article.name,
                    anzahl = item.quantity,
                    einzelpreis = item.article.price,
                    taxRate = 0,
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
}
