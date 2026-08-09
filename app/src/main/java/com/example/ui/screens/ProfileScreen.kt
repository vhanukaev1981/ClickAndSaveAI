package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.ui.MainViewModel
import com.example.ui.theme.FinancialDesignTokens
import com.example.ui.theme.TechBluePrimary

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showSignOutConfirmation by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(viewModel = viewModel, onBackClick = { showSettings = false })
        return
    }
    if (showPrivacy) {
        PrivacyConnectionsScreen(viewModel = viewModel, onBackClick = { showPrivacy = false })
        return
    }

    val session by viewModel.userSession.collectAsState()
    val monthlyGoal by viewModel.monthlySavingsGoal.collectAsState()
    val minSavingsThreshold by viewModel.minSavingsThreshold.collectAsState()

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmation = false },
            title = { Text("להתנתק מהחשבון?") },
            text = {
                Text(
                    "תצא מהחשבון במכשיר הזה. כדי לחזור לתמונה הפיננסית ולהעדפות שלך יהיה צורך להתחבר שוב."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmation = false
                        viewModel.signOut()
                    },
                    modifier = Modifier.testTag("confirm_profile_sign_out")
                ) {
                    Text("התנתק")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutConfirmation = false },
                    modifier = Modifier.testTag("cancel_profile_sign_out")
                ) {
                    Text("הישאר מחובר")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = financialScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "אני והעדפות",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "החשבון, יעדי החיסכון והשליטה בפרטיות במקום אחד.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(
                modifier = Modifier.testTag("profile_account_card"),
                shape = RoundedCornerShape(FinancialDesignTokens.cardRadius)
            ) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("החשבון שלי", fontWeight = FontWeight.Bold)
                    }

                    if (session.isAuthenticated) {
                        Text(
                            session.displayName.ifBlank { "משתמש מחובר" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            session.email.ifBlank { "כתובת דוא״ל לא זמינה" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showSignOutConfirmation = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_sign_out")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.size(FinancialDesignTokens.compactSpacing))
                            Text("התנתק")
                        }
                    } else {
                        Text(
                            "התחבר כדי לשמור את התמונה הפיננסית וההעדפות שלך.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onGoogleSignIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_sign_in")
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(modifier = Modifier.size(FinancialDesignTokens.compactSpacing))
                            Text("התחבר")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.testTag("profile_savings_preferences_card"),
                shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.cardSpacing)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Savings, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("יעדי החיסכון שלי", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (monthlyGoal > 0.0) "יעד חיסכון חודשי: ₪${monthlyGoal.toInt()}" else "עדיין לא הוגדר יעד חיסכון חודשי",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (minSavingsThreshold > 0.0) "הצג לי חיסכון משמעותי מ־₪${minSavingsThreshold.toInt()} בחודש" else "אפשר להגדיר מאיזה סכום חיסכון תרצה להתמקד",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showSettings = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_savings_preferences")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.size(FinancialDesignTokens.compactSpacing))
                        Text("ערוך העדפות")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.testTag("profile_privacy_card"),
                shape = RoundedCornerShape(FinancialDesignTokens.cardRadius)
            ) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("פרטיות ושליטה", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "המלצות מוצגות רק כשיש מספיק מידע כדי לאמת אותן. כל פעולה מול נותן שירות דורשת אישור מפורש שלך.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { showPrivacy = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_privacy_connections")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.size(FinancialDesignTokens.compactSpacing))
                        Text("ניהול פרטיות וחיבורים")
                    }
                }
            }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val gmailAuthorizationKeptForNavigationCompatibility = onRequestGmailAuthorization
}

@Composable
private fun PrivacyConnectionsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()
    val session by viewModel.userSession.collectAsState()
    var showDisconnectConfirmation by remember { mutableStateOf(false) }

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            title = { Text("לבטל את החיבור?") },
            text = {
                Text(
                    "המערכת תפסיק לקלוט מסמכים חדשים ממקור זה עד לחיבור מחדש. החשבונות שכבר זוהו יישארו בתמונה הפיננסית שלך."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectConfirmation = false
                        viewModel.disconnectGmail()
                    },
                    modifier = Modifier.testTag("confirm_disconnect_document_source")
                ) {
                    Text("בטל חיבור")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisconnectConfirmation = false },
                    modifier = Modifier.testTag("cancel_disconnect_document_source")
                ) {
                    Text("השאר מחובר")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("privacy_connections_screen"),
        contentPadding = financialScreenPadding(),
        verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.sectionSpacing)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("privacy_back")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "חזרה")
                }
                Spacer(modifier = Modifier.size(FinancialDesignTokens.compactSpacing))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "פרטיות וחיבורים",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "כאן אפשר לראות ולבטל מקורות מידע שחיברת.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.testTag("privacy_document_source_card"),
                shape = RoundedCornerShape(FinancialDesignTokens.cardRadius)
            ) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("מקור מסמכים", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (isGmailConnected) "מחובר${connectedEmail.ifBlank { session.email }.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""}" else "לא מחובר",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isGmailConnected) {
                            "החיבור הוא לקריאה בלבד ומשמש לאיתור חשבוניות ומסמכי חיוב רלוונטיים. אפשר לבטל אותו בכל עת."
                        } else {
                            "חיבור חדש מתבצע מתהליך ההצטרפות במסך הבית."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isGmailConnected) {
                        OutlinedButton(
                            onClick = { showDisconnectConfirmation = true },
                            enabled = !isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("disconnect_document_source")
                        ) {
                            Text(if (isSyncing) "מנתק…" else "בטל את החיבור")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.testTag("privacy_control_explainer"),
                shape = RoundedCornerShape(FinancialDesignTokens.cardRadius),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(FinancialDesignTokens.cardPadding),
                    verticalArrangement = Arrangement.spacedBy(FinancialDesignTokens.compactSpacing)
                ) {
                    Text("השליטה נשארת אצלך", fontWeight = FontWeight.Bold)
                    Text(
                        "המערכת אינה מבצעת מעבר ספק או פעולה כספית ללא אישור מפורש שלך, ואינה מעבירה לנותן שירות את תוכן תיבת הדואר או את תמונת ההוצאות המלאה שלך.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun financialScreenPadding() = PaddingValues(
    start = FinancialDesignTokens.screenHorizontalPadding,
    top = FinancialDesignTokens.screenTopPadding,
    end = FinancialDesignTokens.screenHorizontalPadding,
    bottom = FinancialDesignTokens.screenBottomNavigationClearance
)
