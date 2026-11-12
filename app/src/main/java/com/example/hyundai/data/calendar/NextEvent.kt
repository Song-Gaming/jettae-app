package com.example.hyundai.data.calendar

import com.example.hyundai.data.local.CalendarEventEntity
import java.time.Duration

/**
 * 출발 제안 대상: 장소가 있고, 아직 끝나지 않았고, [within] 안에 시작하는 첫 일정. 종일 일정은 뺀다.
 * 이미 시작한 일정도 끝나기 전이면 고른다(늦었어도 안내는 필요).
 */
fun pickNextEvent(
    nowMillis: Long,
    events: List<CalendarEventEntity>,
    within: Duration = Duration.ofHours(3),
): CalendarEventEntity? = events
    .filter { !it.allDay && !it.location.isNullOrBlank() }
    .filter { it.endMillis > nowMillis && it.startMillis <= nowMillis + within.toMillis() }
    .minByOrNull { it.startMillis }
