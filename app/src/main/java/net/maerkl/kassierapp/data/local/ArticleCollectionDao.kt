package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleCollectionDao {
    @Query("SELECT * FROM article_collections ORDER BY id ASC")
    fun getAll(): Flow<List<ArticleCollection>>

    @Query("SELECT COUNT(*) FROM article_collections")
    suspend fun getCount(): Int

    @Insert
    suspend fun insert(collection: ArticleCollection): Long

    @Update
    suspend fun update(collection: ArticleCollection)

    @Delete
    suspend fun delete(collection: ArticleCollection)
}
