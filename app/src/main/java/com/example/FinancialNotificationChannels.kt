package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

internal const val FINANCIAL_PUSH_CHANNEL_ID = "savings_opportunities"
internal const val FINANCIAL_PUSH_CHANNEL_NAME = "הזדמנויות חיסכון"

internal object FinancialNotificationChannels {
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(
            NotificationChannel(
                FINANCIAL_PUSH_CHANNEL_ID,
                FINANCIAL_PUSH_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "התראות על חשבוניות והזדמנויות חיסכון חדשות"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }
}
