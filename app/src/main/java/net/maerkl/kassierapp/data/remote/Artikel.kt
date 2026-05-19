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
    private const val MAX_PREIS_CENT = 1_000_000_00L
    private val ALLOWED_TAX_RATES = setOf(0, 7, 19)

    fun fromDocument(doc: DocumentSnapshot): Artikel? {
        val name = doc.getString("name") ?: return null
        val preis = doc.getLong("preis") ?: return null
        if (preis < 0 || preis > MAX_PREIS_CENT) return null
        val taxRate = doc.getLong("taxRate")?.toInt() ?: 0
        if (taxRate !in ALLOWED_TAX_RATES) return null
        return Artikel(
            id = doc.id,
            name = name,
            emoji = doc.getString("emoji"),
            preisCent = preis,
            taxRate = taxRate,
            aktiv = doc.getBoolean("aktiv") ?: false,
        )
    }
}
