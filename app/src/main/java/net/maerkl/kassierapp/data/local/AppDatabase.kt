package net.maerkl.kassierapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [Article::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kassierapp_db"
                )
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
