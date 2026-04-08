package net.maerkl.kassierapp.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val paymentMethod: String,
    val totalAmount: Double,
    val txCode: String? = null,
    val collectionId: Long = 1
)

data class TransactionWithSales(
    @Embedded val transaction: Transaction,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val sales: List<Sale>
)
