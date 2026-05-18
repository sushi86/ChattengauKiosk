package net.maerkl.kassierapp.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import net.maerkl.kassierapp.data.repository.DeviceSessionRepository
import net.maerkl.kassierapp.data.repository.PairingState

data class TransaktionItem(
    val artikelId: String,
    val name: String,
    val anzahl: Int,
    val einzelpreis: Double,
    val taxRate: Int,
)

sealed class RecordTransaktionResult {
    data object Success : RecordTransaktionResult()
    data object PermissionDeniedUnpaired : RecordTransaktionResult()
    data class Failure(val cause: Throwable) : RecordTransaktionResult()
}

class TransaktionRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val sessionRepo: DeviceSessionRepository,
) {
    suspend fun recordTransaktion(
        items: List<TransaktionItem>,
        zahlungsart: String,
        sumupTransactionId: String?,
    ): RecordTransaktionResult {
        val paired = sessionRepo.pairingState.value as? PairingState.Paired
            ?: return RecordTransaktionResult.Failure(IllegalStateException("Device not paired"))
        val uid = auth.currentUser?.uid
            ?: return RecordTransaktionResult.Failure(IllegalStateException("No authenticated user"))

        val tax = TaxCalculator.compute(
            items.map { TaxLineItem(einzelpreis = it.einzelpreis, anzahl = it.anzahl, taxRate = it.taxRate) }
        )

        val itemMaps = items.map { item ->
            mapOf(
                "artikelId" to item.artikelId,
                "name" to item.name,
                "anzahl" to item.anzahl,
                "einzelpreis" to item.einzelpreis,
                "taxRate" to item.taxRate,
            )
        }

        val doc = mutableMapOf<String, Any>(
            "items" to itemMaps,
            "gesamtbetrag" to tax.gesamtbetrag,
            "zahlungsart" to zahlungsart,
            "steuern" to mapOf(
                "netto7" to tax.netto7,
                "mwst7" to tax.mwst7,
                "netto19" to tax.netto19,
                "mwst19" to tax.mwst19,
            ),
            "geraetId" to paired.geraetId,
            "createdAt" to FieldValue.serverTimestamp(),
            "createdBy" to uid,
        )
        if (sumupTransactionId != null) {
            doc["sumupTransactionId"] = sumupTransactionId
        }

        return try {
            // Diagnostic: log current ID token claims so we can see what the rules see.
            try {
                val tokenResult = auth.currentUser?.getIdToken(false)?.await()
                Log.d(
                    "TransaktionRepo",
                    "About to write: vereinId=${paired.vereinId}, geraetId=${paired.geraetId}, uid=$uid, claims=${tokenResult?.claims}"
                )
            } catch (e: Exception) {
                Log.w("TransaktionRepo", "Could not read ID token claims: ${e.message}")
            }

            firestore.collection("vereine")
                .document(paired.vereinId)
                .collection("transaktionen")
                .add(doc)
                .await()
            RecordTransaktionResult.Success
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(
                    "TransaktionRepo",
                    "PERMISSION_DENIED writing transaktion (vereinId=${paired.vereinId}, geraetId=${paired.geraetId}, uid=$uid): ${e.message}",
                    e
                )
                sessionRepo.unpair()
                RecordTransaktionResult.PermissionDeniedUnpaired
            } else {
                Log.w("TransaktionRepo", "Firestore write failed: ${e.code} ${e.message}", e)
                RecordTransaktionResult.Failure(e)
            }
        } catch (e: Exception) {
            Log.w("TransaktionRepo", "Firestore write failed: ${e.message}", e)
            RecordTransaktionResult.Failure(e)
        }
    }
}
