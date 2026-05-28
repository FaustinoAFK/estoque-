package com.faustinoafk.estoque.data.repository

import android.content.Context
import android.util.Log
import com.faustinoafk.estoque.data.local.StockDao
import com.faustinoafk.estoque.data.model.ActionLog
import com.faustinoafk.estoque.data.model.InAppNotification
import com.faustinoafk.estoque.data.model.SaleTransaction
import com.faustinoafk.estoque.data.model.StockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class CloudState(
    val lastUpdated: Long,
    val lastUpdatedBy: String,
    val items: List<StockItem>,
    val logs: List<ActionLog>,
    val sales: List<SaleTransaction>
)

class StockRepository(
    private val stockDao: StockDao,
    context: Context
) {
    private val sharedPrefs = context.getSharedPreferences("stocksync_prefs", Context.MODE_PRIVATE)

    // Observable Flows of Local Database State
    val allItems: Flow<List<StockItem>> = stockDao.getAllItems()
    val allLogs: Flow<List<ActionLog>> = stockDao.getAllLogs()
    val allTransactions: Flow<List<SaleTransaction>> = stockDao.getAllTransactions()
    val allNotifications: Flow<List<InAppNotification>> = stockDao.getAllNotifications()

    // Sync State Status
    private val _syncOnline = MutableStateFlow(false)
    val syncOnline: StateFlow<Boolean> = _syncOnline

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _connectedDeviceCount = MutableStateFlow(1)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private var lastFetchHadError = false

    // Prefix bucket ID for our StockSync system
    private val bucketId = "YADqopXh388yVis5xAqqLX"

    private fun getRoomUrl(roomName: String): String {
        val sanitized = roomName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "")
        val targetRoom = if (sanitized.isEmpty()) "default_room" else sanitized
        return "https://kvdb.io/$bucketId/$targetRoom"
    }

    // Get last synchronized cloud timestamp to filter out new incoming push notifications
    private var lastSyncTime: Long
        get() = sharedPrefs.getLong("last_sync_time", 0L)
        set(value) = sharedPrefs.edit().putLong("last_sync_time", value).apply()

    // --- Core Operations ---

    suspend fun insertItem(item: StockItem, user: String) = withContext(Dispatchers.IO) {
        val existing = stockDao.getItemById(item.id)
        val actionType = if (existing == null) "CREATE" else "UPDATE"
        val qtyDiff = if (existing == null) item.quantity else (item.quantity - existing.quantity)

        stockDao.insertItem(item)

        val log = ActionLog(
            itemId = item.id,
            itemName = item.name,
            user = user,
            actionType = actionType,
            quantityChanged = qtyDiff
        )
        stockDao.insertLog(log)
        
        // Also send visual notification locally immediately
        val msg = if (existing == null) {
            "Você adicionou o produto ${item.name} ao estoque"
        } else {
            "Você editou as informações do produto ${item.name}"
        }
        triggerLocalPushNotification(msg)
    }

    suspend fun updateStockQuantity(itemId: String, amount: Int, user: String): Boolean = withContext(Dispatchers.IO) {
        val item = stockDao.getItemById(itemId) ?: return@withContext false
        val newQuantity = item.quantity + amount
        if (newQuantity < 0) return@withContext false // Out of stock check

        val updatedItem = item.copy(
            quantity = newQuantity,
            lastUpdatedBy = user,
            lastUpdatedAt = System.currentTimeMillis()
        )
        stockDao.insertItem(updatedItem)

        val actionType = if (amount >= 0) "ADD_STOCK" else "REMOVE_STOCK"
        val log = ActionLog(
            itemId = itemId,
            itemName = item.name,
            user = user,
            actionType = actionType,
            quantityChanged = amount
        )
        stockDao.insertLog(log)

        val actionText = if (amount >= 0) "adicionou $amount" else "removeu ${-amount}"
        triggerLocalPushNotification("Você $actionText do estoque de ${item.name}")
        return@withContext true
    }

    suspend fun sellItem(itemId: String, quantitySold: Int, user: String): Boolean = withContext(Dispatchers.IO) {
        if (quantitySold <= 0) return@withContext false
        val item = stockDao.getItemById(itemId) ?: return@withContext false
        if (item.quantity < quantitySold) return@withContext false // Cannot sell more than stocked

        val newQuantity = item.quantity - quantitySold
        val updatedItem = item.copy(
            quantity = newQuantity,
            lastUpdatedBy = user,
            lastUpdatedAt = System.currentTimeMillis()
        )
        stockDao.insertItem(updatedItem)

        // Calculate Profit: (UnitPrice - UnitCost) * Quantity
        val unitCost = item.unitCost
        val unitPrice = item.unitPrice
        val profit = (unitPrice - unitCost) * quantitySold

        // Create transaction record
        val transaction = SaleTransaction(
            itemId = itemId,
            itemName = item.name,
            quantity = quantitySold,
            unitCost = unitCost,
            unitPrice = unitPrice,
            profit = profit,
            user = user
        )
        stockDao.insertTransaction(transaction)

        // Create Action Log
        val log = ActionLog(
            itemId = itemId,
            itemName = item.name,
            user = user,
            actionType = "SALE",
            quantityChanged = -quantitySold
        )
        stockDao.insertLog(log)

        triggerLocalPushNotification("Venda realizada: $quantitySold x ${item.name} (Lucro: R$ ${String.format("%.2f", profit)})")
        return@withContext true
    }

    suspend fun deleteItem(item: StockItem, user: String) = withContext(Dispatchers.IO) {
        stockDao.deleteItem(item)

        val log = ActionLog(
            itemId = item.id,
            itemName = item.name,
            user = user,
            actionType = "DELETE",
            quantityChanged = -item.quantity
        )
        stockDao.insertLog(log)
        triggerLocalPushNotification("Você removeu o produto ${item.name} do estoque")
    }

    suspend fun triggerLocalPushNotification(message: String) {
        val notification = InAppNotification(message = message)
        stockDao.insertNotification(notification)
    }

    suspend fun markNotificationsAllAsRead() = withContext(Dispatchers.IO) {
        stockDao.markAllAsRead()
    }

    // --- Synchronisation Logic ---

    suspend fun fetchFromCloud(roomName: String): CloudState? = withContext(Dispatchers.IO) {
        lastFetchHadError = false
        val url = getRoomUrl(roomName)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val body = response.body?.string() ?: return@use null
                    return@withContext jsonToCloudState(body)
                } else if (response.code == 404) {
                    // Room is empty, not created yet on cloud
                    return@withContext null
                } else {
                    Log.e("StockRepository", "Fetch failed with HTTP ${response.code}")
                    lastFetchHadError = true
                    _syncOnline.value = false
                }
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Fetch failed: ${e.message}")
            lastFetchHadError = true
            _syncOnline.value = false
        }
        return@withContext null
    }

    suspend fun pushToCloud(roomName: String, state: CloudState): Boolean = withContext(Dispatchers.IO) {
        val url = getRoomUrl(roomName)
        val jsonStr = cloudStateToJson(state)
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonStr.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    _syncOnline.value = true
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Push failed: ${e.message}")
            _syncOnline.value = false
        }
        return@withContext false
    }

    suspend fun runSyncCycle(username: String, roomName: String) = withContext(Dispatchers.IO) {
        if (_isSyncing.value) return@withContext
        _isSyncing.value = true

        try {
            val remoteState = fetchFromCloud(roomName)
            val localItems = stockDao.getItemsOnce()
            val localLogs = stockDao.getLogsOnce()
            val localSales = stockDao.getTransactionsOnce()

            if (remoteState == null) {
                if (lastFetchHadError) {
                    _syncOnline.value = false
                    return@withContext
                }
                // Cloud has no data. Let's upload local state if we have any data.
                if (localItems.isNotEmpty() || localLogs.isNotEmpty() || localSales.isNotEmpty()) {
                    val stateToPush = CloudState(
                        lastUpdated = System.currentTimeMillis(),
                        lastUpdatedBy = username,
                        items = localItems,
                        logs = localLogs,
                        sales = localSales
                    )
                    val pushed = pushToCloud(roomName, stateToPush)
                    if (!pushed) {
                        _syncOnline.value = false
                        return@withContext
                    }
                    lastSyncTime = System.currentTimeMillis()
                }
                _syncOnline.value = true
            } else {
                // 1. Merge Stock Items
                val remoteItemMap = remoteState.items.associateBy { it.id }
                val localItemMap = localItems.associateBy { it.id }
                
                val mergedItems = mutableListOf<StockItem>()
                val allIds = remoteItemMap.keys + localItemMap.keys
                var localItemsChanged = false

                for (id in allIds) {
                    val r = remoteItemMap[id]
                    val l = localItemMap[id]
                    if (r != null && l != null) {
                        val itemChanged = r.quantity != l.quantity ||
                                r.name != l.name ||
                                r.unitCost != l.unitCost ||
                                r.unitPrice != l.unitPrice
                        val bothChangedSinceLastSync = itemChanged &&
                                r.lastUpdatedAt > lastSyncTime &&
                                l.lastUpdatedAt > lastSyncTime

                        if (bothChangedSinceLastSync) {
                            val mergedConcurrentItem = mergeConcurrentItemMovements(
                                localItem = l,
                                remoteItem = r,
                                localLogs = localLogs,
                                remoteLogs = remoteState.logs,
                                username = username
                            )

                            if (mergedConcurrentItem != null) {
                                mergedItems.add(mergedConcurrentItem)
                                localItemsChanged = mergedConcurrentItem != l
                                stockDao.insertNotification(
                                    InAppNotification(
                                        message = "Sincronização combinada em ${mergedConcurrentItem.name}: movimentos feitos em dois aparelhos foram somados.",
                                        timestamp = maxOf(r.lastUpdatedAt, l.lastUpdatedAt)
                                    )
                                )
                                continue
                            } else {
                                stockDao.insertNotification(
                                    InAppNotification(
                                        message = "Conflito de sincronização em ${l.name}: dois aparelhos editaram o mesmo produto. A alteração mais recente foi mantida.",
                                        timestamp = maxOf(r.lastUpdatedAt, l.lastUpdatedAt)
                                    )
                                )
                            }
                        }

                        if (r.lastUpdatedAt >= l.lastUpdatedAt) {
                            mergedItems.add(r)
                            if (itemChanged) {
                                localItemsChanged = true
                            }
                        } else {
                            mergedItems.add(l)
                        }
                    } else if (r != null) {
                        // Present on remote, absent locally. Was it deleted locally, or just missing?
                        val wasDeletedLocally = localLogs.any { it.itemId == id && it.actionType == "DELETE" && it.timestamp >= r.lastUpdatedAt }
                        if (!wasDeletedLocally) {
                            mergedItems.add(r)
                            localItemsChanged = true
                        }
                    } else if (l != null) {
                        // Present locally, absent on remote. Was it deleted on remote, or newly created locally?
                        val wasDeletedRemotely = remoteState.logs.any { it.itemId == id && it.actionType == "DELETE" && it.timestamp >= l.lastUpdatedAt }
                        if (!wasDeletedRemotely && l.lastUpdatedAt > remoteState.lastUpdated) {
                            mergedItems.add(l)
                        } else {
                            // Deleted on remote! Remove locally too
                            localItemsChanged = true
                        }
                    }
                }

                // 2. Merge Logs
                val remoteLogIds = remoteState.logs.map { it.id }.toSet()
                val localLogIds = localLogs.map { it.id }.toSet()
                val mergedLogs = (remoteState.logs + localLogs).distinctBy { it.id }
                val localLogsChanged = mergedLogs.size != localLogs.size

                // 3. Merge Sales
                val mergedSales = (remoteState.sales + localSales).distinctBy { it.id }
                val localSalesChanged = mergedSales.size != localSales.size

                val databaseUpdated = localItemsChanged || localLogsChanged || localSalesChanged

                // Produce simulation push notifications for any NEW logs downloaded from *other users*
                if (databaseUpdated) {
                    val baseSyncTime = lastSyncTime
                    val incomingNewLogs = remoteState.logs.filter { 
                        it.user != username && it.timestamp > baseSyncTime && !localLogIds.contains(it.id)
                    }.sortedBy { it.timestamp }

                    for (newLog in incomingNewLogs) {
                        val actionDesc = when (newLog.actionType) {
                            "CREATE" -> "criou o item ${newLog.itemName}"
                            "UPDATE" -> "atualizou dados de ${newLog.itemName}"
                            "ADD_STOCK" -> "adicionou ${newLog.quantityChanged} unidades de ${newLog.itemName}"
                            "REMOVE_STOCK" -> "removeu ${-newLog.quantityChanged} unidades de ${newLog.itemName}"
                            "SALE" -> "vendeu ${-newLog.quantityChanged} unidades de ${newLog.itemName}"
                            "DELETE" -> "removeu o item ${newLog.itemName} do sistema"
                            else -> "alterou estoque de ${newLog.itemName}"
                        }
                        val systemAlert = "${newLog.user} $actionDesc no estoque"
                        
                        // Push alert to SQLite notifications table
                        stockDao.insertNotification(
                            InAppNotification(
                                message = systemAlert,
                                timestamp = newLog.timestamp
                            )
                        )
                    }

                    // Write back merged states locally
                    stockDao.clearAllItems()
                    stockDao.insertItems(mergedItems)

                    stockDao.clearAllLogs()
                    stockDao.insertLogs(mergedLogs)

                    stockDao.clearAllTransactions()
                    stockDao.insertTransactions(mergedSales)
                }

                _connectedDeviceCount.value = 1

                // Check if we have any newer local changes that are not on the remote
                val hasNewerLocalChanges = localItems.any { it.lastUpdatedAt > remoteState.lastUpdated } ||
                        localLogs.any { it.timestamp > remoteState.lastUpdated } ||
                        localSales.any { it.timestamp > remoteState.lastUpdated }

                if (hasNewerLocalChanges) {
                    val stateToPush = CloudState(
                        lastUpdated = System.currentTimeMillis(),
                        lastUpdatedBy = username,
                        items = mergedItems,
                        logs = mergedLogs,
                        sales = mergedSales
                    )
                    val pushed = pushToCloud(roomName, stateToPush)
                    if (!pushed) {
                        _syncOnline.value = false
                        return@withContext
                    }
                }

                lastSyncTime = System.currentTimeMillis()
                _syncOnline.value = true
            }
        } catch (e: Exception) {
            Log.e("StockRepository", "Sync failed: ${e.message}")
            _syncOnline.value = false
        } finally {
            _isSyncing.value = false
        }
    }

    private fun mergeConcurrentItemMovements(
        localItem: StockItem,
        remoteItem: StockItem,
        localLogs: List<ActionLog>,
        remoteLogs: List<ActionLog>,
        username: String
    ): StockItem? {
        val localMovements = movementLogsAfterLastSync(localLogs, localItem.id)
        val remoteMovements = movementLogsAfterLastSync(remoteLogs, localItem.id)
        val mergedMovements = (localMovements + remoteMovements).distinctBy { it.id }

        if (mergedMovements.size < 2) return null

        val localDelta = localMovements.sumOf { it.quantityChanged }
        val remoteDelta = remoteMovements.sumOf { it.quantityChanged }
        val mergedDelta = mergedMovements.sumOf { it.quantityChanged }

        val baseFromLocal = localItem.quantity - localDelta
        val baseFromRemote = remoteItem.quantity - remoteDelta
        if (baseFromLocal != baseFromRemote) return null

        val latestItem = if (remoteItem.lastUpdatedAt >= localItem.lastUpdatedAt) remoteItem else localItem
        val mergedQuantity = (baseFromLocal + mergedDelta).coerceAtLeast(0)

        return latestItem.copy(
            quantity = mergedQuantity,
            lastUpdatedBy = username,
            lastUpdatedAt = maxOf(localItem.lastUpdatedAt, remoteItem.lastUpdatedAt, System.currentTimeMillis())
        )
    }

    private fun movementLogsAfterLastSync(logs: List<ActionLog>, itemId: String): List<ActionLog> {
        return logs.filter {
            it.itemId == itemId &&
                    it.timestamp > lastSyncTime &&
                    it.actionType in setOf("ADD_STOCK", "REMOVE_STOCK", "SALE", "UPDATE")
        }
    }

    // --- JSON Conversion Utilities (Fast, Bulletproof, Zero Reflection) ---

    private fun cloudStateToJson(state: CloudState): String {
        val root = JSONObject()
        root.put("lastUpdated", state.lastUpdated)
        root.put("lastUpdatedBy", state.lastUpdatedBy)

        val itemsArr = JSONArray()
        for (item in state.items) {
            val i = JSONObject()
            i.put("id", item.id)
            i.put("name", item.name)
            i.put("quantity", item.quantity)
            i.put("unitCost", item.unitCost)
            i.put("unitPrice", item.unitPrice)
            i.put("lastUpdatedBy", item.lastUpdatedBy)
            i.put("lastUpdatedAt", item.lastUpdatedAt)
            itemsArr.put(i)
        }
        root.put("items", itemsArr)

        val logsArr = JSONArray()
        for (log in state.logs) {
            val l = JSONObject()
            l.put("id", log.id)
            l.put("itemId", log.itemId)
            l.put("itemName", log.itemName)
            l.put("user", log.user)
            l.put("actionType", log.actionType)
            l.put("quantityChanged", log.quantityChanged)
            l.put("timestamp", log.timestamp)
            logsArr.put(l)
        }
        root.put("logs", logsArr)

        val salesArr = JSONArray()
        for (sale in state.sales) {
            val s = JSONObject()
            s.put("id", sale.id)
            s.put("itemId", sale.itemId)
            s.put("itemName", sale.itemName)
            s.put("quantity", sale.quantity)
            s.put("unitCost", sale.unitCost)
            s.put("unitPrice", sale.unitPrice)
            s.put("profit", sale.profit)
            s.put("timestamp", sale.timestamp)
            s.put("user", sale.user)
            salesArr.put(s)
        }
        root.put("sales", salesArr)

        return root.toString()
    }

    private fun jsonToCloudState(jsonStr: String): CloudState {
        val root = JSONObject(jsonStr)
        val lastUpdated = root.optLong("lastUpdated", 0L)
        val lastUpdatedBy = root.optString("lastUpdatedBy", "System")

        val itemsList = mutableListOf<StockItem>()
        val itemsArr = root.optJSONArray("items")
        if (itemsArr != null) {
            for (idx in 0 until itemsArr.length()) {
                val i = itemsArr.getJSONObject(idx)
                itemsList.add(
                    StockItem(
                        id = i.optString("id", UUID.randomUUID().toString()),
                        name = i.optString("name", "Sem nome"),
                        quantity = i.optInt("quantity", 0),
                        unitCost = i.optDouble("unitCost", 0.0),
                        unitPrice = i.optDouble("unitPrice", 0.0),
                        lastUpdatedBy = i.optString("lastUpdatedBy", "System"),
                        lastUpdatedAt = i.optLong("lastUpdatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        val logsList = mutableListOf<ActionLog>()
        val logsArr = root.optJSONArray("logs")
        if (logsArr != null) {
            for (idx in 0 until logsArr.length()) {
                val l = logsArr.getJSONObject(idx)
                logsList.add(
                    ActionLog(
                        id = l.optString("id", UUID.randomUUID().toString()),
                        itemId = l.optString("itemId", ""),
                        itemName = l.optString("itemName", ""),
                        user = l.optString("user", "System"),
                        actionType = l.optString("actionType", "ADD_STOCK"),
                        quantityChanged = l.optInt("quantityChanged", 0),
                        timestamp = l.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        val salesList = mutableListOf<SaleTransaction>()
        val salesArr = root.optJSONArray("sales")
        if (salesArr != null) {
            for (idx in 0 until salesArr.length()) {
                val s = salesArr.getJSONObject(idx)
                salesList.add(
                    SaleTransaction(
                        id = s.optString("id", UUID.randomUUID().toString()),
                        itemId = s.optString("itemId", ""),
                        itemName = s.optString("itemName", ""),
                        quantity = s.optInt("quantity", 0),
                        unitCost = s.optDouble("unitCost", 0.0),
                        unitPrice = s.optDouble("unitPrice", 0.0),
                        profit = s.optDouble("profit", 0.0),
                        timestamp = s.optLong("timestamp", System.currentTimeMillis()),
                        user = s.optString("user", "System")
                    )
                )
            }
        }

        return CloudState(lastUpdated, lastUpdatedBy, itemsList, logsList, salesList)
    }
}
