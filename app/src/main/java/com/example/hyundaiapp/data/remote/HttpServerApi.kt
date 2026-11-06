package com.example.hyundaiapp.data.remote

import com.example.hyundaiapp.data.token.TokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * docs/api-contract.md를 따르는 실제 서버 클라이언트.
 * 기기 토큰이 없으면 POST /devices로 먼저 발급받고, 401이 오면 토큰을 지워 다음 호출 때 다시 발급받는다.
 * 토큰과 응답 본문은 로그에 남기지 않는다.
 */
class HttpServerApi(
    baseUrl: String,
    private val tokens: TokenStorage,
    private val client: OkHttpClient = defaultClient(),
) : ServerApi {

    private val base: HttpUrl = (if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/").toHttpUrl()
    private val tokenMutex = Mutex()

    override suspend fun createPairing(): Pairing {
        val json = authorized("POST", url("pairing"))
        return parsing { Pairing(qrUrl = json.getString("qr_url"), expiresInSec = json.getInt("expires_in")) }
    }

    override suspend fun pairingStatus(): PairingStatus {
        val json = authorized("GET", url("pairing/status"))
        return parsing { PairingStatus.from(json.getString("status")) }
    }

    override suspend fun deletePairing() {
        authorized("DELETE", url("pairing"))
    }

    override suspend fun calendarEvents(from: OffsetDateTime, to: OffsetDateTime): List<RemoteEvent> {
        val json = authorized("GET", calendarEventsUrl(from, to))
        return parsing {
            val array = json.getJSONArray("events")
            List(array.length()) { i -> array.getJSONObject(i).toRemoteEvent() }
        }
    }

    /** 응답 형식이 계약서와 다르면 앱이 죽지 않게 ApiException으로 바꾼다 */
    private inline fun <T> parsing(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw ApiException(ApiError.Unknown(200), e)
    }

    /** addQueryParameter가 오프셋의 +를 %2B로 인코딩한다(계약서 공통 규칙) */
    internal fun calendarEventsUrl(from: OffsetDateTime, to: OffsetDateTime): HttpUrl =
        url("calendar/events").newBuilder()
            .addQueryParameter("from", from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .addQueryParameter("to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .build()

    private fun url(path: String): HttpUrl = base.resolve(path)!!

    private suspend fun deviceToken(): String = tokenMutex.withLock {
        tokens.load() ?: run {
            val json = execute(Request.Builder().url(url("devices")).post(EMPTY_BODY).build())
            parsing { json.getString("device_token") }.also(tokens::save)
        }
    }

    private suspend fun authorized(method: String, url: HttpUrl): JSONObject {
        val request = Request.Builder()
            .url(url)
            .method(method, if (method == "POST") EMPTY_BODY else null)
            .header("Authorization", "Bearer ${deviceToken()}")
            .build()
        try {
            return execute(request)
        } catch (e: ApiException) {
            if (e.error == ApiError.InvalidToken) tokens.clear()
            throw e
        }
    }

    private suspend fun execute(request: Request): JSONObject = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    throw ApiException(ApiError.from(response.code, body, response.header("Retry-After")))
                }
                if (body.isNullOrBlank()) JSONObject() else JSONObject(body)
            }
        } catch (e: IOException) {
            throw ApiException(ApiError.Network, e)
        } catch (e: JSONException) {
            throw ApiException(ApiError.Unknown(200), e)
        }
    }

    private companion object {
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
}

private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)

private fun JSONObject.toRemoteEvent() = RemoteEvent(
    id = getString("id"),
    title = stringOrNull("title"),
    location = stringOrNull("location"),
    allDay = getBoolean("all_day"),
    start = getString("start"),
    end = getString("end"),
    updated = getString("updated"),
)
