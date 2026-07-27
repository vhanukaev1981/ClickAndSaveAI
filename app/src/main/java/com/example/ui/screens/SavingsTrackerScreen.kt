package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SavingsRecord
import com.example.ui.MainViewModel
import com.example.ui.theme.AiVioletPrimary
import com.example.ui.theme.EmeraldSavings

@Composable
fun SavingsTrackerScreen(
    viewModel: MainViewModel,
    onOpenReceiptScan: () -> Unit
) {
    val totalSavings by viewModel.totalSavings.collectAsState()
    val savingsRecords by viewModel.savingsRecords.collectAsState()
    val receiptScanResult by viewModel.receiptScanResult.collectAsState()
    val isReceiptScanning by viewModel.isReceiptScanning.collectAsState()

    var showAddSavingsDialog by remember { mutableStateOf(false) }
    var showExportReportDialog by remember { mutableStateOf(false) }

    val savingsGoal = 2000.00
    val progress = (totalSavings / savingsGoal).coerceIn(0.0, 1.0).toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("savings_screen")
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "יומן אנליטיקה וחיסכון מצטבר",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "מעקב בזמן אמת אחר הכסף שנחסך באמצעות Click & Save AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Total Savings Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldSavings)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Savings, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "סך חיסכון כולל",
                                    style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.9f))
                                )
                            }

                            Surface(
                                color = Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "חוסך מצטיין - רמה 3",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "₪${String.format("%.2f", totalSavings)}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Savings Goal Progress
                        Text(
                            text = "יעד חיסכון שנתי: ₪${String.format("%.2f", totalSavings)} / ₪${String.format("%.2f", savingsGoal)}",
                            style = MaterialTheme.typography.labelMedium.copy(color = Color.White)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            // Quick Actions Buttons Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showAddSavingsDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("savings_log_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AiVioletPrimary)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("תיעוד חיסכון")
                        }

                        OutlinedButton(
                            onClick = onOpenReceiptScan,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("savings_receipt_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = EmeraldSavings)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("סרוק קבלה")
                        }
                    }

                    // Prominent Export Monthly Savings Report Button
                    Button(
                        onClick = { showExportReportDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_export_monthly_report"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSavings)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📊 ייצוא דו\"ח חיסכון חודשי",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            // History Header
            item {
                Text(
                    text = "היסטוריית חיסכון אחרונה",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            items(savingsRecords, key = { it.id }) { record ->
                SavingsRecordCard(record = record)
            }
        }
    }

    if (showAddSavingsDialog) {
        AddSavingsRecordDialog(
            onDismiss = { showAddSavingsDialog = false },
            onAdd = { title, store, amount, category, note ->
                viewModel.addSavingsRecord(title, store, amount, category, note)
                showAddSavingsDialog = false
            }
        )
    }

    if (showExportReportDialog) {
        val reportText = generateMonthlySavingsReport(totalSavings, savingsRecords, savingsGoal)
        ExportSavingsReportDialog(
            reportText = reportText,
            onDismiss = { showExportReportDialog = false }
        )
    }

    if (receiptScanResult != null) {
        val result = receiptScanResult!!
        AlertDialog(
            onDismissRequest = { viewModel.clearReceiptResult() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = EmeraldSavings)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ניתוח AI לחשבונית/קבלה")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("חנות / ספק: ${result.storeName}", fontWeight = FontWeight.Bold)
                    Text("סכום ששולם: ₪${String.format("%.2f", result.totalAmount)}")
                    Text("חיסכון שזוהה: ₪${String.format("%.2f", result.estimatedSavings)}", color = EmeraldSavings, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("סיכום פריטים: ${result.itemSummary}", style = MaterialTheme.typography.bodySmall)
                    Text("טיפ להחזר כספי: ${result.cashbackTips}", style = MaterialTheme.typography.bodySmall, color = AiVioletPrimary)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearReceiptResult() },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSavings)
                ) {
                    Text("נשמר בהיסטוריה!")
                }
            }
        )
    }
}

@Composable
fun SavingsRecordCard(record: SavingsRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(EmeraldSavings.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Savings, contentDescription = null, tint = EmeraldSavings)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${record.storeName} • ${record.category}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (record.note.isNotBlank()) {
                        Text(
                            text = record.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = "+₪${String.format("%.2f", record.amountSaved)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = EmeraldSavings
                )
            )
        }
    }
}

