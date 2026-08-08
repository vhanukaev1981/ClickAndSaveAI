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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
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

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onGoogleSignIn: () -> Unit,
    onRequestGmailAuthorization: () -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) {
        SettingsScreen(viewModel = viewModel, onBackClick = { showSettings = false })
        return
    }

    val session by viewModel.userSession.collectAsState()
    val isGmailConnected by viewModel.isGmailConnected.collectAsState()
    val connectedEmail by viewModel.connectedEmail.collectAsState()
    val isSyncing by viewModel.isSyncingGmail.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "הפרופיל שלי",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("חשבון", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (session.isAuthenticated) {
                        Text(session.displayName.ifBlank { "משתמש מחובר" }, fontWeight = FontWeight.Bold)
                        Text(session.email.ifBlank { "כתובת דוא״ל לא זמינה" })
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("התנתק")
                        }
                    } else {
                        Text("עדיין לא התחברת")
                        Spacer(modifier = Modifier.height(10.dp))
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
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("חיבור Gmail", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isGmailConnected) {
                            "מחובר: ${connectedEmail.ifBlank { session.email }}"
                        } else {
                            "Gmail עדיין לא מחובר"
                        }
                    )
                    Text(
                        if (isGmailConnected) {
                            "חשבוניות חדשות נקלטות אוטומטית. אין צורך לבצע סריקה ידנית."
                        } else {
                            "בחיבור הראשון נבדוק עד 6 חודשים אחורה. ההרשאה היא לקריאה בלבד."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    when {
                        !session.isAuthenticated -> {
                            Button(onClick = onGoogleSignIn, modifier = Modifier.fillMaxWidth()) {
                                Text("התחבר לפני חיבור Gmail")
                            }
                        }
                        !isGmailConnected -> {
                            Button(
                                onClick = onRequestGmailAuthorization,
                                enabled = !isSyncing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null)
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("חבר Gmail")
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = viewModel::disconnectGmail,
                                enabled = !isSyncing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("נתק Gmail")
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("העדפות", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "הגדר את יעדי החיסכון והעדפות השירות שלך.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("פתח הגדרות")
                    }
                }
            }
        }
    }
}
