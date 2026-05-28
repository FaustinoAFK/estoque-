package com.faustinoafk.estoque.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.faustinoafk.estoque.data.model.ActionLog
import com.faustinoafk.estoque.data.model.InAppNotification
import com.faustinoafk.estoque.data.model.SaleTransaction
import com.faustinoafk.estoque.data.model.StockItem
import com.faustinoafk.estoque.data.repository.StockRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StockViewModel(
    private val repository: StockRepository,
    private val defaultPrefs: android.content.SharedPreferences
) : ViewModel() {

    // Theme active tab: 0=Painel, 1=Estoque, 2=Vendas, 3=Relatórios
    private val _activeTab = MutableStateFlow(1)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    // Configuration / User details (persisted in shared preferences)
    private val _username = MutableStateFlow(defaultPrefs.getString("user_name", "JD") ?: "JD")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _roomName = MutableStateFlow(defaultPrefs.getString("room_name", "Estoque-Geral") ?: "Estoque-Geral")
    val roomName: StateFlow<String> = _roomName.asStateFlow()

    // Sync Statuses
    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val syncOnline: StateFlow<Boolean> = repository.syncOnline
    val connectedDevices: StateFlow<Int> = repository.connectedDeviceCount

    // Reactive Data Sources
    val stocks: StateFlow<List<StockItem>> = repository.allItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val logs: StateFlow<List<ActionLog>> = repository.allLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val sales: StateFlow<List<SaleTransaction>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notifications: StateFlow<List<InAppNotification>> = repository.allNotifications.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var syncJob: Job? = null

    init {
        startSyncLoop()
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            // Give an initial tiny delay to start smoothly
            delay(500)
            while (isActive) {
                val currentRoom = _roomName.value
                val currentUser = _username.value
                if (currentRoom.isNotBlank() && currentUser.isNotBlank()) {
                    repository.runSyncCycle(currentUser, currentRoom)
                }
                // Polling interval of 4 seconds
                delay(4000)
            }
        }
    }

    // --- Action Handlers ---

    fun changeTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun updateProfile(newUsername: String, newRoom: String) {
        val userNameSanitized = newUsername.trim().ifEmpty { "JD" }
        val roomNameSanitized = newRoom.trim().ifEmpty { "Estoque-Geral" }

        _username.value = userNameSanitized
        _roomName.value = roomNameSanitized

        defaultPrefs.edit()
            .putString("user_name", userNameSanitized)
            .putString("room_name", roomNameSanitized)
            .apply()

        // Sync instantly on room/username switch
        forceImmediateSync()
    }

    fun forceImmediateSync() {
        viewModelScope.launch {
            val currentRoom = _roomName.value
            val currentUser = _username.value
            repository.runSyncCycle(currentUser, currentRoom)
        }
    }

    fun addStockItem(name: String, quantity: Int, unitCost: Double, unitPrice: Double) {
        viewModelScope.launch {
            val item = StockItem(
                name = name,
                quantity = quantity,
                unitCost = unitCost,
                unitPrice = unitPrice,
                lastUpdatedBy = _username.value
            )
            repository.insertItem(item, _username.value)
            // Trigger quick sync push
            forceImmediateSync()
        }
    }

    fun updateItem(id: String, name: String, quantity: Int, unitCost: Double, unitPrice: Double) {
        viewModelScope.launch {
            val item = StockItem(
                id = id,
                name = name,
                quantity = quantity,
                unitCost = unitCost,
                unitPrice = unitPrice,
                lastUpdatedBy = _username.value
            )
            repository.insertItem(item, _username.value)
            forceImmediateSync()
        }
    }

    fun adjustStockQuantity(itemId: String, amount: Int) {
        viewModelScope.launch {
            val success = repository.updateStockQuantity(itemId, amount, _username.value)
            if (success) {
                forceImmediateSync()
            }
        }
    }

    fun sellStockItem(itemId: String, quantity: Int) {
        viewModelScope.launch {
            val success = repository.sellItem(itemId, quantity, _username.value)
            if (success) {
                forceImmediateSync()
            }
        }
    }

    fun deleteStockItem(item: StockItem) {
        viewModelScope.launch {
            repository.deleteItem(item, _username.value)
            forceImmediateSync()
        }
    }

    fun markAllNotificationsAsRead() {
        viewModelScope.launch {
            repository.markNotificationsAllAsRead()
        }
    }

    fun createDemoNotification(message: String) {
        viewModelScope.launch {
            repository.triggerLocalPushNotification(message)
        }
    }
}
