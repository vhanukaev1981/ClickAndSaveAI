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
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.example.BuildConfig
import com.example.data.repository.AuthState
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.gmailConnectionOrNull
import com.example.ui.MainViewModel
import com.example.ui.PrivacyOperationUiState
import com.example.ui.components.V3SectionHeader
import com.example.ui.theme.TechBluePrimary

private const val CONFIRM_DISCONNECT_GMAIL = "DISCONNECT_GMAIL"
private const val CONFIRM_DELETE_IMPORTED_DATA = "DELETE_IMPORTED_FINANCIAL_DATA"
private const val CONFIRM_DELETE_ACCOUNT = "DELETE_ACCOUNT"

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit,
    onOpenActivity: () -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val privacyOperationState by viewModel.privacyOperationState.collectAsState()
    var pendingConfirmation by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("פרופיל", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "החיבורים, הפרטיות, הפעילות והאבטחה שלך במקום אחד.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { V3SectionHeader("חיבור ונתונים") }
        item {
            GmailAuthorityCard(
                financialSyncState = financialSyncState,
                authState = authState,
                onRequestGmailAuthorization = onRequestGmailAuthorization
            )
        }

        item { V3SectionHeader("פרטיות והרשאות") }
        item {
            PrivacyAuthorityCard(
                authState = authState,
                financialSyncState = financialSyncState,
                privacyOperationState = privacyOperationState,
                onDisconnectGmail = { pendingConfirmation = CONFIRM_DISCONNECT_GMAIL },
                onDeleteImportedData = { pendingConfirmation = CONFIRM_DELETE_IMPORTED_DATA },
                onDeleteAccount = { pendingConfirmation = CONFIRM_DELETE_ACCOUNT }
            )
        }

        item { V3SectionHeader("פעילות והתראות") }
        item { ActivityNotificationsCard(onOpenActivity) }

        item { V3SectionHeader("חשבון ואבטחה") }
        item {
            AccountCard(
                authState = authState,
                onGoogleSignIn = onGoogleSignIn,
                onSignOut = viewModel::signOut
            )
        }
        item { AppAboutCard() }
    }

    when (pendingConfirmation) {
        CONFIRM_DISCONNECT_GMAIL -> PrivacyConfirmationDialog(
            title = "ניתוק Gmail",
            text = "הפעולה תפסיק ייבוא עתידי מ-Gmail ותסיר את הרשאת הקריאה. היא לא מוחקת את הנתונים שכבר יובאו, לא מוציאה אותך מהחשבון ולא מוחקת את החשבון.",
            confirmLabel = "נתק Gmail",
            onConfirm = { pendingConfirmation = null; viewModel.disconnectGmail() },
            onDismiss = { pendingConfirmation = null }
        )
        CONFIRM_DELETE_IMPORTED_DATA -> PrivacyConfirmationDialog(
            title = "מחיקת נתונים מיובאים",
            text = "הפעולה תמחק את הנתונים הפיננסיים שיובאו מ-Gmail ואת הנתונים שנוצרו מהם. החשבון יישאר קיים ו-Gmail יישאר מחובר, ולכן סריקה עתידית יכולה ליצור נתונים חדשים.",
            confirmLabel = "מחק נתונים מיובאים",
            onConfirm = { pendingConfirmation = null; viewModel.deleteImportedFinancialData() },
            onDismiss = { pendingConfirmation = null }
        )
        CONFIRM_DELETE_ACCOUNT -> PrivacyConfirmationDialog(
            title = "מחיקת חשבון",
            text = "הפעולה תמחק את חשבון Click & Save AI ואת הנתונים שבבעלות החשבון ותסיר את חיבור Gmail. אם לא נוכל להשלים את כל שלבי המחיקה, נציג שגיאה ונאפשר לנסות שוב.",
            confirmLabel = "מחק חשבון",
            onConfirm = { pendingConfirmation = null; viewModel.deleteAccount() },
            onDismiss = { pendingConfirmation = null }
        )
    }
}

@Composable
private fun ActivityNotificationsCard(onOpenActivity: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, null, tint = TechBluePrimary)
                Spacer(Modifier.size(8.dp))
                Text("פעילות", fontWeight = FontWeight.Bold)
            }
            Text("יומן הפעילות הוא מסך משני ומציג רק אירועים שקיימים במידע הזמין.")
            Text(
                "הרשאות התראות מנוהלות על ידי Android; אין כאן הבטחה להתראה שלא נוצרה בפועל.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth().testTag("open_activity")) {
                Text("פתח פעילות")
            }
        }
    }
}

