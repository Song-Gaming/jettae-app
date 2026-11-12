package com.example.hyundai.data.calendar

import com.example.hyundai.data.remote.RemoteEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class EventMappingTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `시각 일정은 오프셋을 반영해 변환한다`() {
        val entity = RemoteEvent(
            id = "a1", title = "치과 예약", location = "강남역 3번 출구", allDay = false,
            start = "2026-11-03T09:00:00+09:00", end = "2026-11-03T10:00:00+09:00", updated = "2026-11-02T08:12:00Z",
        ).toEntity(seoul)

        assertEquals(ZonedDateTime.of(2026, 11, 3, 9, 0, 0, 0, seoul).toInstant().toEpochMilli(), entity.startMillis)
        assertEquals(60 * 60 * 1000L, entity.endMillis - entity.startMillis)
    }

    @Test
    fun `종일 일정의 end는 포함하지 않는 날짜다`() {
        // 계약서 예시: 7일~9일이면 7일, 8일 이틀
        val entity = RemoteEvent(
            id = "f6", title = "제주 여행", location = null, allDay = true,
            start = "2026-11-07", end = "2026-11-09", updated = "2026-11-01T02:30:00Z",
        ).toEntity(seoul)

        assertEquals(ZonedDateTime.of(2026, 11, 7, 0, 0, 0, 0, seoul).toInstant().toEpochMilli(), entity.startMillis)
        assertEquals(ZonedDateTime.of(2026, 11, 9, 0, 0, 0, 0, seoul).toInstant().toEpochMilli(), entity.endMillis)
        assertEquals(2 * 24 * 60 * 60 * 1000L, entity.endMillis - entity.startMillis)
    }

    @Test
    fun `동기화 범위는 지금(분 단위)부터 7일 뒤 끝까지이고 31일을 넘지 않는다`() {
        val now = ZonedDateTime.of(2026, 11, 6, 15, 30, 42, 123_456_789, seoul)
        val window = SyncWindow.around(now)

        assertEquals(ZonedDateTime.of(2026, 11, 6, 15, 30, 0, 0, seoul), window.from)
        assertEquals(ZonedDateTime.of(2026, 11, 14, 0, 0, 0, 0, seoul), window.to)
        assert(ChronoUnit.DAYS.between(window.from, window.to) <= 31)
    }
}
