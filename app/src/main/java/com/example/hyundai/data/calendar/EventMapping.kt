package com.example.hyundai.data.calendar

import com.example.hyundai.data.local.CalendarEventEntity
import com.example.hyundai.data.remote.RemoteEvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 서버 일정을 캐시 엔티티로 바꾼다.
 * 종일 일정은 날짜만 오므로 기기 시간대의 0시로 바꾼다. end는 포함하지 않는 날짜 그대로 쓴다.
 * 형식이 잘못된 값이면 DateTimeParseException을 던진다.
 */
fun RemoteEvent.toEntity(zone: ZoneId): CalendarEventEntity {
    val startMillis: Long
    val endMillis: Long
    if (allDay) {
        startMillis = LocalDate.parse(start).atStartOfDay(zone).toInstant().toEpochMilli()
        endMillis = LocalDate.parse(end).atStartOfDay(zone).toInstant().toEpochMilli()
    } else {
        startMillis = OffsetDateTime.parse(start).toInstant().toEpochMilli()
        endMillis = OffsetDateTime.parse(end).toInstant().toEpochMilli()
    }
    return CalendarEventEntity(
        id = id,
        title = title,
        location = location,
        allDay = allDay,
        startMillis = startMillis,
        endMillis = endMillis,
        updated = updated,
    )
}

/**
 * 일정을 받아 올 범위: 지금 ~ 7일 뒤 끝(8일 뒤 0시). 계약서 상한 31일 안이다.
 * 구글은 끝 시각이 from 뒤인 일정을 주므로 이미 끝난 일정은 빠지고 진행 중인 일정은 들어온다.
 * 분 단위로 자른다(서버 ISO 파서가 나노초를 못 읽을 수 있다).
 */
data class SyncWindow(val from: ZonedDateTime, val to: ZonedDateTime) {
    companion object {
        fun around(now: ZonedDateTime): SyncWindow = SyncWindow(
            from = now.truncatedTo(ChronoUnit.MINUTES),
            to = now.toLocalDate().plusDays(8).atStartOfDay(now.zone),
        )
    }
}
