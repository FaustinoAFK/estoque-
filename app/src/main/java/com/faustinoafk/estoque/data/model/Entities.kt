package com.faustinoafk.estoque.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "stock_items")
data class StockItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int,
    val unitCost: Double,
    val unitPrice: Double,
    val lastUpdatedBy: String,
    val lastUpdatedAt: Long = System.currentTimeMillis()
) {
    val totalCost: Double get() = quantity * unitCost
}

@Entity(tableName = "action_logs")
data class ActionLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val user: String,
    val actionType: String, // "CREATE", "UPDATE", "ADD_STOCK", "REMOVE_STOCK", "SALE"
    val quantityChanged: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sale_transactions")
data class SaleTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val unitCost: Double,
    val unitPrice: Double,
    val profit: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val user: String
)

@Entity(tableName = "in_app_notifications")
data class InAppNotification(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
