package com.example.hyundai.ui.suggest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.hyundai.MainActivity
import com.example.hyundai.R

/**
 * 출발 제안 알림. 전체 화면 팝업 대신 heads-up 알림(IMPORTANCE_HIGH)을 쓴다.
 * 본문과 [안내 시작] 둘 다 MainActivity로 [ACTION_START]를 보낸다.
 */
object DepartureNotifier {
    const val ACTION_START = "com.example.hyundai.START_SUGGESTED_ROUTE"
    const val EXTRA_EVENT_ID = "event_id"
    private const val CHANNEL_ID = "departure"
    private const val NOTIFICATION_ID = 1

    /** 알림 권한이 없으면 false */
    fun show(context: Context, eventId: String, title: String, text: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.departure_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        if (!manager.areNotificationsEnabled()) return false

        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.departure_start), tap)
            .build()
        return try {
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}
