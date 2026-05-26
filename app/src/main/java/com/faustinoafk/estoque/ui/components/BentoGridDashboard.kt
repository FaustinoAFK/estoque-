package com.faustinoafk.estoque.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.composed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.faustinoafk.estoque.data.model.ActionLog
import com.faustinoafk.estoque.data.model.InAppNotification
import com.faustinoafk.estoque.data.model.SaleTransaction
import com.faustinoafk.estoque.data.model.StockItem
import com.faustinoafk.estoque.ui.viewmodel.StockViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

// --- Aesthetic Branding Color Tokens ---
val BentoBackground = Color(0xFFFEF7FF)
val BentoTextPrimary = Color(0xFF1D1B20)
val BentoTextSecondary = Color(0xFF49454F)

// Row 1 High Contrast Card Colors
val BentoPurpleContainer = Color(0xFFD0BCFF)
val BentoPurpleText = Color(0xFF21005D)
val BentoPurpleLight = Color(0xFFEADDFF)

// Row 2 & Standard Container Colors
val BentoGreyContainer = Color(0xFFF3EDF7)
val BentoGreyBorder = Color(0xFFCAC4D0)
val BentoAccentPink = Color(0xFFFFD8E4)
val BentoAccentPinkText = Color(0xFF8C1D18)

