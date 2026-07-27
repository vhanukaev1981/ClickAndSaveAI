package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.IsraeliMarketData
import com.example.data.local.MarketProviderOption
import com.example.ui.MainViewModel
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("הכל") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedProviderForSwitch by remember { mutableStateOf<MarketProviderOption?>(null) }

    val categories = IsraeliMarketData.allCategories

    val options = IsraeliMarketData.getOptionsForCategory(selectedCategory).filter {
        if (searchQuery.isBlank()) true
        else it.providerName.contains(searchQuery, ignoreCase = true) ||
             it.planName.contains(searchQuery, ignoreCase = true) ||
             it.highlights.contains(searchQuery, ignoreCase = true)
    }

    selectedProviderForSwitch?.let { option ->
        AlertDialog(
            onDismissRequest = { selectedProviderForSwitch = null },
            title = {
                Text("בקשת מעבר מסלול: ${option.providerName}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("נבחר מסלול: ${option.planName}")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("הטבות: ${option.highlights}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("מחיר: ${option.priceRange}", style = MaterialTheme.typography.bodyMedium, color = EmeraldSavings, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("לחיצה על 'אישור' תעביר את הפרטים לנציג הספק ליצירת קשר מהירה ללא בירוקרטיה.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        Toast.makeText(context, "בקשת מעבר ל-${option.providerName} נשלחה בהצלחה!", Toast.LENGTH_LONG).show()
                        selectedProviderForSwitch = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSavings)
                ) {
                    Text("אישור ובקשת מעבר בקליק")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProviderForSwitch = null }) {
                    Text("ביטול")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("providers_screen")
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "מאגר ספקי שירות ומסלולים בישראל",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "ספקי חשמל, סלולר 5G, סיבים אופטיים, ביטוחים וטלוויזיה במחיר מנצח",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("חפש ספק, מסלול או חבילה...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "נקה")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TechBluePrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // List of Options with Dynamic Comparison Tool at Top
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dynamic Comparison Component (השוואת 2 ספקים ראש בראש)
            item {
                DynamicProviderComparisonCard(
                    allOptions = IsraeliMarketData.getOptionsForCategory("הכל"),
                    onSelectForSwitch = { option ->
                        selectedProviderForSwitch = option
                    }
                )
            }

            item {
                Text(
                    text = "כל הספקים והמסלולים בקטגוריה הנבחרת:",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(options) { option ->
                MarketOptionCard(
                    option = option,
                    onSelect = { selectedProviderForSwitch = option }
                )
            }
        }
    }
}

@Composable
fun MarketOptionCard(
    option: MarketProviderOption,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val icon = when (option.category) {
                        "חשמל" -> Icons.Default.ElectricBolt
                        "סלולר" -> Icons.Default.Smartphone
                        "אינטרנט" -> Icons.Default.Wifi
                        "ביטוח" -> Icons.Default.Security
                        else -> Icons.Default.Tv
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(TechBluePrimary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = option.providerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = option.planName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Surface(
                    color = EmeraldSavings.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = option.category,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSavings
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = option.highlights,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "טווח מחירים: ${option.priceRange}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = EmeraldSavings
            )

            Text(
                text = option.discountDetails,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
            ) {
                Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("⚡ לבקשת מעבר מסלול בלחיצת כפתור")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicProviderComparisonCard(
    allOptions: List<MarketProviderOption>,
    onSelectForSwitch: (MarketProviderOption) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    var selectedCategoryFilter by remember { mutableStateOf("חשמל") }

    val filteredOptions = remember(selectedCategoryFilter) {
        IsraeliMarketData.getOptionsForCategory(selectedCategoryFilter)
    }

    var providerA by remember(selectedCategoryFilter) {
        mutableStateOf(filteredOptions.firstOrNull() ?: allOptions.first())
    }
    var providerB by remember(selectedCategoryFilter) {
        mutableStateOf(filteredOptions.getOrNull(1) ?: filteredOptions.firstOrNull() ?: allOptions.last())
    }

    var showPickerA by remember { mutableStateOf(false) }
    var showPickerB by remember { mutableStateOf(false) }

    if (showPickerA) {
        ProviderSelectionDialog(
            title = "בחר ספק ראשון להשוואה",
            options = filteredOptions,
            currentSelection = providerA,
            onDismiss = { showPickerA = false },
            onSelect = {
                providerA = it
                showPickerA = false
            }
        )
    }

    if (showPickerB) {
        ProviderSelectionDialog(
            title = "בחר ספק שני להשוואה",
            options = filteredOptions,
            currentSelection = providerB,
            onDismiss = { showPickerB = false },
            onSelect = {
                providerB = it
                showPickerB = false
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dynamic_provider_comparison_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TechBluePrimary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = TechBluePrimary.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Compare, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "השוואת ספקים דינמית (ראש בראש)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "בחר שני ספקים והשווה מסלולים, דמי מנוי ותנאי התקשרות",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "הרחב/קפל"
                    )
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Category selector chip row
                Text(
                    text = "קטגוריית השוואה:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("חשמל", "סלולר", "אינטרנט", "ביטוח", "טלוויזיה ומנויים")) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat,
                            onClick = { selectedCategoryFilter = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TechBluePrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector Cards for Provider A and Provider B
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Provider A Selector
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPickerA = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, TechBluePrimary)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("ספק א'", style = MaterialTheme.typography.labelSmall, color = TechBluePrimary, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(providerA.providerName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(providerA.planName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }

                    // VS badge
                    Surface(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        Text(
                            text = "מול",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Provider B Selector
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPickerB = true },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, EmeraldSavings)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("ספק ב'", style = MaterialTheme.typography.labelSmall, color = EmeraldSavings, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(providerB.providerName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                            Text(providerB.planName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Comparison Table
                Text(
                    text = "טבלת השוואה מפורטת:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Header Row
                        ComparisonRow(
                            label = "פרט להשוואה",
                            valA = providerA.providerName,
                            valB = providerB.providerName,
                            isHeader = true
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                        // Category
                        ComparisonRow(
                            label = "קטגוריה",
                            valA = providerA.category,
                            valB = providerB.category
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 6.dp))

                        // Price Plan
                        ComparisonRow(
                            label = "מסלול מחירים",
                            valA = providerA.planName,
                            valB = providerB.planName,
                            highlightA = true,
                            highlightB = true
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 6.dp))

                        // Monthly Subscription / Price
                        ComparisonRow(
                            label = "דמי מנוי ומחיר חודשי",
                            valA = providerA.priceRange,
                            valB = providerB.priceRange,
                            colorA = TechBluePrimary,
                            colorB = EmeraldSavings
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 6.dp))

                        // Contract Conditions & Highlights
                        ComparisonRow(
                            label = "תנאי התקשרות והטבות",
                            valA = providerA.highlights,
                            valB = providerB.highlights
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 6.dp))

                        // Discount & Transfer Details
                        ComparisonRow(
                            label = "תנאי הנחה וסוג ניוד",
                            valA = providerA.discountDetails,
                            valB = providerB.discountDetails
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons for switching
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSelectForSwitch(providerA) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("בחר ${providerA.providerName.take(12)}...", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }

                    Button(
                        onClick = { onSelectForSwitch(providerB) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSavings),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("בחר ${providerB.providerName.take(12)}...", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    valA: String,
    valB: String,
    isHeader: Boolean = false,
    highlightA: Boolean = false,
    highlightB: Boolean = false,
    colorA: Color? = null,
    colorB: Color? = null
) {
    Column {
        if (!isHeader) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = valA,
                modifier = Modifier.weight(1f),
                style = if (isHeader) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = TechBluePrimary)
                        else MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (highlightA) FontWeight.Bold else FontWeight.Normal,
                            color = colorA ?: MaterialTheme.colorScheme.onSurface
                        )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = valB,
                modifier = Modifier.weight(1f),
                style = if (isHeader) MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldSavings)
                        else MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (highlightB) FontWeight.Bold else FontWeight.Normal,
                            color = colorB ?: MaterialTheme.colorScheme.onSurface
                        )
            )
        }
    }
}

@Composable
private fun ProviderSelectionDialog(
    title: String,
    options: List<MarketProviderOption>,
    currentSelection: MarketProviderOption,
    onDismiss: () -> Unit,
    onSelect: (MarketProviderOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 350.dp)
            ) {
                items(options) { item ->
                    val isSelected = item == currentSelection
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) TechBluePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, TechBluePrimary) else null
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(item.providerName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text(item.planName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.priceRange, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

