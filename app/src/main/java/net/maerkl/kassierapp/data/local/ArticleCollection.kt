package net.maerkl.kassierapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "article_collections")
data class ArticleCollection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
