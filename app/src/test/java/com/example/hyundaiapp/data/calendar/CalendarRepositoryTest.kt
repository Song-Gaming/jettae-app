package com.example.hyundaiapp.data.calendar

import com.example.hyundaiapp.data.local.CalendarEventDao
import com.example.hyundaiapp.data.local.CalendarEventEntity
import com.example.hyundaiapp.data.remote.ApiError
import com.example.hyundaiapp.data.remote.ApiException
import com.example.hyundaiapp.data.remote.Pairing
import com.example.hyundaiapp.data.remote.PairingStatus
import com.example.hyundaiapp.data.remote.RemoteEvent
import com.example.hyundaiapp.data.remote.ServerApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class CalendarRepositoryTest {

    private class MemoryDao : CalendarEventDao() {
        val rows = MutableStateFlow<Map<String, CalendarEventEntity>>(emptyMap())

        override fun observeEndingAfter(fromMillis: Long): Flow<List<CalendarEventEntity>> =
            rows.map { all -> all.values.filter { it.endMillis > fromMillis }.sortedBy { it.startMillis } }

        override suspend fun deleteOverlapping(fromMillis: Long, toMillis: Long) {
            rows.value = rows.value.filterValues { !(it.endMillis > fromMillis && it.startMillis < toMillis) }
        }

        override suspend fun insertAll(events: List<CalendarEventEntity>) {
            rows.value = rows.value + events.associateBy { it.id }
        }

        override suspend fun deleteAll() {
            rows.value = emptyMap()
        }
    }

    private class StubApi : ServerApi {
        var events: List<RemoteEvent> = emptyList()
        var error: ApiError? = null

        override suspend fun createPairing() = Pairing("https://example.com/p/x", 300)
        override suspend fun pairingStatus() = PairingStatus.LINKED
        override suspend fun deletePairing() = Unit
        override suspend fun calendarEvents(from: OffsetDateTime, to: OffsetDateTime): List<RemoteEvent> {
            error?.let { throw ApiException(it) }
            return events
        }
    }

    private class MemoryLastSync : LastSyncStore {
        var value: Long? = null
        override fun load() = value
        override fun save(millis: Long) { value = millis }
        override fun clear() { value = null }
    }

    private val seoul = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-11-06T01:00:00Z"), seoul) // 서울 10시
    private val dao = MemoryDao()
    private val api = StubApi()
    private val lastSync = MemoryLastSync()
    private val repository = CalendarRepository(api, dao, lastSync, clock)

    private fun event(id: String, start: String, end: String) = RemoteEvent(
        id = id, title = id, location = null, allDay = false, start = start, end = end, updated = "2026-11-01T00:00:00Z",
    )

    @Test
    fun `성공하면 범위 안 캐시를 서버 응답으로 바꿔 취소된 일정이 사라진다`() = runTest {
        api.events = listOf(
            event("keep", "2026-11-06T09:00:00+09:00", "2026-11-06T10:00:00+09:00"),
            event("cancelled", "2026-11-06T15:00:00+09:00", "2026-11-06T16:00:00+09:00"),
        )
        repository.refresh()

        api.events = listOf(event("keep", "2026-11-06T09:00:00+09:00", "2026-11-06T10:00:00+09:00"))
        val result = repository.refresh()

        assertEquals(SyncResult.Success(1), result)
        assertEquals(setOf("keep"), dao.rows.value.keys)
        assertEquals(clock.millis(), lastSync.value)
    }

    @Test
    fun `네트워크 실패면 캐시와 마지막 갱신 시각을 그대로 둔다`() = runTest {
        api.events = listOf(event("keep", "2026-11-06T09:00:00+09:00", "2026-11-06T10:00:00+09:00"))
        repository.refresh()
        val syncedAt = lastSync.value

        api.error = ApiError.Network
        val result = repository.refresh()

        assertEquals(SyncResult.Failed(ApiError.Network), result)
        assertEquals(setOf("keep"), dao.rows.value.keys)
        assertEquals(syncedAt, lastSync.value)
    }

    @Test
    fun `연동이 없거나 끊기면 NeedsLink를 돌려준다`() = runTest {
        for (error in listOf(ApiError.NotLinked, ApiError.ReauthRequired, ApiError.InvalidToken)) {
            api.error = error
            assertEquals(SyncResult.NeedsLink, repository.refresh())
        }
    }

    @Test
    fun `시각 형식이 잘못된 일정은 건너뛰고 나머지는 저장한다`() = runTest {
        api.events = listOf(
            event("good", "2026-11-06T09:00:00+09:00", "2026-11-06T10:00:00+09:00"),
            event("bad", "2026-11-06 09:00", "2026-11-06 10:00"),
        )

        assertEquals(SyncResult.Success(1), repository.refresh())
        assertEquals(setOf("good"), dao.rows.value.keys)
    }

    @Test
    fun `연동 해제하면 캐시와 갱신 시각을 지운다`() = runTest {
        api.events = listOf(event("keep", "2026-11-06T09:00:00+09:00", "2026-11-06T10:00:00+09:00"))
        repository.refresh()

        repository.unlink()

        assertEquals(emptyMap<String, CalendarEventEntity>(), dao.rows.value)
        assertNull(lastSync.value)
    }
}
