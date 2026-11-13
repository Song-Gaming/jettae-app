package com.example.hyundai.nav

import java.time.Duration
import java.time.Instant

/** 지금 출발하면 도착하는 시각. etaSec은 NaviHelper onTripInfo의 이동 시간(초) */
fun arrivalIfLeavingNow(now: Instant, etaSec: Long): Instant = now.plusSeconds(etaSec)

/** 지금 출발하면 일정 시작보다 몇 분 일찍(양수) 또는 늦게(음수) 도착하는지 */
fun minutesEarly(eventStart: Instant, now: Instant, etaSec: Long): Long =
    Duration.between(arrivalIfLeavingNow(now, etaSec), eventStart).toMinutes()

/** 93 → "1시간 33분", 60 → "1시간", 27 → "27분" */
fun koreanDuration(minutes: Long): String = when {
    minutes < 60 -> "${minutes}분"
    minutes % 60 == 0L -> "${minutes / 60}시간"
    else -> "${minutes / 60}시간 ${minutes % 60}분"
}
