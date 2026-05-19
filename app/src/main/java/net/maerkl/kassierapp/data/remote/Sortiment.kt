package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot

data class Sortiment(
    val id: String,
    val name: String,
    val articleIds: List<String>,
)

object SortimentMapper {
    fun fromDocument(doc: DocumentSnapshot): Sortiment? {
        val name = doc.getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val articleIds = doc.get("articleIds") as? List<String> ?: emptyList()
        return Sortiment(id = doc.id, name = name, articleIds = articleIds)
    }
}
