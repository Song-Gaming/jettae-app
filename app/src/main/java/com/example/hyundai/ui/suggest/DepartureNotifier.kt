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
import java.security.MessageDigest
import java.util.UUID

/**
 * 출발 제안 알림. 전체 화면 팝업 대신 heads-up 알림(IMPORTANCE_HIGH)을 쓴다.
 * 본문과 [안내 시작] 둘 다 MainActivity로 [ACTION_START]를 보낸다.
 * MainActivity는 다른 앱에도 열려 있으므로, 알림마다 일회용 토큰을 넣고 [consumeTap]으로 확인한다.
 */
object DepartureNotifier {
    const val ACTION_START = "com.example.hyundai.START_SUGGESTED_ROUTE"
    const val EXTRA_EVENT_ID = "event_id"
    private const val EXTRA_TOKEN = "tap_token"
    private const val CHANNEL_ID = "departure"
    private const val NOTIFICATION_ID = 1
    private const val PREFS = "departure_notifier"
    private const val KEY_TOKEN = "tap_token"

    /** 우리 알림에서 온 탭이면 true. 토큰은 한 번 쓰면 지운다(앱이 다시 시작돼도 남도록 prefs에 둠) */
    fun consumeTap(context: Context, intent: Intent): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val genuine = isSameToken(prefs.getString(KEY_TOKEN, null), intent.getStringExtra(EXTRA_TOKEN))
        if (genuine) prefs.edit().remove(KEY_TOKEN).apply()
        return genuine
    }

    internal fun isSameToken(expected: String?, given: String?): Boolean =
        expected != null && given != null && MessageDigest.isEqual(expected.toByteArray(), given.toByteArray())

    /** 알림 권한이 없으면 false */
    fun show(context: Context, eventId: String, title: String, text: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.departure_channel), NotificationManager.IMPORTANCE_HIGH)
        )
        if (!manager.areNotificationsEnabled()) return false

        val token = UUID.randomUUID().toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
        val tap = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_TOKEN, token)
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
