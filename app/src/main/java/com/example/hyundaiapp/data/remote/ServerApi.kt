package com.example.hyundaiapp.data.remote

import java.time.OffsetDateTime

/**
 * 캘린더 연동에 쓰는 서버 API. 실패는 모두 [ApiException]으로 던진다.
 * 실제 서버는 [HttpServerApi], 서버가 없을 때는 [FakeServerApi].
 */
interface ServerApi {
    /** POST /pairing: QR에 넣을 URL 발급 */
    suspend fun createPairing(): Pairing

    /** GET /pairing/status */
    suspend fun pairingStatus(): PairingStatus

    /** DELETE /pairing: 구글 연동 해제 */
    suspend fun deletePairing()

    /** GET /calendar/events: from~to와 겹치는 일정 */
    suspend fun calendarEvents(from: OffsetDateTime, to: OffsetDateTime): List<RemoteEvent>
}

data class Pairing(val qrUrl: String, val expiresInSec: Int)

enum class PairingStatus {
    NONE, PENDING, LINKED, EXPIRED, FAILED, REAUTH_REQUIRED;

    companion object {
        fun from(value: String): PairingStatus = when (value) {
            "none" -> NONE
            "pending" -> PENDING
            "linked" -> LINKED
            "expired" -> EXPIRED
            "failed" -> FAILED
            "reauth_required" -> REAUTH_REQUIRED
            else -> throw ApiException(ApiError.Unknown(200))
        }
    }
}

/**
 * GET /calendar/events의 일정 하나. 서버 형식 그대로 둔다.
 * all_day면 start, end가 날짜만 오고 end는 포함하지 않는다.
 */
data class RemoteEvent(
    val id: String,
    val title: String?,
    val location: String?,
    val allDay: Boolean,
    val start: String,
    val end: String,
    val updated: String,
)
