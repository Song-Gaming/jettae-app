package com.example.hyundai.nav

import java.time.Duration
import java.time.Instant

/** 일정 시작 [margin] 전에 도착하려면 떠나야 하는 시각. etaSec은 NaviHelper onTripInfo의 이동 시간(초) */
fun departureAt(eventStart: Instant, etaSec: Long, margin: Duration = Duration.ofMinutes(10)): Instant =
    eventStart.minusSeconds(etaSec).minus(margin)

/** 지금 출발하면 도착하는 시각 */
fun arrivalIfLeavingNow(now: Instant, etaSec: Long): Instant = now.plusSeconds(etaSec)
