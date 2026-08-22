package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.components.V3ScreenHeader
import com.example.ui.components.V3SectionHeader
import com.example.ui.components.V3SettingsGroup
import com.example.ui.components.V3SettingsRow
import com.example.ui.theme.V3Border

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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            V3ScreenHeader(
                eyebrow = "החשבון שלך",
                title = "פרופיל",
                subtitle = "החיבורים, הפרטיות, הפעילות והאבטחה שלך במקום אחד."
            )
        }

        item { V3SectionHeader("חיבור ונתונים") }
        item {
            GmailAuthorityGroup(
                financialSyncState = financialSyncState,
                authState = authState,
                onRequestGmailAuthorization = onRequestGmailAuthorization
            )
        }

        item { V3SectionHeader("פרטיות והרשאות") }
        item {
            PrivacyAuthorityGroup(
                authState = authState,
                financialSyncState = financialSyncState,
                privacyOperationState = privacyOperationState,
                onDisconnectGmail = { pendingConfirmation = CONFIRM_DISCONNECT_GMAIL },
                onDeleteImportedData = { pendingConfirmation = CONFIRM_DELETE_IMPORTED_DATA },
                onDeleteAccount = { pendingConfirmation = CONFIRM_DELETE_ACCOUNT }
            )
        }

        item { V3SectionHeader("פעילות והתראות") }
        item { ActivityNotificationsGroup(onOpenActivity) }

        item { V3SectionHeader("חשבון ואבטחה") }
        item {
            AccountGroup(
                authState = authState,
                onGoogleSignIn = onGoogleSignIn,
                onSignOut = viewModel::signOut
            )
        }
        item { AppAboutGroup() }
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
private fun GmailAuthorityGroup(
    financialSyncState: FinancialSyncState,
    authState: AuthState,
    onRequestGmailAuthorization: () -> Unit
) {
    val connection = financialSyncState.gmailConnectionOrNull
    val status = when (financialSyncState) {
        FinancialSyncState.Unauthenticated -> "יש להתחבר לחשבון כדי לבדוק את מצב Gmail."
        FinancialSyncState.CheckingConnection, FinancialSyncState.Recovering -> "בודקים את מצב החיבור ל-Gmail."
        FinancialSyncState.Disconnected -> "Gmail אינו מחובר."
        is FinancialSyncState.Failed -> "לא הצלחנו לבדוק את מצב Gmail כרגע. נסו שוב בעוד רגע."
        is FinancialSyncState.Ready, is FinancialSyncState.Partial -> when {
            connection == null -> "מצב Gmail לא ידוע כרגע."
            connection.connected && connection.email.isNotBlank() -> "Gmail מחובר · ${connection.email}"
            connection.connected -> "Gmail מחובר"
            else -> "Gmail אינו מחובר."
        }
    }

    V3SettingsGroup(title = "") {
        V3SettingsRow(
            title = "Gmail",
            subtitle = "$status\nניתוק Gmail הוא פעולה נפרדת מיציאה, ממחיקת נתונים וממחיקת החשבון.",
            icon = Icons.Default.Link,
            trailingText = if (connection?.connected == true) "קריאה בלבד" else null
        )
        if (authState is AuthState.Authenticated && financialSyncState == FinancialSyncState.Disconnected) {
            HorizontalDivider(color = V3Border)
            V3SettingsRow(
                title = "חבר Gmail",
                subtitle = "הרשאת Gmail נשארת קריאה בלבד.",
                icon = Icons.AutoMirrored.Filled.Login,
                onClick = onRequestGmailAuthorization,
                modifier = Modifier.testTag("connect_gmail")
            )
        }
    }
}

