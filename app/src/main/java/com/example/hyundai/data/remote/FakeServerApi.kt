package com.example.hyundai.data.remote

import kotlinx.coroutines.delay
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * 서버 주소가 없을 때 쓰는 가짜 서버. 응답 형식은 계약서 예시와 같다.
 * QR을 띄우고 상태를 몇 번 조회하면 자동으로 연동 완료가 되어 QR 흐름을 끝까지 시연할 수 있다.
 * 상태는 메모리에만 있어 앱을 다시 시작하면 미연동으로 돌아간다.
 */
class FakeServerApi(private val clock: Clock = Clock.systemDefaultZone()) : ServerApi {

    @Volatile
    private var linked = false

    /** QR 발급 후 linked가 되기까지 남은 pending 응답 수. -1이면 QR 없음 */
    @Volatile
    private var pendingPolls = -1

    override suspend fun createPairing(): Pairing {
        delay(300)
        if (linked) throw ApiException(ApiError.AlreadyLinked)
        pendingPolls = PENDING_POLLS_BEFORE_LINK
        return Pairing(qrUrl = "https://example.com/pleos-demo-pairing", expiresInSec = 300)
    }

    override suspend fun pairingStatus(): PairingStatus {
        delay(200)
        return when {
            linked -> PairingStatus.LINKED
            pendingPolls < 0 -> PairingStatus.NONE
            pendingPolls == 0 -> {
                linked = true
                pendingPolls = -1
                PairingStatus.LINKED
            }
            else -> {
                pendingPolls--
                PairingStatus.PENDING
            }
        }
    }

    override suspend fun deletePairing() {
        linked = false
        pendingPolls = -1
    }

    override suspend fun calendarEvents(from: OffsetDateTime, to: OffsetDateTime): List<RemoteEvent> {
        delay(400)
        if (!linked) throw ApiException(ApiError.NotLinked)
        val now = ZonedDateTime.now(clock)
        return sampleEvents(now.toLocalDate(), now).filter { event ->
            val start = event.startInstantOrDate()
            val end = event.endInstantOrDate()
            end.isAfter(from) && start.isBefore(to)
        }
    }

    private fun sampleEvents(today: LocalDate, now: ZonedDateTime): List<RemoteEvent> {
        val updated = now.toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        fun timed(id: String, day: LocalDate, start: LocalTime, end: LocalTime, title: String, location: String?) =
            RemoteEvent(
                id = id,
                title = title,
                location = location,
                allDay = false,
                start = day.atTime(start).atZone(now.zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                end = day.atTime(end).atZone(now.zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                updated = updated,
            )

        val tomorrow = today.plusDays(1)
        return listOf(
            timed("fake-$today-1", today, LocalTime.of(9, 0), LocalTime.of(10, 0), "치과 예약", "강남역 3번 출구"),
            timed("fake-$today-2", today, LocalTime.of(16, 0), LocalTime.of(16, 30), "유치원 하원", "해님유치원"),
            timed("fake-$today-3", today, LocalTime.of(19, 0), LocalTime.of(20, 30), "가족 저녁", null),
            timed("fake-$tomorrow-1", tomorrow, LocalTime.of(8, 30), LocalTime.of(9, 30), "주간 회의", "경기 성남시 분당구 판교역로 166"),
            RemoteEvent(
                id = "fake-trip-${today.plusDays(2)}",
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

    private companion object {
        const val PENDING_POLLS_BEFORE_LINK = 3
    }
}
