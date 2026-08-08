package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val PUSH_CHANNEL_ID = "savings_opportunities"
private const val PUSH_CHANNEL_NAME = "הזדמנויות חיסכון"

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
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "מצאנו הזדמנות לחיסכון"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "פתח את ClickAndSaveAI כדי לראות כמה אפשר לחסוך."
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    PUSH_CHANNEL_ID,
                    PUSH_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "התראות על חשבוניות והזדמנויות חיסכון חדשות"
                }
            )
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("openSavingsOpportunity", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((System.currentTimeMillis() and 0x7fffffff).toInt(), notification)
    }
}
