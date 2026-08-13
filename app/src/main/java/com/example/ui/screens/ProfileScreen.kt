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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.AuthState
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.gmailConnectionOrNull
import com.example.ui.MainViewModel
import com.example.ui.theme.TechBluePrimary

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    val authState by viewModel.authState.collectAsState()
    val financialSyncState by viewModel.financialSyncState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("אני", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "זהות החשבון, מצב Gmail ופרטיות מוצגים כמקורות נפרדים.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { AccountCard(authState = authState, onGoogleSignIn = onGoogleSignIn, onSignOut = viewModel::signOut) }
        item { GmailAuthorityCard(financialSyncState) }
        item { PrivacyAuthorityCard(financialSyncState) }
    }

    @Suppress("UNUSED_VARIABLE")
    val gmailAuthorizationKeptForNavigationCompatibility = onRequestGmailAuthorization
}

@Composable
private fun AccountCard(
    authState: AuthState,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.size(8.dp))
                Text("חשבון", fontWeight = FontWeight.Bold)
            }
            when (authState) {
                AuthState.Loading -> Text("זהות החשבון עדיין נטענת.")
                is AuthState.Error -> Text("מצב החשבון לא ידוע: ${authState.message}")
                AuthState.Idle -> {
                    Text("לא מחובר לחשבון.")
                    Button(onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("התחבר עם Google")
                    }
                }
                is AuthState.Authenticated -> {
                    val session = authState.session
                    Text(session.displayName.ifBlank { "שם לא זמין" }, fontWeight = FontWeight.Bold)
                    Text(session.email.ifBlank { "כתובת דוא״ל לא זמינה" })
                    Text(
                        "התחברות לחשבון אינה הוכחה ש-Gmail מחובר.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Logout, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("יציאה מהחשבון")
                    }
                    Text(
                        "יציאה מהחשבון אינה מוצגת כניתוק Gmail או כמחיקת חשבון.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun GmailAuthorityCard(financialSyncState: FinancialSyncState) {
    val connection = financialSyncState.gmailConnectionOrNull
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Gmail", fontWeight = FontWeight.Bold)
            }
            when (financialSyncState) {
                FinancialSyncState.Unauthenticated -> Text("מצב Gmail לא ידוע ללא חשבון מחובר.")
                FinancialSyncState.CheckingConnection,
                FinancialSyncState.Recovering -> Text("מצב Gmail לא ידוע — הבדיקה עדיין מתבצעת.")
                FinancialSyncState.Disconnected -> Text("Gmail מנותק לפי בדיקת השרת האחרונה.")
                is FinancialSyncState.Failed -> Text("מצב Gmail לא ידוע: ${financialSyncState.reason}")
                is FinancialSyncState.Ready,
                is FinancialSyncState.Partial -> {
                    if (connection == null) {
                        Text("מצב Gmail לא ידוע.")
                    } else if (connection.connected) {
                        Text("Gmail מחובר", fontWeight = FontWeight.SemiBold)
                        if (connection.email.isNotBlank()) Text(connection.email)
                        Text("הרשאה: קריאה בלבד")
                        Text(
                            "גרסת הסכמה: ${connection.consentVersion.ifBlank { "לא ידוע" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Gmail מנותק לפי מצב השרת.")
                    }
                }
            }
            Text(
                "ניתוק Gmail הוא פעולה נפרדת מיציאה מהחשבון. ביצוע ניתוק מלא אינו חלק ממסך זה כרגע.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrivacyAuthorityCard(financialSyncState: FinancialSyncState) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = TechBluePrimary)
                Spacer(modifier = Modifier.size(8.dp))
                Text("פרטיות ושליטה", fontWeight = FontWeight.Bold)
            }
            val connection = financialSyncState.gmailConnectionOrNull
            Text(
                when {
                    connection?.connected == true -> "מצב הסכמה זמין מהשרת; Gmail מוגבל לקריאה בלבד."
                    financialSyncState == FinancialSyncState.Disconnected -> "אין כרגע חיבור Gmail פעיל לפי השרת."
                    else -> "מצב ההסכמה המלא אינו ידוע כרגע."
                }
            )
            Text("מצב התראות: לא ידוע", style = MaterialTheme.typography.bodySmall)
            Text("ניהול מחיקה מלא: אינו זמין במסך זה עדיין", style = MaterialTheme.typography.bodySmall)
            Text(
                "Gmail מנותק אינו שווה לחשבון שנמחק.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
