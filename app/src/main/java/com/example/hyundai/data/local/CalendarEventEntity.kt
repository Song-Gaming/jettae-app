package com.example.hyundai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 캐시한 일정 하나. 서버는 일정을 저장하지 않으므로 오프라인 표시는 이 캐시로 한다.
 * 종일 일정의 endMillis는 마지막 날 다음 날 0시(포함하지 않음)다.
 */
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val location: String?,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val updated: String,
)
