package net.maerkl.kassierapp.data.remote

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SortimentRepository(
    private val firestore: FirebaseFirestore,
) {
    fun observe(vereinId: String): Flow<CatalogState<Sortiment>> = callbackFlow {
        trySend(CatalogState.Loading)
        val reg = firestore.collection("vereine").document(vereinId)
            .collection("sortimente")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w("SortimentRepo", "PERMISSION_DENIED on sortimente listener: ${err.message}")
                        trySend(CatalogState.PermissionDenied)
                    } else {
                        Log.w("SortimentRepo", "Snapshot error: ${err.code} ${err.message}", err)
                    }
                    return@addSnapshotListener
                }
                val items = snap?.documents.orEmpty()
                    .mapNotNull { SortimentMapper.fromDocument(it) }
                    .sortedBy { it.name }
                trySend(CatalogState.Data(items))
            }
        awaitClose { reg.remove() }
    }
}
