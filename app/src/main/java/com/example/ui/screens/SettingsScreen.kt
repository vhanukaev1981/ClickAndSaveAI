package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Current preference values from ViewModel
    val currentGoal by viewModel.monthlySavingsGoal.collectAsState()
    val currentElectricity by viewModel.preferredElectricityProvider.collectAsState()
    val currentCellular by viewModel.preferredCellularProvider.collectAsState()
    val currentInternet by viewModel.preferredInternetProvider.collectAsState()
    val currentInsurance by viewModel.preferredInsuranceProvider.collectAsState()
    val currentStreaming by viewModel.preferredStreamingProvider.collectAsState()
    val currentAutoSwitch by viewModel.autoSwitchAlertsEnabled.collectAsState()
    val currentThreshold by viewModel.minSavingsThreshold.collectAsState()

    // Local editing state
    var savingsGoalInput by remember(currentGoal) { mutableStateOf(currentGoal.toInt().toString()) }
    var selectedElectricity by remember(currentElectricity) { mutableStateOf(currentElectricity) }
    var selectedCellular by remember(currentCellular) { mutableStateOf(currentCellular) }
    var selectedInternet by remember(currentInternet) { mutableStateOf(currentInternet) }
    var selectedInsurance by remember(currentInsurance) { mutableStateOf(currentInsurance) }
    var selectedStreaming by remember(currentStreaming) { mutableStateOf(currentStreaming) }
    var isAutoSwitchActive by remember(currentAutoSwitch) { mutableStateOf(currentAutoSwitch) }
    var minThresholdInput by remember(currentThreshold) { mutableStateOf(currentThreshold.toInt().toString()) }

    // Dropdown expanded states
    var expElectricity by remember { mutableStateOf(false) }
    var expCellular by remember { mutableStateOf(false) }
    var expInternet by remember { mutableStateOf(false) }
    var expInsurance by remember { mutableStateOf(false) }
    var expStreaming by remember { mutableStateOf(false) }

    val electricityOptions = listOf("אלקטרה פאוור", "סלקום אנרגיה", "פזגז חשמל", "חברת החשמל (ברירת מחדל)")
    val cellularOptions = listOf("019 מובייל", "Wecom", "פלאפון", "סלקום", "פרטנר", "גולן טלקום")
    val internetOptions = listOf("בזק סיבים (Bezeq Fiber)", "סלקום פייבר", "פרטנר סיבים", "הוט פייבר", "אנלימיטד")
    val insuranceOptions = listOf("הראל ביטוח", "הפניקס", "מגדל", "כלל ביטוח", "ביטוח ישיר", "AIG")
    val streamingOptions = listOf("FreeTV", "סלקום TV", "פרטנר TV", "YES / STING", "HOT / NEXT")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזרה")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚙️ הגדרות והעדפות חיסכון",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "עדכון יעדי חיסכון חודשיים, ספקי שירות מועדפים והעדפות אופטימיזציה",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Monthly Savings Goal (הגדרת יעדי חיסכון חודשיים)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_savings_goal_setting"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(EmeraldSavings.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Savings, contentDescription = null, tint = EmeraldSavings, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "יעד חיסכון חודשי למשק הבית",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "הגדר את יעד החיסכון החודשי שברצונך להשיג באמצעות המערכת",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = savingsGoalInput,
                        onValueChange = { savingsGoalInput = it.filter { char -> char.isDigit() } },
                        label = { Text("יעד חיסכון חודשי (₪)") },
                        leadingIcon = { Icon(Icons.Default.AttachMoney, contentDescription = null, tint = EmeraldSavings) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset buttons
                    Text(
                        text = "בחירה מהירה:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1000, 2000, 3000, 5000).forEach { preset ->
                            OutlinedButton(
                                onClick = { savingsGoalInput = preset.toString() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Text("₪$preset", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    val goalVal = savingsGoalInput.toDoubleOrNull() ?: 2000.0
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = EmeraldSavings.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("פוטנציאל חיסכון שנתי מתוכנן:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "₪${String.format("%,.0f", goalVal * 12)} / שנה",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings)
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Preferred Service Providers (ספקי שירות מועדפים)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_preferred_providers_setting"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(TechBluePrimary.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ספקי שירות מועדפים",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "בחירת הספקים שאתה מעדיף שה-AI יציע בעת השוואת מחירים",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ⚡ Electricity
                    PreferenceDropdownField(
                        label = "⚡ ספק חשמל מועדף",
                        selectedOption = selectedElectricity,
                        options = electricityOptions,
                        expanded = expElectricity,
                        onExpandedChange = { expElectricity = it },
                        onOptionSelected = {
                            selectedElectricity = it
                            expElectricity = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 📱 Cellular
                    PreferenceDropdownField(
                        label = "📱 ספק סלולר מועדף",
                        selectedOption = selectedCellular,
                        options = cellularOptions,
                        expanded = expCellular,
                        onExpandedChange = { expCellular = it },
                        onOptionSelected = {
                            selectedCellular = it
                            expCellular = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 🌐 Fiber Internet
                    PreferenceDropdownField(
                        label = "🌐 ספק אינטרנט/סיבים מועדף",
                        selectedOption = selectedInternet,
                        options = internetOptions,
                        expanded = expInternet,
                        onExpandedChange = { expInternet = it },
                        onOptionSelected = {
                            selectedInternet = it
                            expInternet = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 🛡️ Insurance
                    PreferenceDropdownField(
                        label = "🛡️ חברת ביטוח מועדפת",
                        selectedOption = selectedInsurance,
                        options = insuranceOptions,
                        expanded = expInsurance,
                        onExpandedChange = { expInsurance = it },
                        onOptionSelected = {
                            selectedInsurance = it
                            expInsurance = false
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 📺 Streaming/TV
                    PreferenceDropdownField(
                        label = "📺 ספק טלוויזיה/סטרימינג מועדף",
                        selectedOption = selectedStreaming,
                        options = streamingOptions,
                        expanded = expStreaming,
                        onExpandedChange = { expStreaming = it },
                        onOptionSelected = {
                            selectedStreaming = it
                            expStreaming = false
                        }
                    )
                }
            }
        }

        // Section 3: Optimization & Alert Threshold Preferences
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_optimization_rules_setting"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "הגדרות סף התראות ואופטימיזציה",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Threshold Input
                    OutlinedTextField(
                        value = minThresholdInput,
                        onValueChange = { minThresholdInput = it.filter { char -> char.isDigit() } },
                        label = { Text("סף חיסכון מינימלי לקבלת התראה (₪/חודש)") },
                        supportingText = { Text("התראות יישלחו רק על הזדמנויות חיסכון מעל סכום זה") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle Auto Switch Recommendations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("המלצות מעבר אוטומטיות בלחיצה אחת", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text("אפשר ל-AI להכין בקשות מעבר לספק מוזל בלחיצה אחת", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAutoSwitchActive,
                            onCheckedChange = { isAutoSwitchActive = it }
                        )
                    }
                }
            }
        }

        // Save Preferences Button
        item {
            Button(
                onClick = {
                    val goal = savingsGoalInput.toDoubleOrNull() ?: 2000.0
                    val threshold = minThresholdInput.toDoubleOrNull() ?: 20.0
                    viewModel.updatePreferences(
                        goal = goal,
                        electricity = selectedElectricity,
                        cellular = selectedCellular,
                        internet = selectedInternet,
                        insurance = selectedInsurance,
                        streaming = selectedStreaming,
                        autoSwitch = isAutoSwitchActive,
                        minThreshold = threshold
                    )
                    Toast.makeText(context, "✅ העדפות החיסכון והספקים עודכנו בהצלחה!", Toast.LENGTH_LONG).show()
                    onBackClick?.invoke()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_save_preferences"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "שמור העדפות ויעדי חיסכון",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreferenceDropdownField(
    label: String,
    selectedOption: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onOptionSelected(option) }
                )
            }
        }
    }
}
