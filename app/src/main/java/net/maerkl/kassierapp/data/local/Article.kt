package net.maerkl.kassierapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

const val MANUAL_PRICE_ARTICLE_NAME = "__MANUAL_PRICE__"

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Double,
    val emoji: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val collectionId: Long = 1
)

val Article.isManualPrice: Boolean
    get() = name == MANUAL_PRICE_ARTICLE_NAME
