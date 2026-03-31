package net.maerkl.kassierapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sales")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val articleName: String,
    val articleEmoji: String,
    val articlePrice: Double,
    val quantity: Int,
    val paymentMethod: String,
    val timestamp: Long
)

data class DailySummary(
    val dayTimestamp: Long,
    val totalRevenue: Double,
    val totalItems: Int
)

data class ArticleDaySummary(
    val articleName: String,
    val articleEmoji: String,
    val cashQuantity: Int,
    val cashRevenue: Double,
    val cardQuantity: Int,
    val cardRevenue: Double
)
