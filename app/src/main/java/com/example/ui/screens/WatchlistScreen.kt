package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.WatchlistItem
import com.example.ui.MainViewModel
import com.example.ui.components.PriceTrendChart
import com.example.ui.theme.AiVioletPrimary
import com.example.ui.theme.EmeraldSavings

@Composable
fun WatchlistScreen(
    viewModel: MainViewModel,
    onInspectItem: (String) -> Unit
) {
    val watchlistItems by viewModel.watchlistItems.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    val categories = listOf("הכל", "טכנולוגיה", "אופנה", "לבית", "סופרמרקט")

    val filteredList = if (selectedCategory == "הכל" || selectedCategory == "All") {
        watchlistItems
    } else {
        watchlistItems.filter { it.category.contains(selectedCategory, ignoreCase = true) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AiVioletPrimary,
                contentColor = Color.White,
                modifier = Modifier
                    .padding(bottom = 80.dp)
                    .testTag("watchlist_add_fab")
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("עקוב אחר מוצר", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("watchlist_screen")
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "מעקב מחירים והתראות ירידת מחיר",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "מנוע ה-AI עוקב 24/7 ומתריע כשהמחיר מגיע ליעד שהגדרת",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { viewModel.setCategoryFilter(cat) },
                                label = { Text(cat) }
                            )
                        }
                    }
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TrendingDown,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "אין מוצרים ברשימת המעקב",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "לחץ על '+ עקוב אחר מוצר' כדי להתחיל לנטר ירידות מחיר.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        WatchlistItemCard(
                            item = item,
                            onDelete = { viewModel.deleteWatchlistItem(item.id) },
                            onInspect = { onInspectItem(item.name) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWatchlistItemDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, store, origPrice, curPrice, targetPrice, category ->
                viewModel.addWatchlistItem(name, store, origPrice, curPrice, targetPrice, category)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun WatchlistItemCard(
    item: WatchlistItem,
    onDelete: () -> Unit,
    onInspect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = item.storeName,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AiVioletPrimary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall.copy(color = AiVioletPrimary),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "מחק", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Price Chart & Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "₪${String.format("%.2f", item.currentPrice)}",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSavings
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "₪${String.format("%.2f", item.originalPrice)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (item.isTargetMet) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (item.isTargetMet) EmeraldSavings else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isTargetMet) "מחיר יעד הושג!" else "מחיר יעד: ₪${String.format("%.2f", item.targetPrice)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (item.isTargetMet) EmeraldSavings else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Box(modifier = Modifier.width(120.dp)) {
                    PriceTrendChart(priceHistoryCsv = item.priceHistoryCsv)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onInspect,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("בדיקת AI")
                }
            }
        }
    }
}

@Composable
fun AddWatchlistItemDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, store: String, origPrice: Double, curPrice: Double, targetPrice: Double, category: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("KSP") }
    var origPriceStr by remember { mutableStateOf("") }
    var curPriceStr by remember { mutableStateOf("") }
    var targetPriceStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("טכנולוגיה") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוספת מוצר למעקב מחירים") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("שם המוצר או קישור") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text("שם החנות (למשל KSP, מחסני חשמל, אמזון)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = origPriceStr,
                        onValueChange = { origPriceStr = it },
                        label = { Text("מחיר מקורי (₪)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = curPriceStr,
                        onValueChange = { curPriceStr = it },
                        label = { Text("מחיר נוכחי (₪)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = targetPriceStr,
                    onValueChange = { targetPriceStr = it },
                    label = { Text("מחיר יעד להתראה (₪)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val op = origPriceStr.toDoubleOrNull() ?: 100.0
                    val cp = curPriceStr.toDoubleOrNull() ?: op
                    val tp = targetPriceStr.toDoubleOrNull() ?: (cp * 0.85)
                    if (name.isNotBlank()) {
                        onAdd(name, store, op, cp, tp, category)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AiVioletPrimary)
            ) {
                Text("התחל מעקב")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}
