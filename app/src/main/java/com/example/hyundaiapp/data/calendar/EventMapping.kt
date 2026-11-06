package com.example.hyundaiapp.data.calendar

import com.example.hyundaiapp.data.local.CalendarEventEntity
import com.example.hyundaiapp.data.remote.RemoteEvent
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

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

/** 일정을 받아 올 범위: 어제 0시 ~ 7일 뒤 끝(8일 뒤 0시). 계약서 상한 31일 안이다 */
data class SyncWindow(val from: ZonedDateTime, val to: ZonedDateTime) {
    companion object {
        fun around(now: ZonedDateTime): SyncWindow {
            val today = now.toLocalDate()
            return SyncWindow(
                from = today.minusDays(1).atStartOfDay(now.zone),
                to = today.plusDays(8).atStartOfDay(now.zone),
            )
        }
    }
}
