package com.faustinoafk.estoque.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.faustinoafk.estoque.data.model.ActionLog
import com.faustinoafk.estoque.data.model.InAppNotification
import com.faustinoafk.estoque.data.model.SaleTransaction
import com.faustinoafk.estoque.data.model.StockItem

@Database(
    entities = [
        StockItem::class,
        ActionLog::class,
        SaleTransaction::class,
        InAppNotification::class
    ],
    version = 1,
    exportSchema = false
)
abstract class StockDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao

    companion object {
        @Volatile
        private var INSTANCE: StockDatabase? = null

        fun getDatabase(context: Context): StockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StockDatabase::class.java,
                    "stocksync_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
