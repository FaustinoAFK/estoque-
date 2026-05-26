package com.faustinoafk.estoque.data.local

import androidx.room.*
import com.faustinoafk.estoque.data.model.ActionLog
import com.faustinoafk.estoque.data.model.InAppNotification
import com.faustinoafk.estoque.data.model.SaleTransaction
import com.faustinoafk.estoque.data.model.StockItem
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM stock_items ORDER BY name ASC")
    fun getAllItems(): Flow<List<StockItem>>

    @Query("SELECT * FROM stock_items")
    suspend fun getItemsOnce(): List<StockItem>

    @Query("SELECT * FROM stock_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): StockItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: StockItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<StockItem>)

    @Delete
    suspend fun deleteItem(item: StockItem)

    @Query("DELETE FROM stock_items")
    suspend fun clearAllItems()

    // Logs
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<ActionLog>>

    @Query("SELECT * FROM action_logs")
    suspend fun getLogsOnce(): List<ActionLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ActionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ActionLog>)

    @Query("DELETE FROM action_logs")
    suspend fun clearAllLogs()

    // Transactions
    @Query("SELECT * FROM sale_transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM sale_transactions")
    suspend fun getTransactionsOnce(): List<SaleTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: SaleTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<SaleTransaction>)

    @Query("DELETE FROM sale_transactions")
    suspend fun clearAllTransactions()

    // Notifications
    @Query("SELECT * FROM in_app_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<InAppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: InAppNotification)

    @Query("UPDATE in_app_notifications SET isRead = 1")
    suspend fun markAllAsRead()
}