@Composable
fun AddSavingsRecordDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, store: String, amount: Double, category: String, note: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("חשמלי") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("תיעוד חיסכון בקנייה") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("תיאור הקנייה / הקופון") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = store,
                    onValueChange = { store = it },
                    label = { Text("שם החנות / הספק") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("סכום נחסך (בש\"ח ₪)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("הערות / קוד קופון ששמש") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amount > 0) {
                        onAdd(title, store.ifBlank { "חנות" }, amount, category, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSavings)
            ) {
                Text("שמור חיסכון")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

// Helper to create sample receipt bitmap for scanner simulation
fun createSampleReceiptBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawRect(0f, 0f, 400f, 500f, paint)

    paint.apply {
        color = android.graphics.Color.BLACK
        textSize = 24f
        isFakeBoldText = true
    }
    canvas.drawText("SUPERMARKET RECEIPT", 60f, 60f, paint)

    paint.apply {
        textSize = 18f
        isFakeBoldText = false
    }
    canvas.drawText("Item 1: Organic Milk  $4.50", 40f, 120f, paint)
    canvas.drawText("Item 2: Fresh Apples   $6.20", 40f, 160f, paint)
    canvas.drawText("Item 3: Coffee Beans   $14.99", 40f, 200f, paint)
    canvas.drawText("PROMO DISCOUNT:      -$6.50", 40f, 260f, paint)
    canvas.drawText("TOTAL PAID:          $19.19", 40f, 320f, paint)

    return bitmap
}

fun generateMonthlySavingsReport(
    totalSavings: Double,
    records: List<SavingsRecord>,
    savingsGoal: Double = 2000.0
): String {
    val currentDate = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
    val sb = StringBuilder()
    sb.appendLine("📊 דו\"ח חיסכון חודשי - Click & Save AI")
    sb.appendLine("תאריך הפקה: $currentDate")
    sb.appendLine("----------------------------------------")
    sb.appendLine("💰 סך חיסכון מצטבר: ₪${String.format("%.2f", totalSavings)}")
    val pct = if (savingsGoal > 0) ((totalSavings / savingsGoal) * 100).toInt() else 0
    sb.appendLine("🎯 יעד חיסכון שנתי: ₪${String.format("%.2f", totalSavings)} / ₪${String.format("%.2f", savingsGoal)} ($pct%)")
    sb.appendLine()
    sb.appendLine("📋 פירוט פעולות חיסכון וחשבונות שנחסכו החודש:")

    if (records.isEmpty()) {
        sb.appendLine("• חשמלי: מעבר לאלקטרה פאוור במסלול הייטק | נחסך: ₪85.00/חודש")
        sb.appendLine("• תקשורת: ניוד 3 קווי סלולר ל-019 מובייל | נחסך: ₪60.00/חודש")
        sb.appendLine("• סיבים: הוזלת אינטרנט לסלקום פייבר | נחסך: ₪30.00/חודש")
        sb.appendLine("• ביטוח: ביטול כפילויות בהר הביטוח | נחסך: ₪120.00/חודש")
    } else {
        records.forEachIndexed { idx, item ->
            sb.appendLine("${idx + 1}. ${item.title} (${item.storeName})")
            sb.appendLine("   קטגוריה: ${item.category} | חיסכון: ₪${String.format("%.2f", item.amountSaved)}")
            if (item.note.isNotBlank()) {
                sb.appendLine("   הערה: ${item.note}")
            }
        }
    }

    sb.appendLine()
    sb.appendLine("💡 המלצת מנוע ה-AI להמשך חיסכון:")
    sb.appendLine("המשך מעקב וסריקה אוטומטית של חשבוניות יבטיח שמירה על מחיר מוזל ללא התייקרויות פתאומיות!")
    sb.appendLine("----------------------------------------")
    sb.appendLine("נוצר באופן אוטומטי ע\"י Click & Save AI 🤖")
    return sb.toString()
}

@Composable
fun ExportSavingsReportDialog(
    reportText: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Assessment, contentDescription = null, tint = EmeraldSavings)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ייצוא דו\"ח חיסכון חודשי",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "סיכום מפורט של פעולות החיסכון שנצברו בחודש האחרון בפורמט טקסט נוח לשיתוף והדפסה:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    LazyColumn(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        item {
                            SelectionContainer {
                                Text(
                                    text = reportText,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "דו\"ח חיסכון חודשי - Click & Save AI")
                            putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "שתף דו\"ח חיסכון"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSavings)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("שתף דו\"ח")
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(reportText))
                        Toast.makeText(context, "📋 הדו\"ח הועתק בהצלחה ללוח!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("העתק")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

