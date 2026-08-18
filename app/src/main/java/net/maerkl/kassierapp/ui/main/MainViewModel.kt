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
import net.maerkl.kassierapp.data.repository.SumupTransactionVerifier
import net.maerkl.kassierapp.data.repository.VerificationOutcome

data class CartItem(val artikel: Artikel, val quantity: Int)

/**
 * Rueckmeldung zu einer Zahlung. Wird als grossflaechige Vollbild-Einblendung
 * angezeigt: ein kleiner Toast geht im Verkaufsstress unter. Neben Erfolg und
 * Fehlschlag gibt es zwei "unklar"-Zustaende: waehrend der Statuspruefung und
 * wenn sie ergebnislos blieb — beide duerfen NIE als Fehlschlag erscheinen,
 * sonst wird doppelt kassiert.
 */
sealed class PaymentFeedback {
    data class Success(val title: String, val detail: String? = null) : PaymentFeedback()
    data class Failed(val reason: String? = null) : PaymentFeedback()

    /** Ausgang unbekannt, Statuspruefung laeuft — nicht wegklickbar. */
    data class Verifying(val detail: String? = null) : PaymentFeedback()

    /** Statuspruefung ergebnislos — Kassierer muss im SumUp-Dashboard nachsehen. */
    data class Unverified(val detail: String? = null) : PaymentFeedback()
}

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

    private val _paymentFeedback = MutableStateFlow<PaymentFeedback?>(null)
    val paymentFeedback: StateFlow<PaymentFeedback?> = _paymentFeedback.asStateFlow()

    /** Laeuft gerade ein Kartenzahlvorgang (inkl. Statuspruefung)? Sperrt "Kassieren". */
    private val _paymentInProgress = MutableStateFlow(false)
    val paymentInProgress: StateFlow<Boolean> = _paymentInProgress.asStateFlow()

    fun dismissPaymentFeedback() {
        // Waehrend der Statuspruefung gibt es nichts zu bestaetigen — die
        // Einblendung bleibt, bis der Ausgang feststeht.
        if (_paymentFeedback.value is PaymentFeedback.Verifying) return
        _paymentFeedback.value = null
    }

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

    fun addFreierPreis(name: String, preisCent: Long, taxRate: Int) {
        if (preisCent <= 0) return
        val finalName = name.trim().ifBlank { FREIER_PREIS_DEFAULT_NAME }
        val artikel = Artikel(
            id = "$FREIER_PREIS_ID_PREFIX${java.util.UUID.randomUUID()}",
            name = finalName,
            emoji = FREIER_PREIS_EMOJI,
            imagePath = null,
            preisCent = preisCent,
            taxRate = taxRate,
            aktiv = true,
        )
        _cart.value = _cart.value + CartItem(artikel, 1)
    }

    companion object {
        const val FREIER_PREIS_SENTINEL_ID = "__freier_preis_sentinel__"
        const val FREIER_PREIS_ID_PREFIX = "freier-preis-"
        const val FREIER_PREIS_DEFAULT_NAME = "Freier Preis"
        const val FREIER_PREIS_EMOJI = "💶"

        val FreierPreisSentinel = Artikel(
            id = FREIER_PREIS_SENTINEL_ID,
            name = FREIER_PREIS_DEFAULT_NAME,
            emoji = FREIER_PREIS_EMOJI,
            imagePath = null,
            preisCent = -1L,
            taxRate = 19,
            aktiv = true,
        )
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
        // Ein Doppel-Tap darf keinen zweiten Checkout starten — zwei parallele
        // SumUp-Aufrufe sind ein direkter Weg zur Doppelabbuchung.
        if (_paymentInProgress.value) return
        val totalCent = cartTotalCent
        if (totalCent > 0) {
            _paymentInProgress.value = true
            viewModelScope.launch {
                _checkoutAmount.emit(totalCent / 100.0)
            }
        }
    }

    fun cashPayment() {
        if (_cart.value.isEmpty()) return
        val amount = cartTotalCent
        saveSales("bar", null)
        clearCart()
        _paymentFeedback.value = PaymentFeedback.Success("Bar kassiert", amount.centsToEuroString())
        logPaymentEvent(amount, "bar", "erfolg")
    }

    fun onPaymentSuccess(txCode: String? = null) {
        // Betrag vor dem Leeren des Warenkorbs sichern — er gehoert in die
        // Rueckmeldung, damit man am Tablet sieht, was gerade kassiert wurde.
        val amount = cartTotalCent
        saveSales("sumup", txCode)
        clearCart()
        _paymentInProgress.value = false
        _paymentFeedback.value = PaymentFeedback.Success("Zahlung erfolgreich", amount.centsToEuroString())
        logPaymentEvent(amount, "sumup", "erfolg", txCode = txCode)
    }

    fun onPaymentFailed(reason: String? = null, sumupResultCode: Int? = null) {
        _paymentInProgress.value = false
        _paymentFeedback.value = PaymentFeedback.Failed(reason)
        logPaymentEvent(
            betragCent = cartTotalCent,
            zahlungsart = "sumup",
            ergebnis = "fehler",
            sumupResultCode = sumupResultCode,
            fehlerDetail = reason,
        )
    }

    /**
     * Das SDK kennt den Ausgang der Zahlung nicht (Verbindungsabbruch mitten in
     * der Transaktion). Die Abbuchung kann trotzdem durchgegangen sein — darum
     * wird der Status ueber die SumUp-API nachgeprueft statt einen Fehler zu
     * zeigen, auf den hin erneut kassiert wuerde.
     */
    fun onPaymentUnknown(foreignTransactionId: String?) {
        val amount = cartTotalCent
        _paymentFeedback.value = PaymentFeedback.Verifying(amount.centsToEuroString())
        viewModelScope.launch {
            when (val outcome = verifyUnknownPayment(foreignTransactionId)) {
                is VerificationOutcome.Confirmed -> {
                    saveSales("sumup", outcome.transactionCode)
                    clearCart()
                    _paymentFeedback.value =
                        PaymentFeedback.Success("Zahlung erfolgreich", amount.centsToEuroString())
                    logPaymentEvent(
                        amount, "sumup", "unbekannt_verifiziert_erfolg",
                        txCode = outcome.transactionCode, foreignTransactionId = foreignTransactionId,
                    )
                }
                VerificationOutcome.ConfirmedFailed -> {
                    _paymentFeedback.value = PaymentFeedback.Failed("Zahlung nicht zustande gekommen")
                    logPaymentEvent(
                        amount, "sumup", "unbekannt_verifiziert_fehler",
                        foreignTransactionId = foreignTransactionId,
                    )
                }
                VerificationOutcome.Unverifiable -> {
                    _paymentFeedback.value = PaymentFeedback.Unverified(amount.centsToEuroString())
                    logPaymentEvent(
                        amount, "sumup", "unbekannt_nicht_klaerbar",
                        foreignTransactionId = foreignTransactionId,
                    )
                }
            }
            _paymentInProgress.value = false
        }
    }

    private fun logPaymentEvent(
        betragCent: Long,
        zahlungsart: String,
        ergebnis: String,
        sumupResultCode: Int? = null,
        sumupMessage: String? = null,
        txCode: String? = null,
        foreignTransactionId: String? = null,
        fehlerDetail: String? = null,
    ) {
        viewModelScope.launch {
            app.zahlungsprotokollRepository.logEvent(
                betragCent = betragCent,
                zahlungsart = zahlungsart,
                ergebnis = ergebnis,
                sumupResultCode = sumupResultCode,
                sumupMessage = sumupMessage,
                txCode = txCode,
                foreignTransactionId = foreignTransactionId,
                fehlerDetail = fehlerDetail,
            )
        }
    }

    private suspend fun verifyUnknownPayment(foreignTransactionId: String?): VerificationOutcome {
        if (foreignTransactionId == null) return VerificationOutcome.Unverifiable
        val session = try {
            app.sumupTokenRepository.getSession()
        } catch (e: Exception) {
            return VerificationOutcome.Unverifiable
        }
        val merchantCode = session.merchantCode ?: return VerificationOutcome.Unverifiable
        // ~60 Sekunden Budget: 15 Versuche im 4-Sekunden-Takt.
        return SumupTransactionVerifier.verify(maxAttempts = 15, delayMs = 4_000) {
            app.sumupTransactionStatusApi.fetchStatus(merchantCode, foreignTransactionId, session.accessToken)
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