@Composable
fun AdaptiveBentoLayout(viewModel: StockViewModel) {
    val activeTab by viewModel.activeTab.collectAsState()
    val username by viewModel.username.collectAsState()
    val roomName by viewModel.roomName.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncOnline by viewModel.syncOnline.collectAsState()
    val connectedDevices by viewModel.connectedDevices.collectAsState()
    val notifications by viewModel.notifications.collectAsState()

    var showConfigDialog by varOf(false)
    var showAddDialog by varOf(false)

    // Handle Sliding Push Notification Overlay at the top
    var activeToastMessage by varOf<InAppNotification?>(null)
    var lastObservedNotificationId by varOf("")

    LaunchedEffect(notifications) {
        if (notifications.isNotEmpty()) {
            val latest = notifications.first()
            if (latest.id != lastObservedNotificationId) {
                lastObservedNotificationId = latest.id
                // Trigger toast visible overlay only if it was added recently (within last 10 seconds)
                if (System.currentTimeMillis() - latest.timestamp < 10000) {
                    activeToastMessage = latest
                    delay(3800) // Keep visible
                    activeToastMessage = null
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BentoBackground)
    ) {
        Scaffold(
            bottomBar = {
                BentoNavigationBar(
                    activeTab = activeTab,
                    onSelectTab = { viewModel.changeTab(it) }
                )
            },
            floatingActionButton = {
                if (activeTab == 0 || activeTab == 1) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = BentoPurpleContainer,
                        contentColor = BentoPurpleText,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .testTag("add_item_fab")
                            .padding(bottom = 16.dp, end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Adicionar Produto",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            },
            containerColor = BentoBackground
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                // Header of application
                AppHeaderSection(
                    appName = "StockSync Pro",
                    online = syncOnline,
                    deviceCount = connectedDevices,
                    username = username,
                    isSyncing = isSyncing,
                    onProfileClick = { showConfigDialog = true },
                    onRefreshClick = { viewModel.forceImmediateSync() }
                )

                Divider(color = BentoGreyBorder.copy(alpha = 0.5f), thickness = 1.dp)

                // Select and display correct tab screen content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when (activeTab) {
                        0 -> DashboardTab(viewModel)
                        1 -> InventoryTab(viewModel)
                        2 -> SalesTab(viewModel)
                        3 -> ReportsTab(viewModel)
                    }
                }
            }
        }

        // Animated In-App Push Notification banner overlay at the top
        AnimatedVisibility(
            visible = activeToastMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(99f)
                .statusBarsPadding()
                .padding(14.dp)
        ) {
            activeToastMessage?.let { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { activeToastMessage = null }
                        .testTag("push_notification_banner"),
                    colors = CardDefaults.cardColors(containerColor = BentoPurpleText),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(BentoPurpleContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Sino",
                                tint = BentoPurpleText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NOTIFICAÇÃO EM TEMPO REAL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoPurpleContainer,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = notification.message,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Alerta",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Configuration Dialog
        if (showConfigDialog) {
            ConfigDialog(
                currentUsername = username,
                currentRoom = roomName,
                onDismiss = { showConfigDialog = false },
                onSave = { u, r ->
                    viewModel.updateProfile(u, r)
                    showConfigDialog = false
                }
            )
        }

        // Add Product Dialog
        if (showAddDialog) {
            AddItemDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, qty, cost, price ->
                    viewModel.addStockItem(name, qty, cost, price)
                    showAddDialog = false
                }
            )
        }
    }
}

// --- Helper UI Components ---

@Composable
fun AppHeaderSection(
    appName: String,
    online: Boolean,
    deviceCount: Int,
    username: String,
    isSyncing: Boolean,
    onProfileClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = appName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = BentoTextPrimary,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onRefreshClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (online) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (online) "ONLINE • $deviceCount CELULARES" else "OFFLINE • LOCAL APENAS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (online) Color(0xFF6B7280) else Color(0xFFE53935),
                    letterSpacing = 0.5.sp
                )
                if (isSyncing) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(Sincronizando...)",
                        fontSize = 10.sp,
                        color = BentoPurpleText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Profile Avatar Widget
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onRefreshClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(BentoGreyContainer, CircleShape)
                    .border(1.dp, BentoGreyBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sincronizar Manualmente",
                    tint = BentoTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            val initials = if (username.length >= 2) {
                username.substring(0, 2).uppercase()
            } else if (username.isNotEmpty()) {
                username.take(1).uppercase() + "D"
            } else {
                "JD"
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(BentoPurpleLight, CircleShape)
                    .border(2.dp, BentoPurpleContainer, CircleShape)
                    .clip(CircleShape)
                    .clickable { onProfileClick() }
                    .testTag("profile_avatar_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = BentoPurpleText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun BentoNavigationBar(
    activeTab: Int,
    onSelectTab: (Int) -> Unit
) {
    NavigationBar(
        containerColor = BentoGreyContainer,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(width = 1.dp, color = BentoGreyBorder.copy(alpha = 0.7f)),
    ) {
        val items = listOf(
            Triple("Painel", Icons.Default.Home, Icons.Default.Home),
            Triple("Estoque", Icons.Default.List, Icons.Default.List),
            Triple("Vendas", Icons.Default.ShoppingCart, Icons.Default.ShoppingCart),
            Triple("Relatórios", Icons.Default.Info, Icons.Default.Info)
        )

        items.forEachIndexed { index, (label, selectedIcon, unselectedIcon) ->
            val isSelected = activeTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(index) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) selectedIcon else unselectedIcon,
                        contentDescription = label,
                        tint = if (isSelected) BentoPurpleText else BentoTextSecondary
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) BentoPurpleText else BentoTextSecondary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = BentoPurpleContainer
                ),
                modifier = Modifier.testTag("nav_tab_$index")
            )
        }
    }
}

// --- Tabs Implementation ---

