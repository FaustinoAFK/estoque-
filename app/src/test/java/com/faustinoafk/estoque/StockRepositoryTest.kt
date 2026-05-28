package com.faustinoafk.estoque

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.faustinoafk.estoque.data.local.StockDatabase
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

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
}
