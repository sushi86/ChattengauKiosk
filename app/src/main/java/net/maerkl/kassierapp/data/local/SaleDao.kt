package net.maerkl.kassierapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Insert
    suspend fun insertAll(sales: List<Sale>)

    @Query("""
        SELECT (s.timestamp / 86400000) * 86400000 AS dayTimestamp,
               SUM(CASE WHEN t.refunded = 0 OR t.id IS NULL THEN s.articlePrice * s.quantity ELSE 0.0 END) AS totalRevenue,
               SUM(CASE WHEN t.refunded = 0 OR t.id IS NULL THEN s.quantity ELSE 0 END) AS totalItems
        FROM sales s
        LEFT JOIN transactions t ON s.transactionId = t.id
        GROUP BY s.timestamp / 86400000
        ORDER BY dayTimestamp DESC
    """)
    fun getDailySummaries(): Flow<List<DailySummary>>

    @Query("""
        SELECT s.articleName, s.articleEmoji,
               SUM(CASE WHEN (t.refunded = 0 OR t.id IS NULL) AND s.paymentMethod = 'bar' THEN s.quantity ELSE 0 END) AS cashQuantity,
               SUM(CASE WHEN (t.refunded = 0 OR t.id IS NULL) AND s.paymentMethod = 'bar' THEN s.articlePrice * s.quantity ELSE 0.0 END) AS cashRevenue,
               SUM(CASE WHEN (t.refunded = 0 OR t.id IS NULL) AND s.paymentMethod = 'sumup' THEN s.quantity ELSE 0 END) AS cardQuantity,
               SUM(CASE WHEN (t.refunded = 0 OR t.id IS NULL) AND s.paymentMethod = 'sumup' THEN s.articlePrice * s.quantity ELSE 0.0 END) AS cardRevenue,
               SUM(CASE WHEN t.refunded = 1 THEN s.quantity ELSE 0 END) AS refundedQuantity,
               SUM(CASE WHEN t.refunded = 1 THEN s.articlePrice * s.quantity ELSE 0.0 END) AS refundedRevenue
        FROM sales s
        LEFT JOIN transactions t ON s.transactionId = t.id
        WHERE s.timestamp >= :startOfDay AND s.timestamp < :endOfDay
        GROUP BY s.articleName, s.articleEmoji
        ORDER BY s.articleName ASC
    """)
    fun getArticleSummariesForDay(startOfDay: Long, endOfDay: Long): Flow<List<ArticleDaySummary>>

    @Query("SELECT * FROM sales WHERE transactionId = :transactionId")
    suspend fun getSalesByTransactionId(transactionId: Long): List<Sale>
}
