package net.maerkl.kassierapp.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import net.maerkl.kassierapp.data.repository.DeviceSessionRepository
import net.maerkl.kassierapp.data.repository.PairingState

/**
 * Schreibt ein Diagnose-Ereignis pro Kassiervorgang (Erfolg, Fehler, geklaerter
 * "unbekannt"-Ausgang) nach `vereine/{vereinId}/zahlungsprotokoll` — unabhaengig
 * vom eigentlichen Umsatz-Eintrag in `transaktionen`. Dient ausschliesslich der
 * Nachvollziehbarkeit bei Reklamationen ("mir wurde zu viel abgebucht") und der
 * Fehlersuche; ein fehlgeschlagener Log-Write darf den Kassiervorgang selbst
 * nie beeintraechtigen, darum wird hier nichts an den Aufrufer zurueckgemeldet.
 */
class ZahlungsprotokollRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val sessionRepo: DeviceSessionRepository,
) {
    suspend fun logEvent(
        betragCent: Long,
        zahlungsart: String,
        ergebnis: String,
        sumupResultCode: Int? = null,
        sumupMessage: String? = null,
        txCode: String? = null,
        foreignTransactionId: String? = null,
        fehlerDetail: String? = null,
    ) {
        val paired = sessionRepo.pairingState.value as? PairingState.Paired ?: return
        val uid = auth.currentUser?.uid ?: return

        val doc = mutableMapOf<String, Any>(
            "betragCent" to betragCent,
            "zahlungsart" to zahlungsart,
            "ergebnis" to ergebnis,
            "geraetId" to paired.geraetId,
            "createdAt" to FieldValue.serverTimestamp(),
            "createdBy" to uid,
        )
        sumupResultCode?.let { doc["sumupResultCode"] = it }
        sumupMessage?.let { doc["sumupMessage"] = it }
        txCode?.let { doc["txCode"] = it }
        foreignTransactionId?.let { doc["foreignTransactionId"] = it }
        fehlerDetail?.let { doc["fehlerDetail"] = it }

        try {
            firestore.collection("vereine")
                .document(paired.vereinId)
                .collection("zahlungsprotokoll")
                .add(doc)
                .await()
        } catch (e: Exception) {
            Log.w("ZahlungsprotokollRepo", "Firestore write failed: ${e.message}", e)
        }
    }
}
