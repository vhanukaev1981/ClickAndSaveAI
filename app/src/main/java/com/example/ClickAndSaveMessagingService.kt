package com.example

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

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
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushRegistration.registerToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val pushType = message.data[PUSH_TYPE_EXTRA]
        val exactEntity = pushType == PUSH_TYPE_NEW_INVOICE ||
            pushType == PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY
        if (exactEntity && !hasExactTarget(message)) {
            Log.w("PushNavigation", "Entity push ignored because its exact target is missing")
            return
        }

        val title = when (pushType) {
            PUSH_TYPE_NEW_INVOICE -> "חיוב חדש זוהה"
            PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY -> "נמצאה הזדמנות חיסכון"
            else -> "עדכון חדש ב-ClickAndSaveAI"
        }
        val body = when (pushType) {
            PUSH_TYPE_NEW_INVOICE -> "פתח את ClickAndSaveAI לצפייה מאובטחת בפרטי החיוב."
            PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY -> "פתח את ClickAndSaveAI לצפייה מאובטחת בהזדמנות."
            else -> "פתח את האפליקציה לצפייה מאובטחת בפרטים."
        }
        showNotification(title, body, message)
    }

    private fun hasExactTarget(message: RemoteMessage): Boolean {
        return when (message.data[PUSH_TYPE_EXTRA]) {
            PUSH_TYPE_NEW_INVOICE -> !message.data["sourceMessageId"].isNullOrBlank()
            PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY ->
                !message.data[PUSH_OPPORTUNITY_ID_EXTRA].isNullOrBlank() &&
                    !message.data[PUSH_OFFER_ID_EXTRA].isNullOrBlank()
            else -> true
        }
    }

    private fun showNotification(title: String, body: String, message: RemoteMessage) {
        FinancialNotificationChannels.ensureCreated(this)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openIntent = Intent(this, PushEntryActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            message.data.forEach { (key, value) -> putExtra(key, value) }
        }
        val requestCode = listOf(
            message.data[PUSH_TYPE_EXTRA].orEmpty(),
            message.data["sourceMessageId"].orEmpty(),
            message.data[PUSH_OPPORTUNITY_ID_EXTRA].orEmpty(),
            message.data[PUSH_OFFER_ID_EXTRA].orEmpty()
        ).joinToString("\u0000").hashCode() and 0x3fffffff
        val pendingIntent = PendingIntent.getActivity(
            this,
            1_000 + requestCode,
            openIntent,
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
        notificationManager.notify(1_000 + requestCode, notification)
    }
}
