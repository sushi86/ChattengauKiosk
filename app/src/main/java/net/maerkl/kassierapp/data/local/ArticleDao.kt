package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles WHERE isActive = 1 AND collectionId = :collectionId ORDER BY sortOrder ASC")
    fun getActiveArticles(collectionId: Long): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    fun getAllArticles(collectionId: Long): Flow<List<Article>>

    @Insert
    suspend fun insert(article: Article)

    @Update
    suspend fun update(article: Article)

    @Delete
    suspend fun delete(article: Article)

    @Query("UPDATE articles SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM articles WHERE collectionId = :collectionId")
    suspend fun deleteAllByCollection(collectionId: Long)

    @Query("DELETE FROM articles")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM articles WHERE name = :name AND collectionId = :collectionId")
    suspend fun countByNameAndCollection(name: String, collectionId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM articles WHERE collectionId = :collectionId")
    suspend fun getMaxSortOrder(collectionId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM articles WHERE collectionId = :collectionId AND name != :excludeName")
    suspend fun getMaxSortOrderExcluding(excludeName: String, collectionId: Long): Int

    @Query("UPDATE articles SET sortOrder = :sortOrder WHERE name = :name AND collectionId = :collectionId")
    suspend fun updateManualPriceSortOrder(name: String, collectionId: Long, sortOrder: Int)
}
