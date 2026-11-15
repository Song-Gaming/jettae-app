package com.example.hyundai.data.remote

import kotlinx.coroutines.delay
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 실제 서버 없이 시연·심사할 때 쓰는 가짜 서버. 응답 형식은 계약서 예시와 같다.
 * [scenario] 번호(1부터)마다 출발 제안을 시험할 일정이 지금 시각 기준으로 만들어진다.
 * 처음에는 연동된 상태이고, [연결 해제] 후 [구글 캘린더 연결하기]를 누르면 시연용 QR을 보여 준다.
 * 그 QR은 찍어도 연동되지 않고, QR 화면 아래에서 가짜 일정을 고르면 [linked]가 다시 true가 된다.
 * 상태는 메모리에만 있다.
 */
class FakeServerApi(
    private val scenario: () -> Int = { 1 },
    private val clock: Clock = Clock.systemDefaultZone(),
) : ServerApi {

    @Volatile
    var linked = true
        private set

    /** 일정 시각의 기준. 새로고침마다 시각이 바뀌어 알림과 목록이 어긋나지 않게 잠시 고정한다 */
    @Volatile
    private var anchor: ZonedDateTime? = null

    /** 가짜 일정을 (다시) 골랐을 때: 연동하고 일정 시각을 지금 기준으로 새로 만든다 */
    fun start() {
        linked = true
        anchor = null
    }

    override suspend fun createPairing(): Pairing {
        delay(300)
        if (linked) throw ApiException(ApiError.AlreadyLinked)
        return Pairing(qrUrl = "https://example.com/pleos-demo-pairing", expiresInSec = 300)
    }

    // 연동 전에는 계속 "기다리는 중": QR 화면은 가짜 일정을 고를 때까지 QR을 보여 주고, 일정 화면은 연결하기 버튼을 보여 준다
    override suspend fun pairingStatus(): PairingStatus = if (linked) PairingStatus.LINKED else PairingStatus.PENDING

    override suspend fun deletePairing() {
        linked = false
    }

    override suspend fun calendarEvents(from: OffsetDateTime, to: OffsetDateTime): List<RemoteEvent> {
        delay(400)
        if (!linked) throw ApiException(ApiError.NotLinked)
        return scenarioEvents(scenario(), ZonedDateTime.now(clock)).filter { event ->
            val start = event.startInstantOrDate()
            val end = event.endInstantOrDate()
            end.isAfter(from) && start.isBefore(to)
        }
    }

    /** 가짜 일정에 쓴 장소만 찾는다. 나머지는 결과 없음. 주소 검색 결과처럼 id·category·distance가 없다 */
    override suspend fun search(query: String): List<SearchPlace> {
        delay(200)
        val place = PLACES.firstOrNull { query.contains(it.key) } ?: return emptyList()
        return listOf(
            SearchPlace(
                id = null,
                name = query.substringBefore('(').trim(),
                category = null,
                address = place.address,
                roadAddress = place.address,
                latitude = place.latitude,
                longitude = place.longitude,
                distanceM = null,
            )
        )
    }

    private fun scenarioEvents(scenario: Int, now: ZonedDateTime): List<RemoteEvent> {
        val updated = now.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        // 30분이 지나면 기준을 다시 잡아 오래 켜 둔 시연에서도 일정이 지나가 버리지 않게 함
        val base = anchor?.takeIf { it.plusMinutes(ANCHOR_MINUTES).isAfter(now) }
            ?: now.truncatedTo(ChronoUnit.MINUTES).also { anchor = it }
        fun timed(id: String, start: ZonedDateTime, minutes: Long, title: String, location: String?) =
            RemoteEvent(
                id = "fake-s$scenario-$id",
                title = title,
                location = location,
                allDay = false,
                start = start.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                end = start.plusMinutes(minutes).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                updated = updated,
            )

        // 출발 제안 대상(장소 있음, 3시간 안)은 시나리오 일정뿐이다.
        // 4·5는 이미 시작한 유치원 일정 + 40분 뒤 회사: 차가 유치원에 있으면(4) 회사, 아직 못 갔으면(5) 유치원을 제안
        val office = { timed("office", base.plusMinutes(40), 60, "회사 출근", "판교역(경기 성남시 분당구 판교역로 160)") }
        val keys = when (scenario) {
            2 -> listOf(timed("key", base.plusMinutes(25), 120, "출장 비행기", "인천국제공항 제1여객터미널(인천 중구 공항로 272)"))
            3 -> listOf(timed("key", base.plusMinutes(60), 120, "엄마 생신 저녁", "엄마네 집"))
            4 -> listOf(timed("key", base.minusMinutes(20), 60, "유치원 등원", "햇살유치원(경기 성남시 수정구 시흥동 287)"), office())
            5 -> listOf(timed("key", base.minusMinutes(20), 60, "유치원 등원", "새싹유치원(서울 송파구 올림픽로 300)"), office())
            else -> listOf(timed("key", base.plusMinutes(50), 60, "고객사 미팅", "강남역 2호선(서울 강남구 강남대로 396)"))
        }
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        return keys + listOf(
            timed("dinner", today.atTime(LocalTime.of(21, 0)).atZone(now.zone), 60, "가족 통화", null),
            timed("weekly", tomorrow.atTime(LocalTime.of(8, 30)).atZone(now.zone), 60, "주간 회의", "판교역(경기 성남시 분당구 판교역로 160)"),
            RemoteEvent(
                id = "fake-s$scenario-trip",
                title = "제주 여행",
                location = null,
                allDay = true,
                start = today.plusDays(2).toString(),
                end = today.plusDays(4).toString(),
                updated = updated,
            ),
        )
    }

    private fun RemoteEvent.startInstantOrDate(): OffsetDateTime =
        if (allDay) LocalDate.parse(start).atStartOfDay(clock.zone).toOffsetDateTime() else OffsetDateTime.parse(start)

    private fun RemoteEvent.endInstantOrDate(): OffsetDateTime =
        if (allDay) LocalDate.parse(end).atStartOfDay(clock.zone).toOffsetDateTime() else OffsetDateTime.parse(end)

    private class Place(val key: String, val address: String, val latitude: Double, val longitude: Double)

    private companion object {
        const val ANCHOR_MINUTES = 30L

        // 좌표는 시연용 근삿값
        val PLACES = listOf(
            Place("강남대로 396", "서울 강남구 강남대로 396", 37.49795, 127.02762),
            Place("공항로 272", "인천 중구 공항로 272", 37.44916, 126.45061),
            Place("판교역로 160", "경기 성남시 분당구 판교역로 160", 37.39470, 127.11120),
            // 에뮬레이터 기본 차 위치(성남 시흥동)와 같은 곳: 시나리오 4에서 "차가 유치원에 있음"이 된다
            Place("시흥동 287", "경기 성남시 수정구 시흥동 287", 37.41272, 127.09468),
            Place("올림픽로 300", "서울 송파구 올림픽로 300", 37.51256, 127.10251),
        )
    }
}
