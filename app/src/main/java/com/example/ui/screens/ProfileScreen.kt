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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.example.ui.theme.TechBluePrimary

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "הפרופיל שלי",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "החשבון והעדפות החיסכון שלך במקום אחד.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("החשבון שלך", fontWeight = FontWeight.Bold)
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
                            onClick = viewModel::signOut,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("התנתק")
                        }
                    } else {
                        Text(
                            "התחבר כדי לשמור את החוויה האישית שלך ולהמשיך מאותו חשבון.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Login, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("התחבר עם Google")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Savings, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("העדפות החיסכון שלי", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (monthlyGoal > 0.0) "יעד חיסכון חודשי: ₪${monthlyGoal.toInt()}" else "עדיין לא הוגדר יעד חיסכון חודשי",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (minSavingsThreshold > 0.0) "סף חיסכון מועדף: ₪${minSavingsThreshold.toInt()} בחודש" else "אפשר להגדיר מאיזה סכום חיסכון תרצה להתמקד",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { showSettings = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("ערוך העדפות")
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = TechBluePrimary)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("פרטיות ושליטה", fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Click&SaveAI מציגה המלצות רק כשיש מספיק מידע כדי לאמת אותן. פעולה מול ספק מתבצעת רק לאחר אישור מפורש שלך.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { showPrivacy = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("privacy_connections_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "חזרה")
                }
                Spacer(modifier = Modifier.size(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "פרטיות וחיבורים",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "כאן אפשר לראות ולבטל חיבורים פעילים.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(
                    modifier = Modifier.padding(18.dp),
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
                            "הגישה היא לקריאה בלבד ומשמשת לאיתור מסמכים פיננסיים רלוונטיים. אפשר לבטל אותה בכל עת."
                        } else {
                            "חיבור חדש מתבצע רק מתהליך ההצטרפות במסך הבית, כדי לשמור על חוויה פשוטה וברורה."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isGmailConnected) {
                        OutlinedButton(
                            onClick = viewModel::disconnectGmail,
                            enabled = !isSyncing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSyncing) "מנתק…" else "בטל את החיבור")
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("השליטה נשארת אצלך", fontWeight = FontWeight.Bold)
                    Text(
                        "המערכת אינה מבצעת מעבר ספק או פעולה כספית ללא אישור מפורש שלך, ואינה שולחת לספק את תוכן ה-Gmail או את תמונת ההוצאות שלך.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
