package com.faustinoafk.estoque

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.faustinoafk.estoque.data.local.StockDatabase
import com.faustinoafk.estoque.data.model.ActionLog
import com.faustinoafk.estoque.data.model.StockItem
import com.faustinoafk.estoque.data.repository.StockRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StockRepositoryTest {
    private lateinit var database: StockDatabase
    private lateinit var repository: StockRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("stocksync_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        database = Room.inMemoryDatabaseBuilder(context, StockDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StockRepository(database.stockDao(), context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun sellItem_reducesStockAndRecordsProfit() = runTest {
        val item = StockItem(
            id = "produto-1",
            name = "Produto Teste",
            quantity = 10,
            unitCost = 5.0,
            unitPrice = 8.0,
            lastUpdatedBy = "Ana"
        )
        database.stockDao().insertItem(item)

        val sold = repository.sellItem(item.id, quantitySold = 3, user = "Ana")

        val updated = database.stockDao().getItemById(item.id)
        val transactions = database.stockDao().getTransactionsOnce()
        val logs = database.stockDao().getLogsOnce()

        assertTrue(sold)
        assertEquals(7, updated?.quantity)
        assertEquals(1, transactions.size)
        assertEquals(9.0, transactions.single().profit, 0.0)
        assertEquals("SALE", logs.single().actionType)
        assertEquals(-3, logs.single().quantityChanged)
    }

    @Test
    fun sellItem_rejectsQuantityGreaterThanStock() = runTest {
        val item = StockItem(
            id = "produto-2",
            name = "Produto Teste",
            quantity = 2,
            unitCost = 5.0,
            unitPrice = 8.0,
            lastUpdatedBy = "Ana"
        )
        database.stockDao().insertItem(item)

        val sold = repository.sellItem(item.id, quantitySold = 3, user = "Ana")

        assertFalse(sold)
        assertEquals(2, database.stockDao().getItemById(item.id)?.quantity)
        assertTrue(database.stockDao().getTransactionsOnce().isEmpty())
        assertTrue(database.stockDao().getLogsOnce().isEmpty())
    }

    @Test
    fun updateStockQuantity_rejectsNegativeFinalStock() = runTest {
        val item = StockItem(
            id = "produto-3",
            name = "Produto Teste",
            quantity = 1,
            unitCost = 5.0,
            unitPrice = 8.0,
            lastUpdatedBy = "Ana"
        )
        database.stockDao().insertItem(item)

        val updated = repository.updateStockQuantity(item.id, amount = -2, user = "Ana")

        assertFalse(updated)
        assertEquals(1, database.stockDao().getItemById(item.id)?.quantity)
        assertTrue(database.stockDao().getLogsOnce().isEmpty())
    }

    @Test
    fun concurrentItemMovements_areSummedFromCommonBase() {
        context.getSharedPreferences("stocksync_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_sync_time", 1_000L)
            .apply()

        val localItem = StockItem(
            id = "produto-4",
            name = "Produto Teste",
            quantity = 12,
            unitCost = 5.0,
            unitPrice = 8.0,
            lastUpdatedBy = "Celular A",
            lastUpdatedAt = 2_000L
        )
        val remoteItem = localItem.copy(
            quantity = 13,
            lastUpdatedBy = "Celular B",
            lastUpdatedAt = 3_000L
        )

        val localLogs = listOf(
            ActionLog(
                id = "log-local",
                itemId = localItem.id,
                itemName = localItem.name,
                user = "Celular A",
                actionType = "ADD_STOCK",
                quantityChanged = 2,
                timestamp = 2_000L
            )
        )
        val remoteLogs = listOf(
            ActionLog(
                id = "log-remoto",
                itemId = localItem.id,
                itemName = localItem.name,
                user = "Celular B",
                actionType = "ADD_STOCK",
                quantityChanged = 3,
                timestamp = 3_000L
            )
        )

        val merged = invokeConcurrentMerge(
            localItem = localItem,
            remoteItem = remoteItem,
            localLogs = localLogs,
            remoteLogs = remoteLogs
        )

        assertEquals(15, merged?.quantity)
        assertEquals("Celular B", merged?.lastUpdatedBy)
    }

    private fun invokeConcurrentMerge(
        localItem: StockItem,
        remoteItem: StockItem,
        localLogs: List<ActionLog>,
        remoteLogs: List<ActionLog>
    ): StockItem? {
        val method = StockRepository::class.java.getDeclaredMethod(
            "mergeConcurrentItemMovements",
            StockItem::class.java,
            StockItem::class.java,
            List::class.java,
            List::class.java,
            String::class.java
        )
        method.isAccessible = true

        return method.invoke(
            repository,
            localItem,
            remoteItem,
            localLogs,
            remoteLogs,
            "Celular B"
        ) as StockItem?
    }
}
