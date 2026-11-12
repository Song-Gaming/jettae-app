package com.example.hyundai.data.calendar

import com.example.hyundai.data.local.CalendarEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextEventTest {

    private val hour = 3_600_000L
    private val now = 100 * hour

    private fun event(id: String, startH: Double, endH: Double, location: String? = "강남역", allDay: Boolean = false) =
        CalendarEventEntity(
            id = id, title = id, location = location, allDay = allDay,
            startMillis = now + (startH * hour).toLong(), endMillis = now + (endH * hour).toLong(), updated = "",
        )

    @Test
    fun `일정이 없으면 null`() {
        assertNull(pickNextEvent(now, emptyList()))
    }

    @Test
    fun `장소 없는 일정과 종일 일정은 고르지 않는다`() {
        val events = listOf(event("no-loc", 1.0, 2.0, location = null), event("blank", 1.0, 2.0, location = " "),
            event("all-day", 0.0, 24.0, allDay = true))
        assertNull(pickNextEvent(now, events))
    }

    @Test
    fun `이미 끝난 일정과 3시간 넘게 남은 일정은 고르지 않는다`() {
        assertNull(pickNextEvent(now, listOf(event("ended", -2.0, -1.0), event("later", 3.5, 4.0))))
    }

    @Test
    fun `진행 중인 일정은 고른다`() {
        assertEquals("ongoing", pickNextEvent(now, listOf(event("ongoing", -0.5, 0.5)))?.id)
    }

    @Test
    fun `여러 개면 가장 이른 일정`() {
        val events = listOf(event("b", 2.0, 3.0), event("a", 1.0, 2.0), event("c", 2.5, 3.0))
        assertEquals("a", pickNextEvent(now, events)?.id)
    }
}
