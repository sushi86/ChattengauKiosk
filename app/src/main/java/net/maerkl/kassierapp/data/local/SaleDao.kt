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
               SUM(CASE WHEN t.refunded = 0 THEN s.articlePrice * s.quantity ELSE 0.0 END) AS totalRevenue,
               SUM(CASE WHEN t.refunded = 0 THEN s.quantity ELSE 0 END) AS totalItems
        FROM sales s
        LEFT JOIN transactions t ON s.transactionId = t.id
        WHERE s.collectionId = :collectionId
        GROUP BY s.timestamp / 86400000
        ORDER BY dayTimestamp DESC
    """)
    fun getDailySummaries(collectionId: Long): Flow<List<DailySummary>>

    @Query("""
        SELECT s.articleName, s.articleEmoji,
               SUM(CASE WHEN t.refunded = 0 AND s.paymentMethod = 'BAR' THEN s.quantity ELSE 0 END) AS cashQuantity,
               SUM(CASE WHEN t.refunded = 0 AND s.paymentMethod = 'BAR' THEN s.articlePrice * s.quantity ELSE 0.0 END) AS cashRevenue,
               SUM(CASE WHEN t.refunded = 0 AND s.paymentMethod = 'KARTE' THEN s.quantity ELSE 0 END) AS cardQuantity,
               SUM(CASE WHEN t.refunded = 0 AND s.paymentMethod = 'KARTE' THEN s.articlePrice * s.quantity ELSE 0.0 END) AS cardRevenue,
               SUM(CASE WHEN t.refunded = 1 THEN s.quantity ELSE 0 END) AS refundedQuantity,
               SUM(CASE WHEN t.refunded = 1 THEN s.articlePrice * s.quantity ELSE 0.0 END) AS refundedRevenue
        FROM sales s
        LEFT JOIN transactions t ON s.transactionId = t.id
        WHERE s.collectionId = :collectionId AND s.timestamp >= :startOfDay AND s.timestamp < :endOfDay
        GROUP BY s.articleName, s.articleEmoji
        ORDER BY s.articleName ASC
    """)
    fun getArticleSummariesForDay(collectionId: Long, startOfDay: Long, endOfDay: Long): Flow<List<ArticleDaySummary>>

    @Query("DELETE FROM sales WHERE collectionId = :collectionId")
    suspend fun deleteAllByCollection(collectionId: Long)

    @Query("""
        SELECT s.articleName, SUM(s.quantity) AS totalSold
        FROM sales s
        LEFT JOIN transactions t ON s.transactionId = t.id
        WHERE s.collectionId = :collectionId AND s.timestamp >= :startOfDay AND (t.refunded = 0 OR t.id IS NULL)
        GROUP BY s.articleName
    """)
    fun getSoldQuantitiesToday(collectionId: Long, startOfDay: Long): Flow<List<SoldQuantity>>

    @Query("SELECT * FROM sales WHERE transactionId = :transactionId")
    suspend fun getSalesByTransactionId(transactionId: Long): List<Sale>
}
