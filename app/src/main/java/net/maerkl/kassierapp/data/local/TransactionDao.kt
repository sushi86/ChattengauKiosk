package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Query("""
        SELECT * FROM transactions
        WHERE collectionId = :collectionId AND timestamp >= :startOfDay
        ORDER BY timestamp DESC
    """)
    fun getTodayTransactions(collectionId: Long, startOfDay: Long): Flow<List<Transaction>>
}
