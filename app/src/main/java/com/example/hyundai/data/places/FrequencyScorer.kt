package com.example.hyundai.data.places

import com.example.hyundai.data.local.VisitEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow

/**
 * 장소 점수 = 방문마다 (최근성 × 시간대 보너스)를 더한 값.
 * - 최근성: 반감기 14일. 오늘 1.0, 2주 전 0.5, 4주 전 0.25. 안 가게 된 곳은 점수가 저절로 떨어진다
 * - 시간대 보너스: 지금과 같은 평일/주말이고 도착 시각이 ±1시간 안이면 1.5배
 *   (요일 하나하나로 나누면 기록이 너무 적어져 평일/주말로만 나눈다)
 */
object FrequencyScorer {

    const val HALF_LIFE_DAYS = 14.0
    const val CONTEXT_BONUS = 1.5
    private const val CONTEXT_WINDOW_MINUTES = 60

    fun score(visits: List<VisitEntity>, now: ZonedDateTime): Double = visits.sumOf { visit ->
        val arrived = Instant.ofEpochMilli(visit.arrivedAtMillis).atZone(now.zone)
        val ageDays = ((now.toInstant().toEpochMilli() - visit.arrivedAtMillis) / 86_400_000.0).coerceAtLeast(0.0)
        val recency = 0.5.pow(ageDays / HALF_LIFE_DAYS)
        recency * if (isSameContext(arrived, now)) CONTEXT_BONUS else 1.0
    }

    /**
     * 같은 평일/주말이고 이 방문의 도착 시각이 지금부터 [maxMinutes]분 안에 있으면 그 분 수, 아니면 null.
     * "지금 출발하면 평소 어디에 도착하나"(다음 목적지 추천)에 쓴다.
     */
    fun minutesUntilUsualArrival(arrived: ZonedDateTime, now: ZonedDateTime, maxMinutes: Int = 90): Int? {
        if (arrived.isWeekend() != now.isWeekend()) return null
        val diff = (arrived.hour * 60 + arrived.minute) - (now.hour * 60 + now.minute)
        return diff.takeIf { it in 0..maxMinutes }
    }

    fun isSameContext(arrived: ZonedDateTime, now: ZonedDateTime): Boolean {
        if (arrived.isWeekend() != now.isWeekend()) return false
        val a = arrived.hour * 60 + arrived.minute
        val b = now.hour * 60 + now.minute
        val diff = abs(a - b)
        return min(diff, 24 * 60 - diff) <= CONTEXT_WINDOW_MINUTES
    }

    private fun ZonedDateTime.isWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
}
