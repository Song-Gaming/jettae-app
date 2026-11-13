package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DepartureTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(seoul).toInstant()

    @Test
    fun `여유가 있으면 일찍 도착하는 분이 양수`() {
        assertEquals(93L, minutesEarly(at("2026-11-14T00:33"), at("2026-11-13T22:33"), etaSec = 27 * 60L))
    }

    @Test
    fun `이미 늦었으면 음수이고 도착 시각은 일정 뒤`() {
        val now = at("2026-11-13T06:00")
        assertEquals(-22L, minutesEarly(at("2026-11-13T06:30"), now, etaSec = 52 * 60L))
        assertEquals(at("2026-11-13T06:52"), arrivalIfLeavingNow(now, 52 * 60L))
    }

    @Test
    fun `자정을 넘겨도 계산된다`() {
        assertEquals(10L, minutesEarly(at("2026-11-14T00:20"), at("2026-11-13T23:30"), etaSec = 40 * 60L))
    }

    @Test
    fun `분을 시간과 분으로 읽기 좋게`() {
        assertEquals("27분", koreanDuration(27))
        assertEquals("1시간", koreanDuration(60))
        assertEquals("1시간 33분", koreanDuration(93))
    }
}
