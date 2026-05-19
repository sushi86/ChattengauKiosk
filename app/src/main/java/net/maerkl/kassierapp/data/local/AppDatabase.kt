package net.maerkl.kassierapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Sale::class, Transaction::class], version = 9)
abstract class AppDatabase : RoomDatabase() {
    abstract fun saleDao(): SaleDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sales (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        articleName TEXT NOT NULL,
                        articleEmoji TEXT NOT NULL,
                        articlePrice REAL NOT NULL,
                        quantity INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS article_collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO article_collections (id, name) VALUES (1, 'Standard')")
                db.execSQL("ALTER TABLE articles ADD COLUMN collectionId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sales ADD COLUMN collectionId INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE articles ADD COLUMN stockQuantity INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        totalAmount REAL NOT NULL,
                        txCode TEXT,
                        collectionId INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE sales ADD COLUMN transactionId INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN refunded INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE sales SET paymentMethod = 'bar' WHERE paymentMethod = 'BAR'")
                db.execSQL("UPDATE sales SET paymentMethod = 'sumup' WHERE paymentMethod = 'KARTE'")
                db.execSQL("UPDATE transactions SET paymentMethod = 'bar' WHERE paymentMethod = 'BAR'")
                db.execSQL("UPDATE transactions SET paymentMethod = 'sumup' WHERE paymentMethod = 'KARTE'")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kassierapp_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
