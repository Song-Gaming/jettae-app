package com.example.hyundaiapp.data.places

import com.example.hyundaiapp.data.local.VisitEntity
import com.example.hyundaiapp.data.local.VisitSource
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * 에뮬레이터는 실제로 돌아다니지 않아 방문 기록이 쌓이지 않으므로, 시연용으로 5주치 가상 기록을 만든다.
 * 장소·좌표는 판교 근처의 가상 값이다. 매번 같은 결과가 나오도록 무작위 값을 쓰지 않는다.
 *
 * - 집, 회사: 카테고리 없이 주차만 함 → 시간 패턴으로 분류됨
 * - 해님유치원: 평일 아침 등원 → "유치원" 하면 여기
 * - 햇살어린이집: 한 달 전에 두 번 간 곳 → 점수가 낮아 후보에서 밀림
 * - 이마트(토요일), GS더프레시(화·목), 크린토피아(수요일)
 */
object DemoVisits {

    private data class DemoPlace(
        val placeId: String?,
        val name: String?,
        val category: String?,
        val latitude: Double,
        val longitude: Double,
        val source: VisitSource,
    )

    private val HOME = DemoPlace(null, null, null, 37.39200, 127.11200, VisitSource.PARKED)
    private val WORK = DemoPlace(null, null, null, 37.40200, 127.10800, VisitSource.PARKED)
    private val KINDERGARTEN = DemoPlace("demo-kg-1", "해님유치원", "교육,학습 > 유아교육 > 유치원", 37.39500, 127.11500, VisitSource.NAVI)
    private val OLD_DAYCARE = DemoPlace("demo-kg-2", "햇살어린이집", "교육,학습 > 유아교육 > 어린이집", 37.39900, 127.12000, VisitSource.NAVI)
    private val MART = DemoPlace("demo-mart-1", "이마트 판교점", "가정,생활 > 대형마트 > 이마트", 37.39350, 127.10950, VisitSource.NAVI)
    private val SUPER = DemoPlace("demo-super-1", "GS더프레시 판교점", "가정,생활 > 슈퍼마켓 > GS더프레시", 37.38850, 127.11550, VisitSource.NAVI)
    private val LAUNDRY = DemoPlace("demo-laundry-1", "크린토피아 판교점", "생활,편의 > 세탁소 > 크린토피아", 37.39150, 127.11400, VisitSource.NAVI)

    private const val DAYS = 35

    fun generate(now: ZonedDateTime): List<VisitEntity> {
        val visits = mutableListOf<VisitEntity>()
        val today = now.toLocalDate()
        for (daysAgo in DAYS downTo 1) {
            val day = today.minusDays(daysAgo.toLong())
            val weekday = day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY
            // 주차 위치가 매번 조금씩 다르게(최대 약 30m)
            val jitter = (daysAgo % 5 - 2) * 0.0001

            fun add(place: DemoPlace, arrive: LocalTime, stayMinutes: Long) {
                val arrivedAt = day.atTime(arrive).atZone(now.zone)
                visits += VisitEntity(
                    placeId = place.placeId,
                    name = place.name,
                    category = place.category,
                    latitude = place.latitude + jitter,
                    longitude = place.longitude + jitter,
                    arrivedAtMillis = arrivedAt.toInstant().toEpochMilli(),
                    departedAtMillis = arrivedAt.plusMinutes(stayMinutes).toInstant().toEpochMilli(),
                    source = place.source,
                )
            }

            if (weekday) {
                add(KINDERGARTEN, LocalTime.of(8, 40), 8)
                add(WORK, LocalTime.of(9, 5), 9 * 60)
            }
            when (day.dayOfWeek) {
                DayOfWeek.TUESDAY, DayOfWeek.THURSDAY -> add(SUPER, LocalTime.of(18, 40), 15)
                DayOfWeek.WEDNESDAY -> add(LAUNDRY, LocalTime.of(18, 50), 5)
                DayOfWeek.SATURDAY -> add(MART, LocalTime.of(11, 0), 70)
                else -> Unit
            }
            if (daysAgo in 30..31) add(OLD_DAYCARE, LocalTime.of(8, 50), 10)
            add(HOME, LocalTime.of(19, 20), 13 * 60)
        }
        return visits
    }
}
