package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE isActive = 1 ORDER BY sortOrder ASC")
    fun getActiveArticles(): Flow<List<Article>>

    @Query("SELECT * FROM articles ORDER BY sortOrder ASC")
    fun getAllArticles(): Flow<List<Article>>

    @Insert
    suspend fun insert(article: Article)

    @Update
    suspend fun update(article: Article)

    @Delete
    suspend fun delete(article: Article)

    @Query("UPDATE articles SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)
}
