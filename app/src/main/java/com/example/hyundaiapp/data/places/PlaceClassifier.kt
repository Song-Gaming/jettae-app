package com.example.hyundaiapp.data.places

import com.example.hyundaiapp.data.local.VisitEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** 분류 근거. 화면에 "자동 분류인지 사용자가 정했는지" 보여 줄 때 쓴다 */
enum class RoleSource { USER, CATEGORY, PATTERN, NONE }

/**
 * 카카오 카테고리 문자열로 장소 종류를 정한다.
 * 카테고리는 "교육,학습 > 유아교육 > 유치원"처럼 큰 분류 > 작은 분류 순이라, 뒤(구체적인 것)부터 찾는다.
 * 같은 단계에서는 표의 위쪽 규칙이 이긴다. 예: "이마트24"는 대형마트가 아니라 편의점.
 */
object CategoryClassifier {

    private val RULES: List<Pair<PlaceRole, List<String>>> = listOf(
        PlaceRole.KINDERGARTEN to listOf("유치원", "어린이집"),
        PlaceRole.SCHOOL to listOf("초등학교", "중학교", "고등학교"),
        PlaceRole.ACADEMY to listOf("학원"),
        PlaceRole.CONVENIENCE to listOf("편의점", "이마트24", "GS25", "세븐일레븐", "미니스톱"),
        PlaceRole.SUPERMARKET to listOf("슈퍼마켓", "익스프레스", "에브리데이", "식자재", "슈퍼"),
        PlaceRole.MART to listOf("대형마트", "이마트", "홈플러스", "롯데마트", "코스트코", "트레이더스"),
        PlaceRole.LAUNDRY to listOf("세탁"),
        PlaceRole.PHARMACY to listOf("약국"),
        PlaceRole.HOSPITAL to listOf("병원", "의원", "치과"),
        PlaceRole.CAFE to listOf("카페", "커피"),
    )

    fun classify(category: String?): PlaceRole? {
        if (category.isNullOrBlank()) return null
        for (segment in category.split(">").map { it.trim() }.asReversed()) {
            RULES.firstOrNull { (_, keywords) -> keywords.any { segment.contains(it, ignoreCase = true) } }
                ?.let { return it.first }
        }
        return null
    }
}

/**
 * 카테고리가 없는 곳(집·회사처럼 그냥 주차한 곳)을 시간 패턴으로 분류한다.
 * - 집: 저녁(18~4시)에 도착해 6시간 넘게 머문 날이 3번 이상인 곳 중 가장 많은 곳
 * - 회사: 평일 오전(6~11시)에 도착해 4시간 넘게 머문 날이 3번 이상인 곳 중 가장 많은 곳
 * - 유치원(보조 규칙): 평일 등·하원 시간(7~10시, 14~18시)에 2~20분만 머문 방문이 3번 이상이고 방문의 60% 이상
 */
object PatternClassifier {

    private const val MIN_REPEATS = 3

    fun classify(clusters: List<PlaceCluster>, zone: ZoneId): Map<String, PlaceRole> {
        val result = mutableMapOf<String, PlaceRole>()

        clusters.maxByOrNull { it.visits.count { v -> v.isOvernightStay(zone) } }
            ?.takeIf { c -> c.visits.count { it.isOvernightStay(zone) } >= MIN_REPEATS }
            ?.let { result[it.key] = PlaceRole.HOME }

        clusters.filter { it.key !in result }
            .maxByOrNull { it.visits.count { v -> v.isWorkday(zone) } }
            ?.takeIf { c -> c.visits.count { it.isWorkday(zone) } >= MIN_REPEATS }
            ?.let { result[it.key] = PlaceRole.WORK }

        clusters.filter { it.key !in result }.forEach { cluster ->
            val pickups = cluster.visits.count { it.isPickup(zone) }
            if (pickups >= MIN_REPEATS && pickups >= cluster.visits.size * 0.6) {
                result[cluster.key] = PlaceRole.KINDERGARTEN
            }
        }
        return result
    }

    private fun VisitEntity.arrivedAt(zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(arrivedAtMillis).atZone(zone)

    private fun VisitEntity.stayMinutes(): Long? = departedAtMillis?.let { (it - arrivedAtMillis) / 60_000 }

    private fun ZonedDateTime.isWeekday() = dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY

    private fun VisitEntity.isOvernightStay(zone: ZoneId): Boolean {
        val hour = arrivedAt(zone).hour
        return (hour >= 18 || hour < 4) && (stayMinutes() ?: 0) >= 6 * 60
    }

    private fun VisitEntity.isWorkday(zone: ZoneId): Boolean {
        val arrived = arrivedAt(zone)
        return arrived.isWeekday() && arrived.hour in 6..10 && (stayMinutes() ?: 0) >= 4 * 60
    }

    private fun VisitEntity.isPickup(zone: ZoneId): Boolean {
        val arrived = arrivedAt(zone)
        val stay = stayMinutes() ?: return false
        return arrived.isWeekday() && (arrived.hour in 7..9 || arrived.hour in 14..17) && stay in 2..20
    }
}
