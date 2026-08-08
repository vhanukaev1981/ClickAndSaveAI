package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentGoal by viewModel.monthlySavingsGoal.collectAsState()
    val currentElectricity by viewModel.preferredElectricityProvider.collectAsState()
    val currentCellular by viewModel.preferredCellularProvider.collectAsState()
    val currentInternet by viewModel.preferredInternetProvider.collectAsState()
    val currentInsurance by viewModel.preferredInsuranceProvider.collectAsState()
    val currentStreaming by viewModel.preferredStreamingProvider.collectAsState()
    val currentThreshold by viewModel.minSavingsThreshold.collectAsState()

    var goalInput by remember(currentGoal) { mutableStateOf(currentGoal.toInt().toString()) }
    var thresholdInput by remember(currentThreshold) { mutableStateOf(currentThreshold.toInt().toString()) }
    var electricity by remember(currentElectricity) { mutableStateOf(currentElectricity) }
    var cellular by remember(currentCellular) { mutableStateOf(currentCellular) }
    var internet by remember(currentInternet) { mutableStateOf(currentInternet) }
    var insurance by remember(currentInsurance) { mutableStateOf(currentInsurance) }
    var streaming by remember(currentStreaming) { mutableStateOf(currentStreaming) }

    val electricityOptions = listOf("לא נבחר", "חברת החשמל", "אלקטרה פאוור", "סלקום אנרגיה", "פזגז חשמל")
    val cellularOptions = listOf("לא נבחר", "019 מובייל", "Wecom", "פלאפון", "סלקום", "פרטנר", "גולן טלקום")
    val internetOptions = listOf("לא נבחר", "בזק", "סלקום", "פרטנר", "HOT", "אנלימיטד")
    val insuranceOptions = listOf("לא נבחר", "הראל", "הפניקס", "מגדל", "כלל", "ביטוח ישיר", "AIG")
    val streamingOptions = listOf("לא נבחר", "FreeTV", "סלקום TV", "פרטנר TV", "yes / STING", "HOT / NEXT")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBackClick != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "חזרה")
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "העדפות חיסכון",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "עזור למערכת להבין מה חשוב לך יותר.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        "העדפות אלו עוזרות להתאים את החוויה והיעדים שלך. הן אינן מאשרות מעבר ספק או פעולה כספית — כל פעולה כזו דורשת אישור מפורש שלך.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("יעדי חיסכון", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = goalInput,
                        onValueChange = { goalInput = it.filter(Char::isDigit) },
                        label = { Text("יעד חיסכון חודשי (₪)") },
                        supportingText = { Text("יעד אישי שיעזור לך לעקוב אחרי ההתקדמות.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { thresholdInput = it.filter(Char::isDigit) },
                        label = { Text("סף חיסכון מועדף (₪ בחודש)") },
                        supportingText = { Text("הסכום שממנו הזדמנות חיסכון הופכת למעניינת עבורך.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ספקים מועדפים", fontWeight = FontWeight.Bold)
                    Text(
                        "אפשר לציין העדפות, אבל ההמלצה הסופית עדיין מבוססת על התאמה, מחיר ותנאים מאומתים — לא על העמלה של Click&SaveAI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PreferencePicker("חשמל", electricity, electricityOptions) { electricity = it }
                    PreferencePicker("סלולר", cellular, cellularOptions) { cellular = it }
                    PreferencePicker("אינטרנט", internet, internetOptions) { internet = it }
                    PreferencePicker("ביטוח", insurance, insuranceOptions) { insurance = it }
                    PreferencePicker("טלוויזיה / סטרימינג", streaming, streamingOptions) { streaming = it }
                }
            }
        }

        item {
            Button(
                onClick = {
                    viewModel.updatePreferences(
                        goal = goalInput.toDoubleOrNull() ?: 0.0,
                        electricity = electricity,
                        cellular = cellular,
                        internet = internet,
                        insurance = insurance,
                        streaming = streaming,
                        autoSwitch = false,
                        minThreshold = thresholdInput.toDoubleOrNull() ?: 0.0
                    )
                    Toast.makeText(context, "ההעדפות נשמרו", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("שמור העדפות")
            }
        }
    }
}

@Composable
private fun PreferencePicker(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(selected.ifBlank { "לא נבחר" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