// TAB 0: PAINEL (Bento Grid layout)
@Composable
fun DashboardTab(viewModel: StockViewModel) {
    val stocks by viewModel.stocks.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val sales by viewModel.sales.collectAsState()

    // 1. Calculate values
    val totalEstoqueCost = stocks.sumOf { it.totalCost }
    
    // Profit today
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayStart = calendar.timeInMillis

    val totalProfitToday = sales.filter { it.timestamp >= todayStart }.sumOf { it.profit }
    
    // Reposição: count of items with quantity <= 4
    val itemsNeedReposition = stocks.filter { it.quantity <= 4 }.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Row 1: Primary Bento Card (Total Value)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("metric_total_value_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = BentoPurpleContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Valor Total em Estoque",
                            color = BentoPurpleText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "TEMPO REAL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoPurpleText,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = formatCurrency(totalEstoqueCost),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoPurpleText,
                        letterSpacing = (-1).sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "${stocks.size} produtos cadastrados no inventário",
                        fontSize = 12.sp,
                        color = BentoPurpleText.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // Row 2: Secondary Metrics (Daily Profit & Reposition)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Profit Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(145.dp)
                        .testTag("metric_profit_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoGreyContainer),
                    border = BorderStroke(1.dp, BentoGreyBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(BentoPurpleLight, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$", color = BentoPurpleText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        Column {
                            Text(
                                text = "LUCRO DIÁRIO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatCurrency(totalProfitToday),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Reposition Alert Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(145.dp)
                        .testTag("metric_reposition_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = BentoGreyContainer),
                    border = BorderStroke(1.dp, BentoGreyBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(BentoAccentPink, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Reposição",
                                tint = BentoAccentPinkText,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "REPOSIÇÃO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoTextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$itemsNeedReposition Itens",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (itemsNeedReposition > 0) BentoAccentPinkText else BentoTextPrimary
                            )
                        }
                    }
                }
            }
        }

        // Row 3: Action Log list panel
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recent_activity_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BentoGreyBorder.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HISTÓRICO RECENTE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            text = "Dispositivos ativos",
                            fontSize = 11.sp,
                            color = BentoPurpleText,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Relatório limpo",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Nenhuma movimentação realizada ainda.",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        // Display top 5 most recent logs
                        logs.take(5).forEach { log ->
                            BentoLogItemRow(log = log)
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BentoLogItemRow(log: ActionLog) {
    val initials = if (log.user.length >= 2) {
        log.user.substring(0, 2).uppercase()
    } else {
        log.user.take(1).uppercase() + "D"
    }

    // Styling according to action type
    val (avatarBg, avatarText) = when (log.actionType) {
        "SALE" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32)) // Green
        "CREATE", "ADD_STOCK" -> Pair(Color(0xFFFFE0B2), Color(0xFFE65100)) // Orange
        "DELETE", "REMOVE_STOCK" -> Pair(Color(0xFFFFCDD2), Color(0xFFC62828)) // Red
        else -> Pair(BentoPurpleLight, BentoPurpleText) // Purple/General edit
    }

    val actionWord = when (log.actionType) {
        "CREATE" -> "criou o produto"
        "ADD_STOCK" -> "adicionou estoque para"
        "REMOVE_STOCK" -> "tirou estoque de"
        "SALE" -> "vendeu"
        "DELETE" -> "excluiu o produto"
        else -> "editou"
    }

    val qtyDisplay = when (log.actionType) {
        "ADD_STOCK" -> "+${log.quantityChanged}"
        "REMOVE_STOCK", "SALE" -> "${log.quantityChanged}"
        else -> ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // User Initial Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(avatarBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = avatarText
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${log.user} $actionWord ${log.itemName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BentoTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatRelativeTime(log.timestamp)} • Qtde: ${if (qtyDisplay.isNotEmpty()) qtyDisplay else log.quantityChanged}",
                fontSize = 10.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }

        // Action values indicators spacer
        if (log.actionType == "SALE") {
            Text(
                text = "Venda",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2E7D32),
                modifier = Modifier
                    .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// TAB 1: ESTOQUE (Inventory product list & search engine)
@Composable
fun InventoryTab(viewModel: StockViewModel) {
    val stocks by viewModel.stocks.collectAsState()
    val username by viewModel.username.collectAsState()

    var searchQuery by varOf("")
    var selectedItemForEdit by varOf<StockItem?>(null)

    val filteredStocks = stocks.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search Input Header
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar produto no estoque...", color = Color.Gray) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "S", tint = Color.Gray) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = BentoPurpleContainer,
                unfocusedIndicatorColor = BentoGreyBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("inventory_search_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredStocks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Limpo",
                        tint = Color.LightGray,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Nenhum produto atende a busca." else "Estoque vazio. Toque no '+' para cadastrar!",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredStocks, key = { it.id }) { item ->
                    InventoryItemCard(
                        item = item,
                        currentUser = username,
                        onPlus = { viewModel.adjustStockQuantity(item.id, 1) },
                        onMinus = { viewModel.adjustStockQuantity(item.id, -1) },
                        onEdit = { selectedItemForEdit = item },
                        onDelete = { viewModel.deleteStockItem(item) }
                    )
                }
            }
        }
    }

    if (selectedItemForEdit != null) {
        EditItemDialog(
            item = selectedItemForEdit!!,
            onDismiss = { selectedItemForEdit = null },
            onUpdate = { name, quantity, cost, price ->
                viewModel.updateItem(selectedItemForEdit!!.id, name, quantity, cost, price)
                selectedItemForEdit = null
            }
        )
    }
}

@Composable
fun InventoryItemCard(
    item: StockItem,
    currentUser: String,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedActions by varOf(false)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_item_card_${item.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BentoGreyBorder.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Item details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Custo: ${formatCurrency(item.unitCost)} • Venda: ${formatCurrency(item.unitPrice)}",
                        fontSize = 11.sp,
                        color = BentoTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Inline quick adjustment increment controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onMinus,
                        enabled = item.quantity > 0,
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                color = if (item.quantity > 0) BentoGreyContainer else BentoGreyContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .testTag("item_minus_button_${item.id}")
                    ) {
                        Text("-", color = BentoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(min = 34.dp)
                            .height(34.dp)
                            .background(BentoPurpleLight, RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.quantity.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BentoPurpleText,
                            modifier = Modifier.testTag("item_stock_qty_${item.id}")
                        )
                    }

                    IconButton(
                        onClick = onPlus,
                        modifier = Modifier
                            .size(34.dp)
                            .background(BentoPurpleContainer, RoundedCornerShape(10.dp))
                            .testTag("item_plus_button_${item.id}")
                    ) {
                        Text("+", color = BentoPurpleText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Divider(color = Color.LightGray.copy(alpha = 0.3f))

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Calculated automatic total cost
                Column {
                    Text(
                        text = "VALOR TOTAL ESTOCADO",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = formatCurrency(item.totalCost),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = BentoPurpleText
                    )
                }

                // Expandable tools
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { expandedActions = !expandedActions },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (expandedActions) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Ações",
                            tint = Color.Gray
                        )
                    }
                }
            }

            if (expandedActions) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = BentoGreyContainer),
                        border = BorderStroke(1.dp, BentoGreyBorder),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("edit_product_btn_${item.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "E", tint = BentoTextPrimary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editar", color = BentoTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = BentoAccentPink),
                        border = BorderStroke(1.dp, BentoAccentPinkText.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("delete_product_btn_${item.id}")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "D", tint = BentoAccentPinkText, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excluir", color = BentoAccentPinkText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Última edição por ${item.lastUpdatedBy} há ${formatRelativeTime(item.lastUpdatedAt)}",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// TAB 2: VENDAS (Sales registers panel)
@Composable
fun SalesTab(viewModel: StockViewModel) {
    val stocks by viewModel.stocks.collectAsState()
    val sales by viewModel.sales.collectAsState()

    var selectedItem by varOf<StockItem?>(null)
    var selectByItemName by varOf("")
    var quantityInput by varOf("1")
    var expandSearchDropdown by varOf(false)

    // Calculate dynamic profit metrics
    val totalRevenue = sales.sumOf { it.quantity * it.unitPrice }
    val totalCosts = sales.sumOf { it.quantity * it.unitCost }
    val totalProfit = totalRevenue - totalCosts

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sales registration card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sell_product_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BentoGreyBorder.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "REGISTRAR NOVA VENDA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Selector of Product
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (selectedItem != null) selectedItem!!.name else selectByItemName,
                            onValueChange = {
                                selectByItemName = it
                                selectedItem = null
                                expandSearchDropdown = true
                            },
                            placeholder = { Text("Selecione um produto...", color = Color.Gray) },
                            label = { Text("Produto do Estoque") },
                            trailingIcon = {
                                IconButton(onClick = { expandSearchDropdown = !expandSearchDropdown }) {
                                    Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "D")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BentoPurpleContainer,
                                unfocusedBorderColor = BentoGreyBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sale_product_input")
                        )

                        // Simple Dropdown with filtered stock results
                        if (expandSearchDropdown) {
                            val itemsMatching = stocks.filter {
                                it.name.contains(selectByItemName, ignoreCase = true) && it.quantity > 0
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 64.dp)
                                    .zIndex(10f)
                                    .border(1.dp, BentoGreyBorder, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = BentoGreyContainer)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                        .verticalScrollState()
                                ) {
                                    if (itemsMatching.isEmpty()) {
                                        Text(
                                            text = "Nenhum produto disponível com estoque.",
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    } else {
                                        itemsMatching.forEach { item ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedItem = item
                                                        selectByItemName = item.name
                                                        expandSearchDropdown = false
                                                    }
                                                    .padding(14.dp)
                                                    .testTag("dropdown_item_${item.id}"),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BentoTextPrimary
                                                )
                                                Text(
                                                    text = "Qtd: ${item.quantity} • Venda: ${formatCurrency(item.unitPrice)}",
                                                    fontSize = 11.sp,
                                                    color = BentoTextSecondary
                                                )
                                            }
                                            Divider(color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Show selected item values
                    selectedItem?.let { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BentoGreyContainer, RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ESTOQUE DISPONÍVEL", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${item.quantity} unidades", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("PREÇO DE VENDA", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(formatCurrency(item.unitPrice), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BentoPurpleText)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Quantity selector Input
                    OutlinedTextField(
                        value = quantityInput,
                        onValueChange = { quantityInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Quantidade Vendida") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BentoPurpleContainer,
                            unfocusedBorderColor = BentoGreyBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sale_quantity_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Automatic calculation of profit
                    val quantityToSell = quantityInput.toIntOrNull() ?: 0
                    if (selectedItem != null && quantityToSell > 0) {
                        val price = selectedItem!!.unitPrice
                        val cost = selectedItem!!.unitCost
                        val revenueCalculated = price * quantityToSell
                        val profitCalculated = (price - cost) * quantityToSell

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Receita total:", fontSize = 13.sp, color = BentoTextSecondary)
                            Text(formatCurrency(revenueCalculated), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Lucro estimado:", fontSize = 13.sp, color = BentoTextSecondary)
                            Text(
                                text = formatCurrency(profitCalculated),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (profitCalculated >= 0) Color(0xFF2E7D32) else BentoAccentPinkText
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Button submit sale
                    Button(
                        onClick = {
                            selectedItem?.let {
                                viewModel.sellStockItem(it.id, quantityToSell)
                                // reset inputs
                                selectedItem = null
                                selectByItemName = ""
                                quantityInput = "1"
                            }
                        },
                        enabled = selectedItem != null && quantityToSell > 0 && quantityToSell <= selectedItem!!.quantity,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BentoPurpleText,
                            disabledContainerColor = Color.LightGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_sale_button")
                    ) {
                        val messageDesc = if (selectedItem == null) {
                            "Selecione um Produto"
                        } else if (quantityToSell > selectedItem!!.quantity) {
                            "Quantidade Superior ao Estoque"
                        } else {
                            "Registrar Saída de Venda"
                        }
                        Text(messageDesc, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Summary financial statistics
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sales_summary_finance_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = BentoGreyContainer),
                border = BorderStroke(1.dp, BentoGreyBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FATURAMENTO", fontSize = 8.sp, color = BentoTextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(formatCurrency(totalRevenue), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
                    }

                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(BentoGreyBorder).align(Alignment.CenterVertically))

                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LUCRO LÍQUIDO", fontSize = 8.sp, color = BentoTextSecondary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(formatCurrency(totalProfit), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }
            }
        }

        // Historic of transactions list
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sales_history_card"),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BentoGreyBorder.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "HISTÓRICO DE VENDAS COMPLETO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        letterSpacing = 1.2.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (sales.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 30.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Nenhuma venda realizada até o momento.", fontSize = 12.sp, color = Color.Gray)
                        }
                    } else {
                        sales.forEach { sale ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "${sale.quantity}x ${sale.itemName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BentoTextPrimary
                                    )
                                    Text(
                                        text = "Vendido por ${sale.user} • ${formatDate(sale.timestamp)}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+ ${formatCurrency(sale.unitPrice * sale.quantity)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                    Text(
                                        text = "Lucro: ${formatCurrency(sale.profit)}",
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            Divider(color = Color.LightGray.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}

// TAB 3: RELATÓRIOS (Historical logs & stats per user)
@Composable
fun ReportsTab(viewModel: StockViewModel) {
    val logs by viewModel.logs.collectAsState()
    val sales by viewModel.sales.collectAsState()

    // Grouping analytics by user
    val userLogsMap = logs.groupBy { it.user }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 45.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "DESEMPENHO POR COLABORADOR",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (userLogsMap.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BentoGreyBorder),
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Cadastre produtos ou configure sync para ver relatórios por usuário.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            // Report calculations for each user profile
            items(userLogsMap.keys.toList()) { user ->
                val userLogs = userLogsMap[user] ?: emptyList()
                val createCount = userLogs.filter { it.actionType == "CREATE" }.size
                val addStockCount = userLogs.filter { it.actionType == "ADD_STOCK" }.size
                val removeStockCount = userLogs.filter { it.actionType == "REMOVE_STOCK" }.size
                val deleteCount = userLogs.filter { it.actionType == "DELETE" }.size

                // Sales made by this user
                val userSales = sales.filter { it.user == user }
                val salesCount = userSales.size
                val totalSalesRevenue = userSales.sumOf { it.quantity * it.unitPrice }
                val totalSalesProfit = userSales.sumOf { it.profit }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_user_card_$user"),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BentoGreyBorder.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lançamentos de $user",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = BentoPurpleText
                            )

                            Box(
                                modifier = Modifier
                                    .background(BentoPurpleLight, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${userLogs.size} movimentações",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BentoPurpleText
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Grid metrics summary for this specific user
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Column 1: Items created/edited
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = BentoGreyContainer),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("PRODUTOS", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Cadastros: $createCount", fontSize = 11.sp, color = BentoTextPrimary)
                                    Text("Ajustes (+): $addStockCount", fontSize = 11.sp, color = BentoTextPrimary)
                                    Text("Remoções (-): $removeStockCount", fontSize = 11.sp, color = BentoTextPrimary)
                                    if (deleteCount > 0) {
                                        Text("Exclusões: $deleteCount", fontSize = 11.sp, color = BentoAccentPinkText)
                                    }
                                }
                            }

                            // Column 2: Financial Sales contribution
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = BentoGreyContainer),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("VENDAS E CRÉDITO", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Total de vendas: $salesCount", fontSize = 11.sp, color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                                    Text("Faturou: ${formatCurrency(totalSalesRevenue)}", fontSize = 11.sp, color = BentoTextPrimary)
                                    Text("Lucro: ${formatCurrency(totalSalesProfit)}", fontSize = 11.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Dynamic Interactive Dialogs ---

@Composable
fun ConfigDialog(
    currentUsername: String,
    currentRoom: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var rawInputUser by varOf(currentUsername)
    var rawInputRoom by varOf(currentRoom)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("app_configuration_dialog"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "Ajustar Conexão Sync",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Altere as credenciais para ver as atualizações em tempo real sincronizadas entre múltiplos celulares simultaneamente.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(18.dp))

                OutlinedTextField(
                    value = rawInputUser,
                    onValueChange = { rawInputUser = it },
                    label = { Text("Nome de Usuário") },
                    placeholder = { Text("Ex: Ana, Matheus, JD") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BentoPurpleContainer,
                        unfocusedBorderColor = BentoGreyBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config_username_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = rawInputRoom,
                    onValueChange = { rawInputRoom = it },
                    label = { Text("Chave da Sala de Sincronização") },
                    placeholder = { Text("Ex: Padaria, Estoque-Principal") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BentoPurpleContainer,
                        unfocusedBorderColor = BentoGreyBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("config_roomname_input")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("config_dialog_cancel")) {
                        Text("Cancelar", color = BentoTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(rawInputUser, rawInputRoom) },
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPurpleText),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("config_dialog_save")
                    ) {
                        Text("Salvar & Sincronizar", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AddItemDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int, Double, Double) -> Unit
) {
    var name by varOf("")
    var quantity by varOf("1")
    var unitCost by varOf("")
    var unitPrice by varOf("")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("add_item_dialog_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScrollState()
            ) {
                Text(
                    text = "Cadastrar Novo Produto",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Produto") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_name_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                    label = { Text("Estoque Inicial") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_qty_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = unitCost,
                    onValueChange = { unitCost = it.replace(",", ".") },
                    label = { Text("Preço Unitário de Compra (Custo) R$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_cost_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it.replace(",", ".") },
                    label = { Text("Preço Unitário de Venda R$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_price_field")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("add_dialog_cancel")) {
                        Text("Cancelar", color = BentoTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val qtyVal = quantity.toIntOrNull() ?: 0
                            val costVal = unitCost.toDoubleOrNull() ?: 0.0
                            val priceVal = unitPrice.toDoubleOrNull() ?: 0.0
                            if (name.isNotBlank()) {
                                onAdd(name, qtyVal, costVal, priceVal)
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPurpleText),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_dialog_save")
                    ) {
                        Text("Salvar Produto", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun EditItemDialog(
    item: StockItem,
    onDismiss: () -> Unit,
    onUpdate: (String, Int, Double, Double) -> Unit
) {
    var name by varOf(item.name)
    var quantity by varOf(item.quantity.toString())
    var unitCost by varOf(item.unitCost.toString())
    var unitPrice by varOf(item.unitPrice.toString())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("edit_item_dialog_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScrollState()
            ) {
                Text(
                    text = "Editar Produto",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BentoTextPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Produto") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_name_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantidade em Estoque") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_qty_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = unitCost,
                    onValueChange = { unitCost = it.replace(",", ".") },
                    label = { Text("Preço Unitário de Compra R$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_cost_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it.replace(",", ".") },
                    label = { Text("Preço Unitário de Venda R$") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_price_field")
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("edit_dialog_cancel")) {
                        Text("Cancelar", color = BentoTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val qtyVal = quantity.toIntOrNull() ?: 0
                            val costVal = unitCost.toDoubleOrNull() ?: 0.0
                            val priceVal = unitPrice.toDoubleOrNull() ?: 0.0
                            if (name.isNotBlank()) {
                                onUpdate(name, qtyVal, costVal, priceVal)
                            }
                        },
                        enabled = name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BentoPurpleText),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("edit_dialog_save")
                    ) {
                        Text("Atualizar Produto", color = Color.White)
                    }
                }
            }
        }
    }
}

// --- Utilities & Syntactic Sugars ---

@Composable
fun <T> varOf(initialValue: T): MutableState<T> {
    return remember { mutableStateOf(initialValue) }
}

fun Modifier.verticalScrollState(): Modifier = composed {
    val state = rememberScrollState()
    this.verticalScroll(state)
}

fun formatCurrency(value: Double): String {
    return "R$ " + String.format(Locale.US, "%,.2f", value).replace(",", "X").replace(".", ",").replace("X", ".")
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "Há poucos segundos"
        diff < 3600_000 -> "Há ${diff / 60_000} minutos"
        diff < 86400_000 -> "Há ${diff / 3600_000} horas"
        else -> "Há ${diff / 86400_000} dias"
    }
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
