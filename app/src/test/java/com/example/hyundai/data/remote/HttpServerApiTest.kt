package com.example.hyundai.data.remote

import com.example.hyundai.data.token.TokenStorage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.OffsetDateTime

class HttpServerApiTest {

    private class MemoryTokens(var token: String? = null) : TokenStorage {
        override fun load() = token
        override fun save(token: String) { this.token = token }
        override fun clear() { token = null }
    }

    private val server = MockWebServer()
    private lateinit var tokens: MemoryTokens
    private lateinit var api: HttpServerApi

    @Before
    fun setUp() {
        server.start()
        tokens = MemoryTokens()
        api = HttpServerApi(server.url("/").toString(), tokens)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `토큰이 없으면 devices로 발급받고 Bearer로 붙인다`() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "device_token": "dvc_test" }"""))
        server.enqueue(MockResponse().setBody("""{ "status": "pending" }"""))

        assertEquals(PairingStatus.PENDING, api.pairingStatus())

        val devices = server.takeRequest()
        assertEquals("POST", devices.method)
        assertEquals("/devices", devices.path)
        assertNull(devices.getHeader("Authorization"))

        val status = server.takeRequest()
        assertEquals("/pairing/status", status.path)
        assertEquals("Bearer dvc_test", status.getHeader("Authorization"))
        assertEquals("dvc_test", tokens.token)
    }

    @Test
    fun `일정 조회 URL에서 오프셋의 +는 %2B로 인코딩된다`() {
        val url = api.calendarEventsUrl(
            OffsetDateTime.parse("2026-11-05T00:00:00+09:00"),
            OffsetDateTime.parse("2026-11-14T00:00:00+09:00"),
        )
        assertEquals(
            "from=2026-11-05T00%3A00%3A00%2B09%3A00&to=2026-11-14T00%3A00%3A00%2B09%3A00",
            url.encodedQuery,
        )
    }

    @Test
    fun `일정 응답의 null 필드와 종일 일정을 그대로 읽는다`() = runTest {
        tokens.token = "dvc_test"
        server.enqueue(
            MockResponse().setBody(
                """
                { "events": [
                  { "id": "f6", "title": "제주 여행", "location": null, "all_day": true,
                    "start": "2026-11-07", "end": "2026-11-09", "updated": "2026-11-01T02:30:00Z" }
                ] }
                """.trimIndent()
            )
        )

        val events = api.calendarEvents(
            OffsetDateTime.parse("2026-11-05T00:00:00+09:00"),
            OffsetDateTime.parse("2026-11-14T00:00:00+09:00"),
        )

        assertEquals(1, events.size)
        assertNull(events[0].location)
        assertTrue(events[0].allDay)
        assertEquals("2026-11-09", events[0].end)
    }

    @Test
    fun `401이 오면 토큰을 지워 다음 호출 때 다시 발급받게 한다`() = runTest {
        tokens.token = "dvc_old"
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{ "code": "invalid_token", "message": "x" }"""))

        try {
            api.pairingStatus()
            fail("ApiException이 나야 함")
        } catch (e: ApiException) {
            assertEquals(ApiError.InvalidToken, e.error)
        }
        assertNull(tokens.token)
    }

    @Test
    fun `409 not_linked는 NotLinked로 던진다`() = runTest {
        tokens.token = "dvc_test"
        server.enqueue(MockResponse().setResponseCode(409).setBody("""{ "code": "not_linked", "message": "x" }"""))

        try {
            api.calendarEvents(OffsetDateTime.now(), OffsetDateTime.now().plusDays(1))
            fail("ApiException이 나야 함")
        } catch (e: ApiException) {
            assertEquals(ApiError.NotLinked, e.error)
        }
        assertEquals("dvc_test", tokens.token)
    }

    @Test
    fun `search는 일정 장소를 그대로 보내고 주소 검색 결과의 null 필드를 받는다`() = runTest {
        tokens.token = "dvc_test"
        server.enqueue(
            MockResponse().setBody(
                """{ "places": [ { "id": null, "name": "서초 금융 캠퍼스", "category": null,
                  "address": "서울 서초구 서초동 1641-10", "road_address": "서울 서초구 사임당로23길 27",
                  "latitude": 37.4919, "longitude": 127.0203, "distance_m": null } ] }"""
            )
        )

        val places = api.search("삼성금융캠퍼스(서울 서초구 사임당로23길 27)")

        val request = server.takeRequest()
        assertEquals("삼성금융캠퍼스(서울 서초구 사임당로23길 27)", request.requestUrl?.queryParameter("query"))
        val place = places.single()
        assertNull(place.id)
        assertNull(place.distanceM)
        assertEquals(37.4919, place.latitude, 0.0)
    }
}
