package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction as RoomTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @RoomTransaction
    @Query("""
        SELECT * FROM transactions
        WHERE timestamp >= :startOfDay
        ORDER BY timestamp DESC
    """)
    fun getTodayTransactionsWithSales(startOfDay: Long): Flow<List<TransactionWithSales>>

    @Query("UPDATE transactions SET refunded = 1 WHERE id = :id")
    suspend fun markRefunded(id: Long)
}
