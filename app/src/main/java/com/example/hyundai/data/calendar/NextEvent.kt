package com.example.hyundai.data.calendar

import com.example.hyundai.data.local.CalendarEventEntity
import java.time.Duration

/**
 * 출발 제안 후보. 장소가 있고 아직 끝나지 않은 일정만 보며 종일 일정은 뺀다.
 * [ongoing]은 이미 시작한 일정 중 가장 최근 것, [upcoming]은 [within] 안에 시작할 가장 이른 일정.
 */
data class SuggestionCandidates(val ongoing: CalendarEventEntity?, val upcoming: CalendarEventEntity?)

fun suggestionCandidates(
    nowMillis: Long,
    events: List<CalendarEventEntity>,
    within: Duration = Duration.ofHours(3),
): SuggestionCandidates {
    val usable = events.filter { !it.allDay && !it.location.isNullOrBlank() && it.endMillis > nowMillis }
    return SuggestionCandidates(
        ongoing = usable.filter { it.startMillis <= nowMillis }.maxByOrNull { it.startMillis },
        upcoming = usable.filter { it.startMillis > nowMillis && it.startMillis <= nowMillis + within.toMillis() }
            .minByOrNull { it.startMillis },
    )
}

/**
 * 이미 시작한 일정이 있으면 차가 지금 그 장소에 있는지로 고른다.
 * 그 장소에 있으면 들렀다 떠나는 중이니 다음 일정, 아직 못 갔으면 늦었어도 시작한 일정.
 * 차 위치나 장소를 모르면 다음 일정을 먼저 본다.
 */
fun chooseSuggestion(candidates: SuggestionCandidates, carAtOngoingPlace: Boolean?): CalendarEventEntity? = when {
    candidates.ongoing == null -> candidates.upcoming
    carAtOngoingPlace == true -> candidates.upcoming
    carAtOngoingPlace == false -> candidates.ongoing
    else -> candidates.upcoming ?: candidates.ongoing
}
