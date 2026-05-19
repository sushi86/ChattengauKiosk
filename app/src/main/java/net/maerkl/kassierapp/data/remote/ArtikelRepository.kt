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
