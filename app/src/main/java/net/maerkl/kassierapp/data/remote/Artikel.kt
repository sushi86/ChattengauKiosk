package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot

data class Artikel(
    val id: String,
    val name: String,
    val emoji: String?,
    val preisCent: Long,
    val taxRate: Int,
    val aktiv: Boolean,
)

object ArtikelMapper {
    fun fromDocument(doc: DocumentSnapshot): Artikel? {
        val name = doc.getString("name") ?: return null
        val preis = doc.getLong("preis") ?: return null
        return Artikel(
            id = doc.id,
            name = name,
            emoji = doc.getString("emoji"),
            preisCent = preis,
            taxRate = doc.getLong("taxRate")?.toInt() ?: 0,
            aktiv = doc.getBoolean("aktiv") ?: false,
        )
    }
}
