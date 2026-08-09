package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.theme.FinancialDesignTokens
import com.example.ui.theme.TechBluePrimary

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: (() -> Unit)? = null
) {
    val currentGoal by viewModel.monthlySavingsGoal.collectAsState()
    val currentElectricity by viewModel.preferredElectricityProvider.collectAsState()
    val currentCellular by viewModel.preferredCellularProvider.collectAsState()
    val currentInternet by viewModel.preferredInternetProvider.collectAsState()
    val currentInsurance by viewModel.preferredInsuranceProvider.collectAsState()
    val currentStreaming by viewModel.preferredStreamingProvider.collectAsState()
    val currentThreshold by viewModel.minSavingsThreshold.collectAsState()

    var goalInput by remember(currentGoal) { mutableStateOf(currentGoal.toInt().takeIf { it > 0 }?.toString().orEmpty()) }
    var thresholdInput by remember(currentThreshold) { mutableStateOf(currentThreshold.toInt().takeIf { it > 0 }?.toString().orEmpty()) }
    var electricity by remember(currentElectricity) { mutableStateOf(currentElectricity) }
    var cellular by remember(currentCellular) { mutableStateOf(currentCellular) }
    var internet by remember(currentInternet) { mutableStateOf(currentInternet) }
    var insurance by remember(currentInsurance) { mutableStateOf(currentInsurance) }
    var streaming by remember(currentStreaming) { mutableStateOf(currentStreaming) }
    var savedSignature by remember { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    val electricityOptions = listOf("לא נבחר", "חברת החשמל", "אלקטרה פאוור", "סלקום אנרגיה", "פזגז חשמל")
    val cellularOptions = listOf("לא נבחר", "019 מובייל", "Wecom", "פלאפון", "סלקום", "פרטנר", "גולן טלקום")
    val internetOptions = listOf("לא נבחר", "בזק", "סלקום", "פרטנר", "HOT", "אנלימיטד")
    val insuranceOptions = listOf("לא נבחר", "הראל", "הפניקס", "מגדל", "כלל", "ביטוח ישיר", "AIG")
    val streamingOptions = listOf("לא נבחר", "FreeTV", "סלקום TV", "פרטנר TV", "yes / STING", "HOT / NEXT")

    val goalValue = goalInput.toDoubleOrNull() ?: 0.0
    val thresholdValue = thresholdInput.toDoubleOrNull() ?: 0.0
    val canSave = goalValue >= 0.0 && thresholdValue >= 0.0
    val currentSignature = listOf(
        goalInput,
        thresholdInput,
        electricity,
        cellular,
        internet,
        insurance,
        streaming
    ).joinToString("|")
    val persistedSignature = listOf(
        currentGoal.toInt().takeIf { it > 0 }?.toString().orEmpty(),
        currentThreshold.toInt().takeIf { it > 0 }?.toString().orEmpty(),
        currentElectricity,
        currentCellular,
        currentInternet,
        currentInsurance,
        currentStreaming
    ).joinToString("|")
    val showSavedConfirmation = savedSignature != null && savedSignature == currentSignature
    val hasUnsavedChanges = currentSignature != persistedSignature && savedSignature != currentSignature

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("לצאת בלי לשמור?") },
            text = { Text("יש שינויים בהעדפות שעדיין לא נשמרו. אם תצא עכשיו הם לא יישמרו.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmation = false
                        onBackClick?.invoke()
                    },
                    modifier = Modifier.testTag("discard_preferences_changes")
                ) {
                    Text("צא בלי לשמור")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirmation = false },
                    modifier = Modifier.testTag("keep_editing_preferences")
                ) {
                    Text("המשך לערוך")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = financialSettingsPadding(),
        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBackClick != null) {
                    IconButton(
                        onClick = {
                            if (hasUnsavedChanges) {
                                showDiscardConfirmation = true
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.testTag("settings_back")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזרה")
                    }
                    Spacer(modifier = Modifier.size(FinancialDesignTokens.compactSpacing))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "העדפות חיסכון",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "מגדירים מה חשוב לך — המערכת ממשיכה לבדוק ולסנן הזדמנויות ברקע.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(FinancialDesignTokens.cardRadius)
            ) {
                Row(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Savings, contentDescription = null, tint = TechBluePrimary)
                    Spacer(modifier = Modifier.size(FinancialDesignTokens.cardSpacing))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("החיסכון שלך קודם", fontWeight = FontWeight.Bold)
                        Text(
                            "העדפות משפרות התאמה בלבד. סכום חיסכון מוצג רק אחרי אימות של הצעה ותנאים.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(FinancialDesignTokens.cardRadius)) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
                ) {
                    Text("יעדי חיסכון", fontWeight = FontWeight.Bold)
                    Text(
                        "היעדים עוזרים למקד את התמונה הפיננסית שלך — הם לא מבטיחים תוצאה ולא משנים את כללי האימות.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it.filter(Char::isDigit).take(7) },
                        label = { Text("יעד חיסכון חודשי") },
                        prefix = { Text("₪") },
                        supportingText = { Text("כמה היית רוצה לחסוך בכל חודש") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("monthly_savings_goal")
                    )
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { thresholdInput = it.filter(Char::isDigit).take(7) },
                        label = { Text("חיסכון חודשי שמעניין אותי") },
                        prefix = { Text("₪") },
                        supportingText = { Text("עוזר למקד את ההצגה בהזדמנויות משמעותיות עבורך") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("minimum_savings_threshold")
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(FinancialDesignTokens.cardRadius)) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
                ) {
                    Text("העדפות שירות", fontWeight = FontWeight.Bold)
                    Text(
                        "אם יש ספק שאתה מעדיף, אפשר לציין אותו. ההמלצה עדיין תתבסס על התאמה, מחיר ותנאים שניתן לאמת.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PreferencePicker("חשמל", electricity, electricityOptions, "preference_electricity") { electricity = it }
                    PreferencePicker("סלולר", cellular, cellularOptions, "preference_cellular") { cellular = it }
                    PreferencePicker("אינטרנט", internet, internetOptions, "preference_internet") { internet = it }
                    PreferencePicker("ביטוח", insurance, insuranceOptions, "preference_insurance") { insurance = it }
                    PreferencePicker("טלוויזיה / סטרימינג", streaming, streamingOptions, "preference_streaming") { streaming = it }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(FinancialDesignTokens.compactCardRadius)
            ) {
                Row(
                    modifier = Modifier.padding(FinancialDesignTokens.compactCardPadding),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.size(FinancialDesignTokens.cardSpacing))
                    Text(
                        "שמירת העדפות אינה מאשרת מעבר ספק או פעולה כספית. כל פעולה מול נותן שירות דורשת אישור מפורש שלך להצעה המדויקת.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.updatePreferences(
                        goal = goalValue,
                        electricity = electricity,
                        cellular = cellular,
                        internet = internet,
                        insurance = insurance,
                        streaming = streaming,
                        autoSwitch = false,
                        minThreshold = thresholdValue
                    )
                    savedSignature = currentSignature
                },
                enabled = canSave && hasUnsavedChanges,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_savings_preferences")
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("שמור העדפות")
            }
        }

        if (showSavedConfirmation) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("preferences_saved_confirmation"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(FinancialDesignTokens.compactCardRadius)
                ) {
                    Text(
                        "ההעדפות נשמרו. נשתמש בהן כדי למקד את ההזדמנויות שיוצגו לך.",
                        modifier = Modifier.padding(FinancialDesignTokens.compactCardPadding),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferencePicker(
    label: String,
    selected: String,
    options: List<String>,
    testTag: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
        ) {
            Text(selected.ifBlank { "לא נבחר" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    modifier = Modifier.testTag("${testTag}_option_$index")
                )
            }
        }
    }
}

private fun financialSettingsPadding() = PaddingValues(
    start = FinancialDesignTokens.screenHorizontalPadding,
    top = FinancialDesignTokens.screenTopPadding,
    end = FinancialDesignTokens.screenHorizontalPadding,
    bottom = FinancialDesignTokens.screenBottomNavigationClearance
)
