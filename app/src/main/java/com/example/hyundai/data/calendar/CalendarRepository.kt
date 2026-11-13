package com.example.hyundai.data.calendar

import android.content.Context
import android.util.Log
import com.example.hyundai.data.local.CalendarEventDao
import com.example.hyundai.data.local.CalendarEventEntity
import com.example.hyundai.data.remote.ApiError
import com.example.hyundai.data.remote.ApiException
import com.example.hyundai.data.remote.Pairing
import com.example.hyundai.data.remote.PairingStatus
import com.example.hyundai.data.remote.ServerApi
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

/** 동기화 결과. 화면과 WorkManager가 이 값으로 다음 동작을 정한다 */
sealed interface SyncResult {
    data class Success(val eventCount: Int) : SyncResult

    /** 구글 연동이 없거나 끊겨 QR 화면으로 안내해야 함. 캐시는 그대로 둔다 */
    data object NeedsLink : SyncResult

    /** 네트워크·서버 문제. 캐시는 그대로 둔다 */
    data class Failed(val error: ApiError) : SyncResult
}

/**
 * 캘린더 연동과 일정 캐시를 맡는다. 화면은 서버 응답이 아니라 [observeEvents]의 Room 캐시만 본다.
 */
class CalendarRepository(
    private val api: ServerApi,
    private val dao: CalendarEventDao,
    private val lastSync: LastSyncStore,
    private val clock: Clock,
) {

    /** 오늘 0시 이후에 끝나는 캐시 일정 */
    fun observeEvents(): Flow<List<CalendarEventEntity>> {
        val startOfToday = LocalDate.now(clock).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        return dao.observeEndingAfter(startOfToday)
    }

    val lastSyncMillis: Long? get() = lastSync.load()

    /**
     * 화면에 들어올 때 쓴다. 계약서대로 GET /pairing/status로 연동 여부를 먼저 확인하고,
     * linked일 때만 일정을 받는다.
     */
    suspend fun syncIfLinked(): SyncResult {
        val status = try {
            api.pairingStatus()
        } catch (e: ApiException) {
            return e.error.toSyncResult()
        }
        return if (status == PairingStatus.LINKED) refresh() else SyncResult.NeedsLink
    }

    /** 연동 여부 확인 없이 일정만 다시 받는다. 새로고침 버튼과 주기 갱신에서 쓴다 */
    suspend fun refresh(): SyncResult {
        val window = SyncWindow.around(ZonedDateTime.now(clock))
        val remote = try {
            api.calendarEvents(window.from.toOffsetDateTime(), window.to.toOffsetDateTime())
        } catch (e: ApiException) {
            return e.error.toSyncResult()
        }
        val entities = remote.mapNotNull { event ->
            try {
                event.toEntity(clock.zone)
            } catch (e: DateTimeParseException) {
                null
            }
        }
        if (entities.size < remote.size) {
            // 일정 내용은 로그에 남기지 않고 개수만 남긴다
            Log.w(TAG, "시각 형식이 잘못된 일정 ${remote.size - entities.size}개를 건너뜀")
        }
        dao.replaceAll(entities)
        lastSync.save(clock.millis())
        return SyncResult.Success(entities.size)
    }

    suspend fun pairingStatus(): PairingStatus = api.pairingStatus()

    suspend fun createPairing(): Pairing = api.createPairing()

    /** 구글 연동을 해제하고 이 기기의 일정 캐시도 지운다 */
    suspend fun unlink() {
        api.deletePairing()
        clearCache()
    }

    /** 서버 모드를 바꿀 때: 다른 서버의 일정이 섞이지 않게 캐시를 비운다 */
    suspend fun clearCache() {
        dao.deleteAll()
        lastSync.clear()
    }

    private fun ApiError.toSyncResult(): SyncResult = when (this) {
        ApiError.NotLinked, ApiError.ReauthRequired, ApiError.InvalidToken -> SyncResult.NeedsLink
        else -> SyncResult.Failed(this)
    }

    private companion object {
        const val TAG = "CalendarRepository"
    }
}

/** 마지막 동기화 성공 시각 저장소 */
interface LastSyncStore {
    fun load(): Long?
    fun save(millis: Long)
    fun clear()
}

class PrefsLastSyncStore(context: Context) : LastSyncStore {
    private val prefs = context.getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)

    override fun load(): Long? = prefs.getLong(KEY, -1L).takeIf { it >= 0 }

    override fun save(millis: Long) {
        prefs.edit().putLong(KEY, millis).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "last_sync_millis"
    }
}
