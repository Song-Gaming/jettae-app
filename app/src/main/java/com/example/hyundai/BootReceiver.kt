package com.example.hyundai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.ZonedDateTime

internal const val PREFS_EVENTS = "vehicle_events"
internal const val KEY_LAST_BOOT = "last_boot"

/**
 * 시동 근사치: 차량 화면 시스템(헤드유닛)이 부팅된 시각을 기록한다.
 * 헤드유닛이 절전(suspend)에서 깨어날 때는 BOOT_COMPLETED가 오지 않으니 시동 판단에 단독으로 쓰지 않는다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val now = ZonedDateTime.now().withNano(0).toString()
        context.getSharedPreferences(PREFS_EVENTS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_BOOT, now).apply()
        Log.i("GleoNaviTest", "BOOT_COMPLETED $now")
    }
}
