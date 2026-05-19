package net.maerkl.kassierapp.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
import net.maerkl.kassierapp.data.remote.Sortiment
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
    private val catalogSignal: SharedFlow<CatalogPair> = sessionRepo.pairingState
        .flatMapLatest { pair ->
            val paired = pair as? PairingState.Paired
                ?: return@flatMapLatest flowOf(CatalogPair.NotPaired)
            combine(
                artikelRepo.observeAktive(paired.vereinId),
                sortimentRepo.observe(paired.vereinId),
            ) { a, s -> CatalogPair.Loaded(a, s) }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val uiState: StateFlow<KassenUiState> = catalogSignal
        .combine(selectedSortimentStore.selectedSortimentId) { signal, selectedId ->
            when (signal) {
                CatalogPair.NotPaired -> KassenUiState.NotPaired
                is CatalogPair.Loaded -> deriveState(signal.artikel, signal.sortiment, selectedId)
            }
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
        // PERMISSION_DENIED in either catalog flow → unpair
        viewModelScope.launch {
            catalogSignal
                .map { it is CatalogPair.Loaded && (it.artikel is CatalogState.PermissionDenied || it.sortiment is CatalogState.PermissionDenied) }
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
        // Local Room write is the source of truth for refunds and statistics;
        // a failing Firestore sync surfaces as a snackbar but the local record stays.
        // The admin can reconcile any drift from the backend side.
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
                )
            )
            val sales = cartSnapshot.map { item ->
                Sale(
                    articleName = item.artikel.name,
                    articleEmoji = item.artikel.emoji ?: "",
                    articlePrice = item.artikel.preisCent / 100.0,
                    quantity = item.quantity,
                    paymentMethod = paymentMethod,
                    timestamp = now,
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

    private sealed class CatalogPair {
        data object NotPaired : CatalogPair()
        data class Loaded(
            val artikel: CatalogState<Artikel>,
            val sortiment: CatalogState<Sortiment>,
        ) : CatalogPair()
    }
}