@Composable
private fun PrivacyAuthorityGroup(
    authState: AuthState,
    financialSyncState: FinancialSyncState,
    privacyOperationState: PrivacyOperationUiState,
    onDisconnectGmail: () -> Unit,
    onDeleteImportedData: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val authenticated = authState is AuthState.Authenticated
    val working = privacyOperationState is PrivacyOperationUiState.Working
    val connection = financialSyncState.gmailConnectionOrNull
    val connectionText = when {
        connection?.connected == true -> "Gmail מחובר לקריאה בלבד."
        financialSyncState == FinancialSyncState.Disconnected -> "אין כרגע ייבוא פעיל מ-Gmail."
        else -> "מצב Gmail עדיין לא ידוע במלואו."
    }
    val operationText = when (privacyOperationState) {
        PrivacyOperationUiState.Idle -> null
        is PrivacyOperationUiState.Working -> "הפעולה מתבצעת. נציג הצלחה רק לאחר שתושלם."
        is PrivacyOperationUiState.Success -> privacyOperationState.message
        is PrivacyOperationUiState.Error -> "לא הצלחנו להשלים את הפעולה. אפשר לנסות שוב."
    }

    V3SettingsGroup(title = "") {
        V3SettingsRow(
            title = "פרטיות ושליטה",
            subtitle = listOfNotNull(connectionText, operationText).joinToString("\n"),
            icon = Icons.Default.PrivacyTip
        )
        if (authenticated) {
            HorizontalDivider(color = V3Border)
            V3SettingsRow(
                title = "נתק Gmail",
                subtitle = "מפסיק ייבוא עתידי ומסיר את הרשאת Gmail; אינו מוחק נתונים שכבר יובאו.",
                icon = Icons.Default.LinkOff,
                onClick = if (working) null else onDisconnectGmail,
                modifier = Modifier.testTag("disconnect_gmail")
            )
            HorizontalDivider(color = V3Border)
            V3SettingsRow(
                title = "מחק נתונים מיובאים",
                subtitle = "מוחק נתונים מיובאים ונתונים שנוצרו מהם בלבד; החשבון ו-Gmail נשמרים.",
                icon = Icons.Default.DeleteSweep,
                onClick = if (working) null else onDeleteImportedData,
                modifier = Modifier.testTag("delete_imported_data")
            )
            HorizontalDivider(color = V3Border)
            V3SettingsRow(
                title = "מחק חשבון",
                subtitle = "מוחק את החשבון, הנתונים וחיבור Gmail לאחר אישור מפורש.",
                icon = Icons.Default.DeleteForever,
                onClick = if (working) null else onDeleteAccount,
                destructive = true,
                modifier = Modifier.testTag("delete_account")
            )
        } else {
            HorizontalDivider(color = V3Border)
            V3SettingsRow(
                title = "נדרשת התחברות",
                subtitle = "יש להתחבר לחשבון כדי לבצע פעולות פרטיות.",
                icon = Icons.Default.PrivacyTip
            )
        }
    }
}

@Composable
private fun ActivityNotificationsGroup(onOpenActivity: () -> Unit) {
    V3SettingsGroup(title = "") {
        V3SettingsRow(
            title = "פעילות",
            subtitle = "יומן הפעילות מציג רק אירועים שקיימים במידע הזמין. הרשאות התראות מנוהלות על ידי Android.",
            icon = Icons.Default.History,
            trailingText = "פתח",
            onClick = onOpenActivity,
            modifier = Modifier.testTag("open_activity")
        )
    }
}

@Composable
private fun AccountGroup(authState: AuthState, onGoogleSignIn: () -> Unit, onSignOut: () -> Unit) {
    V3SettingsGroup(title = "") {
        when (authState) {
            AuthState.Loading -> V3SettingsRow("חשבון", "מצב החשבון עדיין נטען.", Icons.Default.AccountCircle)
            is AuthState.Error -> V3SettingsRow("חשבון", "לא הצלחנו לטעון את מצב החשבון כרגע. נסו שוב בעוד רגע.", Icons.Default.AccountCircle)
            AuthState.Idle -> V3SettingsRow(
                title = "התחבר עם Google",
                subtitle = "התחברות לחשבון וחיבור Gmail הם שני מצבים נפרדים.",
                icon = Icons.AutoMirrored.Filled.Login,
                trailingText = "התחבר",
                onClick = onGoogleSignIn
            )
            is AuthState.Authenticated -> {
                val session = authState.session
                V3SettingsRow(
                    title = session.displayName.ifBlank { "שם לא זמין" },
                    subtitle = "${session.email.ifBlank { "כתובת דוא״ל לא זמינה" }}\nהתחברות לחשבון וחיבור Gmail הם שני מצבים נפרדים.",
                    icon = Icons.Default.AccountCircle
                )
                HorizontalDivider(color = V3Border)
                V3SettingsRow(
                    title = "יציאה מהחשבון",
                    subtitle = "יציאה מהחשבון אינה מנתקת Gmail ואינה מוחקת את החשבון.",
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = onSignOut
                )
            }
        }
    }
}

@Composable
private fun AppAboutGroup() {
    V3SettingsGroup(title = "") {
        V3SettingsRow(
            title = "Click & Save AI",
            subtitle = "גרסה ${BuildConfig.VERSION_NAME}",
            icon = Icons.Default.Info
        )
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
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } }
    )
}
