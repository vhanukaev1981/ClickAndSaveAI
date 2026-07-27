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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.InvoiceItem
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberDeal
import com.example.ui.theme.EmeraldSavings
import com.example.ui.theme.TechBluePrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicesScreen(
    viewModel: MainViewModel,
    onOpenReceiptScan: () -> Unit
) {
    val context = LocalContext.current
    val invoices by viewModel.invoices.collectAsState()
    val totalMonthlySavingsPotential by viewModel.totalMonthlySavingsPotential.collectAsState()
    val totalMonthlyCost by viewModel.totalMonthlyCost.collectAsState()
    val totalAnnualSavingsPotential = totalMonthlySavingsPotential * 12

    var selectedCategory by remember { mutableStateOf("הכל") }
    var selectedInvoiceForSwitch by remember { mutableStateOf<InvoiceItem?>(null) }
    var showAddInvoiceDialog by remember { mutableStateOf(false) }

    val categories = listOf("הכל", "חשמל", "סלולר", "אינטרנט", "ביטוח", "טלוויזיה")

    val filteredInvoices = if (selectedCategory == "הכל") {
        invoices
    } else {
        invoices.filter { it.category.contains(selectedCategory, ignoreCase = true) }
    }

    var selectedInvoiceForPayment by remember { mutableStateOf<InvoiceItem?>(null) }

    selectedInvoiceForPayment?.let { invoice ->
        ProviderExternalPaymentModal(
            invoice = invoice,
            onDismiss = { selectedInvoiceForPayment = null }
        )
    }

    selectedInvoiceForSwitch?.let { invoice ->
        SwitchRequestModal(
            invoice = invoice,
            onDismiss = { selectedInvoiceForSwitch = null },
            onSubmit = { fullName, phone, email ->
                viewModel.requestProviderSwitch(invoice)
                Toast.makeText(
                    context,
                    "בקשת מעבר ל-${invoice.recommendedAlternative} נשלחה בהצלחה! נציג יצור איתך קשר.",
                    Toast.LENGTH_LONG
                ).show()
                selectedInvoiceForSwitch = null
            }
        )
    }

    if (showAddInvoiceDialog) {
        AddInvoiceModal(
            onDismiss = { showAddInvoiceDialog = false },
            onAddInvoice = { provider, category, monthlyCost, altProvider, altCost, savings ->
                viewModel.addManualInvoice(
                    providerName = provider,
                    category = category,
                    monthlyCost = monthlyCost,
                    recommendedAlternative = altProvider,
                    alternativeCost = altCost,
                    savings = savings
                )
                Toast.makeText(context, "חשבונית נוספה ופוענחה בהצלחה!", Toast.LENGTH_SHORT).show()
                showAddInvoiceDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("invoices_screen")
    ) {
        // Top Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "חשבוניות הבית והוצאות קבועות",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "ריכוז חשבוניות שנרדפו מ-Gmail לחשמל, תקשורת וביטוח",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showAddInvoiceDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("הוסף חשבונית", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Summary Stats Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TechBluePrimary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("סך הוצאה חודשית", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₪${String.format("%.2f", totalMonthlyCost)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }

                        Divider(modifier = Modifier.height(30.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("חיסכון חודשי אפשרי", style = MaterialTheme.typography.labelSmall, color = EmeraldSavings)
                            Text("₪${String.format("%.2f", totalMonthlySavingsPotential)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = EmeraldSavings))
                        }

                        Divider(modifier = Modifier.height(30.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("פוטנציאל שנתי", style = MaterialTheme.typography.labelSmall, color = EmeraldSavings)
                            Text("₪${String.format("%.2f", totalAnnualSavingsPotential)}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = EmeraldSavings))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category Filter Chips
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

        // List of Invoices
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (filteredInvoices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "לא נמצאו חשבוניות בקטגוריה זו",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredInvoices) { invoice ->
                    InvoiceDetailCard(
                        invoice = invoice,
                        onSwitchClick = { selectedInvoiceForSwitch = invoice },
                        onPayClick = { selectedInvoiceForPayment = invoice },
                        onDeleteClick = { viewModel.deleteInvoice(invoice.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun InvoiceDetailCard(
    invoice: InvoiceItem,
    onSwitchClick: () -> Unit,
    onPayClick: () -> Unit,
    onDeleteClick: () -> Unit
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
                    val icon = when (invoice.category) {
                        "חשמל" -> Icons.Default.ElectricBolt
                        "סלולר" -> Icons.Default.Smartphone
                        "אינטרנט" -> Icons.Default.Wifi
                        "ביטוח" -> Icons.Default.Security
                        else -> Icons.Default.ReceiptLong
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(TechBluePrimary.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = TechBluePrimary, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = invoice.providerName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "קטגוריה: ${invoice.category} • חשבון: ${invoice.accountNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (invoice.isSwitchRequested) TechBluePrimary.copy(alpha = 0.15f) else EmeraldSavings.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = invoice.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (invoice.isSwitchRequested) TechBluePrimary else EmeraldSavings
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "תשלום חודשי נוכחי:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₪${String.format("%.2f", invoice.monthlyCost)}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "אלטרנטיבה מוזלת AI:",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSavings
                    )
                    Text(
                        text = "₪${String.format("%.2f", invoice.alternativeMonthlyCost)} / חודש",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = EmeraldSavings
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = AmberDeal.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = AmberDeal, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = invoice.recommendedAlternative,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "חיסכון שנתי צפוי: ₪${String.format("%.2f", invoice.potentialAnnualSavings)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldSavings
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "מחק חשבונית", tint = MaterialTheme.colorScheme.error)
                }

                OutlinedButton(
                    onClick = onPayClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("לתשלום אצל הספק", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onSwitchClick,
                    enabled = !invoice.isSwitchRequested,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (invoice.isSwitchRequested) MaterialTheme.colorScheme.surfaceVariant else EmeraldSavings,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (invoice.isSwitchRequested) "בטיפול" else "⚡ מעבר מוזל",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderExternalPaymentModal(
    invoice: InvoiceItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("מעבר מאובטח לתשלום אצל הספק", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Surface(
                    color = TechBluePrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "ספק: ${invoice.providerName}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "מספר חשבון: ${invoice.accountNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "לתשלום: ₪${String.format("%.2f", invoice.monthlyCost)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldSavings, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔒 מעביר אותך לעמוד הסליקה הרשמי של ${invoice.providerName}. האפליקציה אינה שומרת פרטי אשראי.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val url = com.example.data.local.IsraeliMarketData.getPaymentUrl(invoice.providerName)
                    Toast.makeText(
                        context,
                        "🔒 מעביר אותך לעמוד הסליקה הרשמי של ${invoice.providerName}. האפליקציה אינה שומרת פרטי אשראי.",
                        Toast.LENGTH_LONG
                    ).show()
                    try {
                        uriHandler.openUri(url)
                    } catch (e: Exception) {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TechBluePrimary)
            ) {
                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("המשך לעמוד התשלום הרשמי של הספק")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}
