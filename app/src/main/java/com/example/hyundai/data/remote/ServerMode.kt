package com.example.hyundai.data.remote

import android.content.Context
import java.time.OffsetDateTime

/**
 * 어느 서버를 쓸지 기억한다. 0 = 실제 서버, 1부터 = [FakeServerApi]의 가짜 일정 시나리오 번호.
 * SERVER_URL 없이 빌드했으면 실제 서버를 고를 수 없다.
 */
class ServerModeStore(context: Context, val hasRealServer: Boolean) {
    private val prefs = context.getSharedPreferences("server_mode", Context.MODE_PRIVATE)

    var mode: Int
        get() = prefs.getInt(KEY, if (hasRealServer) REAL else 1).takeIf { it != REAL || hasRealServer } ?: 1
        set(value) {
            prefs.edit().putInt(KEY, value).apply()
        }

    val isFake: Boolean get() = mode != REAL

    companion object {
        const val REAL = 0
        private const val KEY = "mode"
    }
}

/** 호출할 때마다 [current]가 고른 서버로 넘긴다. 앱을 다시 시작하지 않고 모드를 바꿀 수 있다 */
class SwitchingServerApi(private val current: () -> ServerApi) : ServerApi {
    override suspend fun createPairing() = current().createPairing()
    override suspend fun pairingStatus() = current().pairingStatus()
    override suspend fun deletePairing() = current().deletePairing()
    override suspend fun calendarEvents(from: OffsetDateTime, to: OffsetDateTime) = current().calendarEvents(from, to)
    override suspend fun search(query: String) = current().search(query)
}