@Composable
private fun AccountCard(authState: AuthState, onGoogleSignIn: () -> Unit, onSignOut: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, null, tint = TechBluePrimary)
                Spacer(Modifier.size(8.dp))
                Text("חשבון", fontWeight = FontWeight.Bold)
            }
            when (authState) {
                AuthState.Loading -> Text("מצב החשבון עדיין נטען.")
                is AuthState.Error -> Text("לא הצלחנו לטעון את מצב החשבון כרגע. נסו שוב בעוד רגע.")
                AuthState.Idle -> {
                    Text("לא מחובר לחשבון.")
                    Button(onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Login, null)
                        Spacer(Modifier.size(6.dp))
                        Text("התחבר עם Google")
                    }
                }
                is AuthState.Authenticated -> {
                    val session = authState.session
                    Text(session.displayName.ifBlank { "שם לא זמין" }, fontWeight = FontWeight.Bold)
                    Text(session.email.ifBlank { "כתובת דוא״ל לא זמינה" })
                    Text("התחברות לחשבון וחיבור Gmail הם שני מצבים נפרדים.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null)
                        Spacer(Modifier.size(6.dp))
                        Text("יציאה מהחשבון")
                    }
                    Text("יציאה מהחשבון אינה מנתקת Gmail ואינה מוחקת את החשבון.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun GmailAuthorityCard(
    financialSyncState: FinancialSyncState,
    authState: AuthState,
    onRequestGmailAuthorization: () -> Unit
) {
    val connection = financialSyncState.gmailConnectionOrNull
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, tint = TechBluePrimary)
                Spacer(Modifier.size(8.dp))
                Text("Gmail", fontWeight = FontWeight.Bold)
            }
            when (financialSyncState) {
                FinancialSyncState.Unauthenticated -> Text("יש להתחבר לחשבון כדי לבדוק את מצב Gmail.")
                FinancialSyncState.CheckingConnection, FinancialSyncState.Recovering -> Text("בודקים את מצב החיבור ל-Gmail.")
                FinancialSyncState.Disconnected -> Text("Gmail אינו מחובר.")
                is FinancialSyncState.Failed -> Text("לא הצלחנו לבדוק את מצב Gmail כרגע. נסו שוב בעוד רגע.")
                is FinancialSyncState.Ready, is FinancialSyncState.Partial -> {
                    if (connection == null) Text("מצב Gmail לא ידוע כרגע.")
                    else if (connection.connected) {
                        Text("Gmail מחובר", fontWeight = FontWeight.SemiBold)
                        if (connection.email.isNotBlank()) Text(connection.email)
                        Text("הרשאה: קריאה בלבד")
                    } else Text("Gmail אינו מחובר.")
                }
            }
            if (authState is AuthState.Authenticated && financialSyncState == FinancialSyncState.Disconnected) {
                OutlinedButton(onClick = onRequestGmailAuthorization, modifier = Modifier.fillMaxWidth().testTag("connect_gmail")) {
                    Text("חבר Gmail")
                }
            }
            Text("ניתוק Gmail הוא פעולה נפרדת מיציאה, ממחיקת נתונים וממחיקת החשבון.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PrivacyAuthorityCard(
    authState: AuthState,
    financialSyncState: FinancialSyncState,
    privacyOperationState: PrivacyOperationUiState,
    onDisconnectGmail: () -> Unit,
    onDeleteImportedData: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val authenticated = authState is AuthState.Authenticated
    val working = privacyOperationState is PrivacyOperationUiState.Working
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PrivacyTip, null, tint = TechBluePrimary)
                Spacer(Modifier.size(8.dp))
                Text("פרטיות ושליטה", fontWeight = FontWeight.Bold)
            }
            val connection = financialSyncState.gmailConnectionOrNull
            Text(when {
                connection?.connected == true -> "Gmail מחובר לקריאה בלבד."
                financialSyncState == FinancialSyncState.Disconnected -> "אין כרגע ייבוא פעיל מ-Gmail."
                else -> "מצב Gmail עדיין לא ידוע במלואו."
            })
            when (privacyOperationState) {
                PrivacyOperationUiState.Idle -> Unit
                is PrivacyOperationUiState.Working -> Text("הפעולה מתבצעת. נציג הצלחה רק לאחר שתושלם.", style = MaterialTheme.typography.bodySmall)
                is PrivacyOperationUiState.Success -> Text(privacyOperationState.message, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                is PrivacyOperationUiState.Error -> Text("לא הצלחנו להשלים את הפעולה. אפשר לנסות שוב.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            if (authenticated) {
                OutlinedButton(onClick = onDisconnectGmail, enabled = !working, modifier = Modifier.fillMaxWidth().testTag("disconnect_gmail")) { Text("נתק Gmail") }
                Text("מפסיק ייבוא עתידי ומסיר את הרשאת Gmail; אינו מוחק נתונים שכבר יובאו.", style = MaterialTheme.typography.labelSmall)
                OutlinedButton(onClick = onDeleteImportedData, enabled = !working, modifier = Modifier.fillMaxWidth().testTag("delete_imported_data")) { Text("מחק נתונים מיובאים") }
                Text("מוחק נתונים מיובאים ונתונים שנוצרו מהם בלבד; החשבון ו-Gmail נשמרים.", style = MaterialTheme.typography.labelSmall)
                Button(onClick = onDeleteAccount, enabled = !working, modifier = Modifier.fillMaxWidth().testTag("delete_account")) { Text("מחק חשבון") }
                Text("מוחק את החשבון, הנתונים וחיבור Gmail לאחר אישור מפורש.", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("יש להתחבר לחשבון כדי לבצע פעולות פרטיות.", style = MaterialTheme.typography.bodySmall)
            }
            Text("ניתוק Gmail, מחיקת נתונים ומחיקת חשבון הן פעולות שונות.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AppAboutCard() {
    Card(shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Info, null, tint = TechBluePrimary)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Click & Save AI", fontWeight = FontWeight.Bold)
                Text("גרסה ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrivacyConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
