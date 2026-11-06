package com.example.hyundaiapp.data.places

import com.example.hyundaiapp.data.local.VisitEntity
import com.example.hyundaiapp.data.local.VisitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PlaceAnalysisTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private fun visit(
        placeId: String? = null,
        lat: Double = 37.39,
        lng: Double = 127.11,
        at: ZonedDateTime = ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, seoul),
        stayMinutes: Long? = 10,
        category: String? = null,
    ) = VisitEntity(
        placeId = placeId,
        name = placeId,
        category = category,
        latitude = lat,
        longitude = lng,
        arrivedAtMillis = at.toInstant().toEpochMilli(),
        departedAtMillis = stayMinutes?.let { at.plusMinutes(it).toInstant().toEpochMilli() },
        source = if (placeId == null) VisitSource.PARKED else VisitSource.NAVI,
    )

    // --- 카테고리 분류 ---

    @Test
    fun `카카오 카테고리로 종류를 정한다`() {
        assertEquals(PlaceRole.KINDERGARTEN, CategoryClassifier.classify("교육,학습 > 유아교육 > 유치원"))
        assertEquals(PlaceRole.KINDERGARTEN, CategoryClassifier.classify("교육,학습 > 유아교육 > 어린이집"))
        assertEquals(PlaceRole.MART, CategoryClassifier.classify("가정,생활 > 대형마트 > 이마트"))
        assertEquals(PlaceRole.SUPERMARKET, CategoryClassifier.classify("가정,생활 > 슈퍼마켓 > GS더프레시"))
        assertEquals(PlaceRole.LAUNDRY, CategoryClassifier.classify("생활,편의 > 세탁소 > 크린토피아"))
        assertEquals(PlaceRole.PHARMACY, CategoryClassifier.classify("의료,건강 > 약국"))
        assertEquals(PlaceRole.CAFE, CategoryClassifier.classify("음식점 > 카페 > 커피전문점 > 스타벅스"))
    }

    @Test
    fun `이름이 비슷해도 구체적인 분류가 이긴다`() {
        // 이마트24는 편의점, 홈플러스 익스프레스는 슈퍼마켓
        assertEquals(PlaceRole.CONVENIENCE, CategoryClassifier.classify("가정,생활 > 편의점 > 이마트24"))
        assertEquals(PlaceRole.SUPERMARKET, CategoryClassifier.classify("가정,생활 > 슈퍼마켓 > 홈플러스익스프레스"))
    }

    @Test
    fun `카테고리가 없거나 모르는 분류면 null`() {
        assertNull(CategoryClassifier.classify(null))
        assertNull(CategoryClassifier.classify("부동산 > 아파트"))
    }

    @Test
    fun `말로 부른 이름을 종류로 바꾼다`() {
        assertEquals(setOf(PlaceRole.KINDERGARTEN), PlaceRole.fromQuery("유치원"))
        assertEquals(setOf(PlaceRole.MART, PlaceRole.SUPERMARKET), PlaceRole.fromQuery("마트"))
        assertEquals(setOf(PlaceRole.HOME), PlaceRole.fromQuery("우리 집"))
        assertTrue(PlaceRole.fromQuery("해님유치원").isEmpty())
    }

    // --- 장소 묶기 ---

    @Test
    fun `같은 카카오 id는 멀리 찍혀도 한 장소`() {
        val clusters = PlaceClusterer.cluster(listOf(visit("kg"), visit("kg", lat = 37.395)))
        assertEquals(1, clusters.size)
        assertEquals("kakao:kg", clusters[0].key)
    }

    @Test
    fun `id 없는 방문은 150m 안이면 묶고 그보다 멀면 나눈다`() {
        val near = PlaceClusterer.cluster(listOf(visit(lat = 37.39000), visit(lat = 37.39090))) // 약 100m
        assertEquals(1, near.size)

        val far = PlaceClusterer.cluster(listOf(visit(lat = 37.39000), visit(lat = 37.39300))) // 약 330m
        assertEquals(2, far.size)
    }

    @Test
    fun `서로 다른 id는 붙어 있어도 합치지 않는다`() {
        val clusters = PlaceClusterer.cluster(listOf(visit("kindergarten"), visit("mart", lat = 37.39010)))
        assertEquals(2, clusters.size)
    }

    @Test
    fun `id 없는 주차 기록은 가까운 장소에 붙는다`() {
        val clusters = PlaceClusterer.cluster(listOf(visit("mart"), visit(null, lat = 37.39030)))
        assertEquals(1, clusters.size)
        assertEquals(2, clusters[0].visits.size)
    }

    @Test
    fun `집 앞 세탁소처럼 75m보다 먼 가게에는 주차 기록을 붙이지 않는다`() {
        val clusters = PlaceClusterer.cluster(listOf(visit("laundry"), visit(null, lat = 37.39100))) // 약 111m
        assertEquals(2, clusters.size)
    }

    @Test
    fun `지금부터 90분 안의 평소 도착 시각만 다음 목적지 후보`() {
        val now = ZonedDateTime.of(2026, 11, 6, 8, 30, 0, 0, seoul) // 금요일
        assertEquals(10, FrequencyScorer.minutesUntilUsualArrival(now.minusDays(1).withHour(8).withMinute(40), now))
        assertNull(FrequencyScorer.minutesUntilUsualArrival(now.minusDays(1).withHour(8).withMinute(10), now)) // 이미 지남
        assertNull(FrequencyScorer.minutesUntilUsualArrival(now.minusDays(5).withHour(8).withMinute(40), now)) // 일요일
    }

    @Test
    fun `거리 계산은 위도 0_001도가 약 111m`() {
        val meters = PlaceClusterer.distanceMeters(37.0, 127.0, 37.001, 127.0)
        assertEquals(111.2, meters, 0.5)
    }

    // --- 점수 ---

    @Test
    fun `반감기 14일이 지나면 점수가 절반`() {
        val now = ZonedDateTime.of(2026, 11, 6, 15, 0, 0, 0, seoul) // 금요일 15시
        val today = visit(at = now.minusMinutes(1).withHour(11)) // 시간대 다름 → 보너스 없음
        val twoWeeksAgo = visit(at = now.minusDays(14).withHour(11))

        val todayScore = FrequencyScorer.score(listOf(today), now)
        val oldScore = FrequencyScorer.score(listOf(twoWeeksAgo), now)
        assertEquals(todayScore / 2, oldScore, 0.02)
    }

    @Test
    fun `같은 평일 비슷한 시각의 방문은 1_5배`() {
        val now = ZonedDateTime.of(2026, 11, 6, 8, 30, 0, 0, seoul) // 금요일 8:30
        val sameContext = visit(at = ZonedDateTime.of(2026, 11, 5, 8, 50, 0, 0, seoul)) // 목요일 8:50
        val weekend = visit(at = ZonedDateTime.of(2026, 11, 1, 8, 50, 0, 0, seoul)) // 일요일 8:50

        assertTrue(FrequencyScorer.isSameContext(ZonedDateTime.of(2026, 11, 5, 8, 50, 0, 0, seoul), now))
        assertFalse(FrequencyScorer.isSameContext(ZonedDateTime.of(2026, 11, 1, 8, 50, 0, 0, seoul), now))
        assertTrue(FrequencyScorer.score(listOf(sameContext), now) > FrequencyScorer.score(listOf(weekend), now))
    }

    // --- 시간 패턴 ---

    @Test
    fun `저녁에 와서 밤새 머문 곳은 집, 평일 아침에 와서 오래 머문 곳은 회사`() {
        val monday = ZonedDateTime.of(2026, 11, 2, 0, 0, 0, 0, seoul)
        val visits = (0L until 5L).flatMap { d ->
            listOf(
                visit(lat = 37.40, at = monday.plusDays(d).withHour(9), stayMinutes = 9 * 60),
                visit(lat = 37.39, at = monday.plusDays(d).withHour(19), stayMinutes = 13 * 60),
            )
        }
        val clusters = PlaceClusterer.cluster(visits)
        val roles = PatternClassifier.classify(clusters, seoul)

        val homeKey = clusters.first { it.latitude < 37.395 }.key
        val workKey = clusters.first { it.latitude > 37.395 }.key
        assertEquals(PlaceRole.HOME, roles[homeKey])
        assertEquals(PlaceRole.WORK, roles[workKey])
    }

    @Test
    fun `평일 등하원 시간에 잠깐 들르는 곳은 유치원으로 본다`() {
        val monday = ZonedDateTime.of(2026, 11, 2, 8, 40, 0, 0, seoul)
        val visits = (0L until 4L).map { d -> visit(lat = 37.38, at = monday.plusDays(d), stayMinutes = 7) }
        val clusters = PlaceClusterer.cluster(visits)

        assertEquals(PlaceRole.KINDERGARTEN, PatternClassifier.classify(clusters, seoul)[clusters[0].key])
    }

    @Test
    fun `두 번뿐이면 패턴으로 분류하지 않는다`() {
        val monday = ZonedDateTime.of(2026, 11, 2, 19, 0, 0, 0, seoul)
        val visits = (0L until 2L).map { d -> visit(at = monday.plusDays(d), stayMinutes = 12 * 60) }
        assertTrue(PatternClassifier.classify(PlaceClusterer.cluster(visits), seoul).isEmpty())
    }
}
