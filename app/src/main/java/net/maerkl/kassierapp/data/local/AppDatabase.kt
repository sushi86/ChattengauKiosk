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

@Database(entities = [Article::class, Sale::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    abstract fun saleDao(): SaleDao

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

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kassierapp_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addCallback(PrepopulateCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class PrepopulateCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.articleDao()
                    dao.insert(Article(name = "Bratwurst", price = 2.50, emoji = "\uD83C\uDF2D", sortOrder = 0))
                    dao.insert(Article(name = "Bier", price = 2.00, emoji = "\uD83C\uDF7A", sortOrder = 1))
                    dao.insert(Article(name = "Cola", price = 1.50, emoji = "\uD83E\uDD64", sortOrder = 2))
                    dao.insert(Article(name = "Wasser", price = 1.00, emoji = "\uD83D\uDCA7", sortOrder = 3))
                    dao.insert(Article(name = "Kaffee", price = 1.50, emoji = "\u2615", sortOrder = 4))
                    dao.insert(Article(name = "Eintritt", price = 3.00, emoji = "\uD83C\uDF9F\uFE0F", sortOrder = 5))
                }
            }
        }
    }
}
