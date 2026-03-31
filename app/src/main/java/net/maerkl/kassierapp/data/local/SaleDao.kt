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
        SELECT (timestamp / 86400000) * 86400000 AS dayTimestamp,
               SUM(articlePrice * quantity) AS totalRevenue,
               SUM(quantity) AS totalItems
        FROM sales
        GROUP BY timestamp / 86400000
        ORDER BY dayTimestamp DESC
    """)
    fun getDailySummaries(): Flow<List<DailySummary>>

    @Query("""
        SELECT articleName, articleEmoji,
               SUM(CASE WHEN paymentMethod = 'BAR' THEN quantity ELSE 0 END) AS cashQuantity,
               SUM(CASE WHEN paymentMethod = 'BAR' THEN articlePrice * quantity ELSE 0.0 END) AS cashRevenue,
               SUM(CASE WHEN paymentMethod = 'KARTE' THEN quantity ELSE 0 END) AS cardQuantity,
               SUM(CASE WHEN paymentMethod = 'KARTE' THEN articlePrice * quantity ELSE 0.0 END) AS cardRevenue
        FROM sales
        WHERE timestamp >= :startOfDay AND timestamp < :endOfDay
        GROUP BY articleName, articleEmoji
        ORDER BY articleName ASC
    """)
    fun getArticleSummariesForDay(startOfDay: Long, endOfDay: Long): Flow<List<ArticleDaySummary>>
}
