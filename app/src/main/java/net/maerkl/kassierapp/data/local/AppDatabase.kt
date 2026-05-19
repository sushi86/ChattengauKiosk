package net.maerkl.kassierapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Article::class, Sale::class, ArticleCollection::class, Transaction::class], version = 8)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun saleDao(): SaleDao
    abstract fun articleCollectionDao(): ArticleCollectionDao
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
                    .addCallback(PrepopulateCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun insertDefaultArticles(dao: ArticleDao, collectionId: Long = 1) {
            dao.insert(Article(name = "Hütt Luxus Pils", price = 2.00, emoji = "\uD83C\uDF7A", sortOrder = 0, collectionId = collectionId))
            dao.insert(Article(name = "Martini Edelpils", price = 2.00, emoji = "\uD83C\uDF7A", sortOrder = 1, collectionId = collectionId))
            dao.insert(Article(name = "Hütt Naturtrüb Radler", price = 2.00, emoji = "\uD83C\uDF7B", sortOrder = 2, collectionId = collectionId))
            dao.insert(Article(name = "Hütt Hefeweizen", price = 3.00, emoji = "\uD83C\uDF7A", sortOrder = 3, collectionId = collectionId))
            dao.insert(Article(name = "Hütt Hefeweizen alkoholfrei", price = 3.00, emoji = "\uD83C\uDF7A", sortOrder = 4, collectionId = collectionId))
            dao.insert(Article(name = "Bier (Kiste)", price = 35.00, emoji = "\uD83D\uDCE6", sortOrder = 5, collectionId = collectionId))
            dao.insert(Article(name = "Coca Cola", price = 2.00, emoji = "\uD83E\uDD64", sortOrder = 6, collectionId = collectionId))
            dao.insert(Article(name = "Fanta", price = 2.00, emoji = "\uD83C\uDF4A", sortOrder = 7, collectionId = collectionId))
            dao.insert(Article(name = "Sprite", price = 2.00, emoji = "\uD83E\uDDCB", sortOrder = 8, collectionId = collectionId))
            dao.insert(Article(name = "Mineralwasser", price = 1.50, emoji = "\uD83D\uDCA7", sortOrder = 9, collectionId = collectionId))
            dao.insert(Article(name = "Kaffee", price = 1.50, emoji = "\u2615", sortOrder = 10, collectionId = collectionId))
            dao.insert(Article(name = "Kuchen", price = 1.50, emoji = "\uD83C\uDF70", sortOrder = 11, collectionId = collectionId))
            dao.insert(Article(name = "Bratwurst mit Brötchen", price = 3.50, emoji = "\uD83C\uDF2D", sortOrder = 12, collectionId = collectionId))
            dao.insert(Article(name = MANUAL_PRICE_ARTICLE_NAME, price = 0.0, emoji = "\u270F\uFE0F", sortOrder = 13, collectionId = collectionId))
        }

        suspend fun ensureManualPriceArticles(articleDao: ArticleDao, collectionDao: ArticleCollectionDao) {
            val collections = collectionDao.getAllOnce()
            for (collection in collections) {
                val count = articleDao.countByNameAndCollection(MANUAL_PRICE_ARTICLE_NAME, collection.id)
                if (count == 0) {
                    val maxSort = articleDao.getMaxSortOrder(collection.id)
                    articleDao.insert(
                        Article(
                            name = MANUAL_PRICE_ARTICLE_NAME,
                            price = 0.0,
                            emoji = "\u270F\uFE0F",
                            sortOrder = maxSort + 1,
                            collectionId = collection.id
                        )
                    )
                }
            }
        }
    }

    private class PrepopulateCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val id = database.articleCollectionDao().insert(ArticleCollection(name = "Standard"))
                    insertDefaultArticles(database.articleDao(), collectionId = id)
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    ensureManualPriceArticles(database.articleDao(), database.articleCollectionDao())
                }
            }
        }
    }
}
