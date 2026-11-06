package com.example.hyundaiapp.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorTest {

    private fun body(code: String) = """{ "code": "$code", "message": "개발자용" }"""

    @Test
    fun `계약서의 에러 code를 모두 구분한다`() {
        assertEquals(ApiError.InvalidRequest, ApiError.from(400, body("invalid_request"), null))
        assertEquals(ApiError.InvalidToken, ApiError.from(401, body("invalid_token"), null))
        assertEquals(ApiError.NotLinked, ApiError.from(409, body("not_linked"), null))
        assertEquals(ApiError.ReauthRequired, ApiError.from(409, body("reauth_required"), null))
        assertEquals(ApiError.AlreadyLinked, ApiError.from(409, body("already_linked"), null))
        assertEquals(ApiError.DailyLimit, ApiError.from(429, body("daily_limit"), null))
        assertEquals(ApiError.Upstream, ApiError.from(502, body("upstream_error"), null))
    }

    @Test
    fun `rate_limited는 Retry-After 초를 담는다`() {
        assertEquals(ApiError.RateLimited(30), ApiError.from(429, body("rate_limited"), "30"))
        assertEquals(ApiError.RateLimited(null), ApiError.from(429, body("rate_limited"), null))
    }

    @Test
    fun `본문이 JSON이 아니면 HTTP 상태로 판단한다`() {
        assertEquals(ApiError.InvalidToken, ApiError.from(401, "<html>Unauthorized</html>", null))
        assertEquals(ApiError.Upstream, ApiError.from(504, null, null))
        assertEquals(ApiError.Unknown(500), ApiError.from(500, "", null))
    }
}
