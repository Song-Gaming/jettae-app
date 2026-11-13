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

    private fun pick(events: List<CalendarEventEntity>, carAtOngoingPlace: Boolean? = null) =
        chooseSuggestion(suggestionCandidates(now, events), carAtOngoingPlace)?.id

    @Test
    fun `일정이 없으면 null`() {
        assertNull(pick(emptyList()))
    }

    @Test
    fun `장소 없는 일정과 종일 일정은 고르지 않는다`() {
        val events = listOf(event("no-loc", 1.0, 2.0, location = null), event("blank", 1.0, 2.0, location = " "),
            event("all-day", 0.0, 24.0, allDay = true))
        assertNull(pick(events))
    }

    @Test
    fun `이미 끝난 일정과 3시간 넘게 남은 일정은 고르지 않는다`() {
        assertNull(pick(listOf(event("ended", -2.0, -1.0), event("later", 3.5, 4.0))))
    }

    @Test
    fun `여러 개면 가장 이른 다음 일정`() {
        assertEquals("a", pick(listOf(event("b", 2.0, 3.0), event("a", 1.0, 2.0), event("c", 2.5, 3.0))))
    }

    // 00시 유치원(00~01시), 01시 회사, 지금 00시 30분
    private val kindergartenThenOffice = listOf(event("유치원", -0.5, 0.5), event("회사", 0.5, 1.5))

    @Test
    fun `시작한 일정 장소에 차가 있으면 다녀가는 중이니 다음 일정`() {
        assertEquals("회사", pick(kindergartenThenOffice, carAtOngoingPlace = true))
    }

    @Test
    fun `시작한 일정 장소에 아직 못 갔으면 늦었어도 그 일정`() {
        assertEquals("유치원", pick(kindergartenThenOffice, carAtOngoingPlace = false))
    }

    @Test
    fun `차 위치를 모르면 다음 일정을 먼저`() {
        assertEquals("회사", pick(kindergartenThenOffice, carAtOngoingPlace = null))
    }

    @Test
    fun `시작한 일정만 있으면 그 장소에 있을 때는 제안하지 않는다`() {
        val onlyOngoing = listOf(event("유치원", -0.5, 0.5))
        assertNull(pick(onlyOngoing, carAtOngoingPlace = true))
        assertEquals("유치원", pick(onlyOngoing, carAtOngoingPlace = false))
    }
}
