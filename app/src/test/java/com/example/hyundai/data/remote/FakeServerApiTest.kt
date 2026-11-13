package com.example.hyundai.data.remote

import com.example.hyundai.data.calendar.chooseSuggestion
import com.example.hyundai.data.calendar.suggestionCandidates
import com.example.hyundai.data.calendar.toEntity
import com.example.hyundai.data.places.PlaceClusterer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

/** 가짜 일정 시나리오마다 출발 제안이 의도한 대로 갈리는지: 고르는 일정과 장소 검색 결과 */
class FakeServerApiTest {

    private val zone = ZoneId.of("Asia/Seoul")

    // 에뮬레이터 기본 차 위치(성남 시흥동). MainActivity가 내비에서 받는 현재 위치 대신 쓴다
    private val emulatorCar = 37.4127212 to 127.094681

    private suspend fun keyEventAndPlaces(scenario: Int, now: Instant): Pair<String?, Int> {
        val api = FakeServerApi(scenario = { scenario }, clock = Clock.fixed(now, zone))
        val events = api.calendarEvents(now.atZone(zone).minusDays(1).toOffsetDateTime(), now.atZone(zone).plusDays(7).toOffsetDateTime())
            .map { it.toEntity(zone) }
        val candidates = suggestionCandidates(now.toEpochMilli(), events)
        val carAtOngoing = candidates.ongoing?.let { ongoing ->
            api.search(ongoing.location.orEmpty()).firstOrNull()?.let {
                PlaceClusterer.distanceMeters(emulatorCar.first, emulatorCar.second, it.latitude, it.longitude) <= 300
            }
        }
        val picked = chooseSuggestion(candidates, carAtOngoing)
        return picked?.title to api.search(picked?.location.orEmpty()).size
    }

    @Test
    fun `시나리오마다 의도한 일정이 골라진다`() = runTest {
        // 하루 중 아무 때나 시연해도 같은 결과여야 한다: 아침, 저녁 9시 직전, 자정 직전
        for (time in listOf("2026-11-14T08:00:00+09:00", "2026-11-14T20:50:00+09:00", "2026-11-14T23:50:00+09:00")) {
            val now = OffsetDateTime.parse(time).toInstant()
            assertEquals(time, "고객사 미팅" to 1, keyEventAndPlaces(1, now))
            assertEquals(time, "출장 비행기" to 1, keyEventAndPlaces(2, now))
            assertEquals(time, "엄마 생신 저녁" to 0, keyEventAndPlaces(3, now)) // 장소를 못 찾아 제안 없음
            assertEquals(time, "회사 출근" to 1, keyEventAndPlaces(4, now)) // 유치원에 있으니 다음 일정
            assertEquals(time, "유치원 등원" to 1, keyEventAndPlaces(5, now)) // 유치원에 못 갔으니 늦은 일정
        }
    }

    @Test
    fun `연결 해제하면 시연용 QR만 나오고 linked를 켜야 다시 일정이 온다`() = runTest {
        val api = FakeServerApi()
        assertEquals(PairingStatus.LINKED, api.pairingStatus())
        api.deletePairing()
        api.createPairing()
        assertEquals(PairingStatus.PENDING, api.pairingStatus())
        val failed = runCatching { api.calendarEvents(OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1)) }
        assertEquals(ApiError.NotLinked, (failed.exceptionOrNull() as ApiException).error)
        api.start() // AppContainer.chooseServerMode가 하는 일
        assertEquals(PairingStatus.LINKED, api.pairingStatus())
    }
}
