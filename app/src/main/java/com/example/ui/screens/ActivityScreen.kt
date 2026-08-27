package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.FinancialActivityEvent
import com.example.data.repository.FinancialActivityResult
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.activityOrNull
import com.example.ui.MainViewModel
import com.example.ui.components.ActivityTimelineItem
import com.example.ui.components.V3ActivityTone
import com.example.ui.components.V3Panel
import com.example.ui.components.V3ScreenHeader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(viewModel: MainViewModel) {
    val financialSyncState by viewModel.financialSyncState.collectAsState()
    val authoritativeFinancialActivity by viewModel.authoritativeFinancialActivity.collectAsState()
    val activityHistory = financialSyncState.activityOrNull ?: authoritativeFinancialActivity

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .testTag("activity_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        V3ScreenHeader(
            eyebrow = "יומן מאומת",
            title = "פעילות",
            subtitle = "כאן מוצגים רק אירועים שנשמרו ואומתו. אירוע שלא תועד לא יוצג כאילו התרחש."
        )

        when (financialSyncState) {
            FinancialSyncState.Unauthenticated -> ActivityStatusCard(
                "החשבון אינו מחובר",
                "יש להתחבר כדי לראות את פעילות החשבון."
            )
            FinancialSyncState.CheckingConnection -> ActivityStatusCard(
                "בודקים את החיבור",
                "הפעילות עדיין נטענת."
            )
            FinancialSyncState.Disconnected -> ActivityStatusCard(
                "Gmail אינו מחובר",
                "לא ניתן לזהות פעילות חדשה מ-Gmail עד לחיבור מחדש."
            )
            FinancialSyncState.Recovering -> ActivityStatusCard(
                "טוענים את הפעילות",
                "נציג רק אירועים שניתן לאמת."
            )
            is FinancialSyncState.Failed -> {
                ActivityStatusCard(
                    "טעינת הפעילות נכשלה",
                    "לא הצלחנו לעדכן את הפעילות כרגע. אפשר לנסות שוב בעוד רגע."
                )
                RetryActivity(viewModel)
            }
            is FinancialSyncState.Partial -> {
                ActivityStatusCard(
                    "הפעילות עשויה להיות חלקית",
                    "ייתכן שחסרים אירועים חדשים. מוצגים רק אירועים שכבר אומתו."
                )
                VerifiedActivityHistory(activityHistory)
                RetryActivity(viewModel)
            }
            is FinancialSyncState.Ready -> VerifiedActivityHistory(activityHistory)
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun VerifiedActivityHistory(activity: FinancialActivityResult?) {
    if (activity == null) {
        ActivityStatusCard("הפעילות עדיין לא זמינה", "לא הצלחנו לטעון את היסטוריית הפעילות כרגע.")
        return
    }
    if (activity.events.isEmpty()) {
        ActivityStatusCard(
            "אין אירועים מתועדים בתקופה הזמינה",
            if (activity.isCompleteHistory) {
                "לא נמצאה פעילות נוספת בתקופה שנבדקה."
            } else {
                "ייתכן שקיימת פעילות מחוץ לתקופה שנבדקה."
            }
        )
        return
    }

    var previousGroup: String? = null
    activity.events.forEach { event ->
        val group = activityGroupLabel(event.timestamp)
        if (group != previousGroup) {
            Text(
                text = group,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            previousGroup = group
        }
        ActivityTimelineItem(
            title = activityTitle(event),
            supporting = activitySupportingText(event),
            timeLabel = event.timestamp.takeIf(String::isNotBlank),
            tone = activityTone(event),
            modifier = Modifier.testTag("v3_activity_timeline_item")
        )
    }
    if (!activity.isCompleteHistory) {
        Text(
            "מוצגת פעילות חלקית לפי המידע הזמין כרגע.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun activityTitle(event: FinancialActivityEvent): String = when (event.type) {
    "GMAIL_CONNECTED" -> "Gmail חובר לקריאה בלבד"
    "SCAN_COMPLETED" -> "סריקת Gmail הושלמה"
    "BILL_DETECTED" -> "זוהה חשבון"
    "RECURRING_SERVICE_DETECTED" -> "זוהה שירות חוזר"
    "OPPORTUNITY_FOUND" -> "נמצאה הזדמנות חיסכון"
    else -> "פעילות בחשבון"
}

private fun activitySupportingText(event: FinancialActivityEvent): String = buildList {
    add(activityStatusLabel(event.status))
    event.providerName?.takeIf(String::isNotBlank)?.let { add("ספק: $it") }
    event.category?.takeIf(String::isNotBlank)?.let { add("קטגוריה: $it") }
    event.observedAmount?.let { add("סכום שנצפה: ${money(it)}") }
    event.verificationStatus?.takeIf(String::isNotBlank)?.let { add(verificationLabel(it)) }
}.joinToString(" • ")

private fun activityTone(event: FinancialActivityEvent): V3ActivityTone = when (event.status.uppercase()) {
    "COMPLETED", "READY", "SUCCESS" -> V3ActivityTone.SUCCESS
    "PENDING", "PROCESSING", "IN_PROGRESS" -> V3ActivityTone.ATTENTION
    "FAILED", "ERROR" -> V3ActivityTone.NEUTRAL
    else -> V3ActivityTone.INFO
}

private fun activityGroupLabel(timestamp: String, now: Date = Date()): String {
    val datePrefix = timestamp.take(10)
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val eventDate = runCatching { formatter.parse(datePrefix) }.getOrNull() ?: return "פעילות קודמת"

    val todayCalendar = Calendar.getInstance().apply {
        time = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val eventCalendar = Calendar.getInstance().apply {
        time = eventDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val yesterdayCalendar = (todayCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val weekStart = (todayCalendar.clone() as Calendar).apply {
        val daysFromSunday = (get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY + 7) % 7
        add(Calendar.DAY_OF_YEAR, -daysFromSunday)
    }

    return when {
        eventCalendar.timeInMillis == todayCalendar.timeInMillis -> "היום"
        eventCalendar.timeInMillis == yesterdayCalendar.timeInMillis -> "אתמול"
        !eventCalendar.before(weekStart) && eventCalendar.before(todayCalendar) -> "השבוע"
        else -> "פעילות קודמת"
    }
}

private fun activityStatusLabel(status: String): String = when (status.uppercase()) {
    "COMPLETED", "READY", "SUCCESS" -> "הושלם"
    "PENDING", "PROCESSING", "IN_PROGRESS" -> "בטיפול"
    "FAILED", "ERROR" -> "לא הושלם"
    else -> "מצב הפעילות בבדיקה"
}

private fun verificationLabel(status: String): String = when (status.uppercase()) {
    "VERIFIED" -> "המידע אומת"
    "UNVERIFIED", "PENDING" -> "המידע ממתין לאימות"
    else -> "מצב האימות עדיין לא ידוע"
}

@Composable
private fun ActivityStatusCard(title: String, description: String) {
    V3Panel {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RetryActivity(viewModel: MainViewModel) {
    Button(onClick = { viewModel.refreshFinancialSession(FinancialRefreshReason.RETRY) }) {
        Icon(Icons.Outlined.CloudSync, contentDescription = null)
        Spacer(Modifier.padding(3.dp))
        Text("נסה שוב")
    }
}
