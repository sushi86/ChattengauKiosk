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
        val articleIds = (doc.get("articleIds") as? List<*>).orEmpty()
            .filterIsInstance<String>()
            .distinct()
        return Sortiment(id = doc.id, name = name, articleIds = articleIds)
    }
}
