package com.example

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.local.AppDatabase
import com.example.data.repository.ObservedBillsRepository
import com.example.data.repository.ShoppingRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PushRegistration {
    fun registerCurrentToken() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(::registerToken)
            .addOnFailureListener { error ->
                Log.w("PushRegistration", "Unable to get FCM token", error)
            }
    }

    fun registerToken(token: String) {
        if (FirebaseAuth.getInstance().currentUser == null || token.isBlank()) return
        FirebaseFunctions.getInstance("europe-west1")
            .getHttpsCallable("registerPushToken")
            .call(mapOf("token" to token))
            .addOnFailureListener { error ->
                Log.w("PushRegistration", "FCM token registration failed", error)
            }
    }
}

class ClickAndSaveMessagingService : FirebaseMessagingService() {
    // This is an acceleration path only. Android may reclaim the process after an FCM
    // callback; authoritative reconciliation is guaranteed again on authenticated startup/resume.
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushRegistration.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val pushType = message.data[PUSH_TYPE_EXTRA]
        if (pushType == PUSH_TYPE_NEW_INVOICE) {
            refreshObservedBillsFromBackend()
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "מצאנו עדכון חדש"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "פתח את ClickAndSaveAI כדי לראות את העדכון."
        showNotification(title, body, pushType)
    }

    private fun refreshObservedBillsFromBackend() {
        if (FirebaseAuth.getInstance().currentUser == null) return
        val shoppingRepository = ShoppingRepository(AppDatabase.getDatabase(applicationContext))
        val observedBillsRepository = ObservedBillsRepository(shoppingRepository)
        refreshScope.launch {
            runCatching { observedBillsRepository.refreshObservedBills() }
                .onFailure { error ->
                    // The push notification remains useful even when this best-effort local
                    // reconciliation is unavailable. Authenticated app startup/resume retries it.
                    Log.w("ObservedBillsRefresh", "NEW_INVOICE snapshot refresh failed", error)
                }
        }
    }

    private fun showNotification(title: String, body: String, pushType: String?) {
        FinancialNotificationChannels.ensureCreated(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val destinationTab = destinationTabForPushType(pushType)
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (destinationTab != null && pushType != null) {
                putExtra(PUSH_TYPE_EXTRA, pushType)
            }
        }
        val pendingIntentRequestCode = when (destinationTab) {
            1 -> 101
            2 -> 102
            0 -> 100
            else -> 199
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            pendingIntentRequestCode,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, FINANCIAL_PUSH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }
}
