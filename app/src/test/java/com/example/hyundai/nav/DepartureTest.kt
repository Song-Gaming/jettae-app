package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DepartureTest {

    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(seoul).toInstant()

    @Test
    fun `여유가 있으면 출발 시각이 지금보다 뒤`() {
        val leave = departureAt(at("2026-11-13T06:30"), etaSec = 52 * 60L)
        assertEquals(at("2026-11-13T05:28"), leave)
        assertTrue(leave.isAfter(at("2026-11-13T05:00")))
    }

    @Test
    fun `이미 늦었으면 출발 시각이 지금보다 앞이고 도착은 일정 뒤`() {
        val now = at("2026-11-13T06:00")
        assertTrue(departureAt(at("2026-11-13T06:30"), etaSec = 52 * 60L).isBefore(now))
        assertEquals(at("2026-11-13T06:52"), arrivalIfLeavingNow(now, 52 * 60L))
    }

    @Test
    fun `자정을 넘기면 전날 출발`() {
        assertEquals(at("2026-11-12T23:30"), departureAt(at("2026-11-13T00:20"), etaSec = 40 * 60L))
    }
}
