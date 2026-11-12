package com.example.hyundai.data.remote

import org.json.JSONException
import org.json.JSONObject

/** 서버 에러 code(docs/api-contract.md 에러 표)와 네트워크 실패를 앱 안에서 다루는 형태 */
sealed interface ApiError {
    data object InvalidRequest : ApiError
    data object InvalidToken : ApiError
    data object NotLinked : ApiError
    data object ReauthRequired : ApiError
    data object AlreadyLinked : ApiError
    data class RateLimited(val retryAfterSec: Int?) : ApiError
    data object DailyLimit : ApiError
    data object Upstream : ApiError
    data object Network : ApiError
    data class Unknown(val httpCode: Int) : ApiError

    companion object {
        /** HTTP 상태와 에러 본문으로 ApiError를 만든다. code를 못 읽으면 HTTP 상태로 판단한다 */
        fun from(httpCode: Int, body: String?, retryAfter: String?): ApiError {
            val code = try {
                body?.let { JSONObject(it).optString("code") }
            } catch (e: JSONException) {
                null
            }
            val retryAfterSec = retryAfter?.trim()?.toIntOrNull()
            return when (code) {
                "invalid_request" -> InvalidRequest
                "invalid_token" -> InvalidToken
                "not_linked" -> NotLinked
                "reauth_required" -> ReauthRequired
                "already_linked" -> AlreadyLinked
                "rate_limited" -> RateLimited(retryAfterSec)
                "daily_limit" -> DailyLimit
                "upstream_error" -> Upstream
                else -> when (httpCode) {
                    400 -> InvalidRequest
                    401 -> InvalidToken
                    429 -> RateLimited(retryAfterSec)
                    502, 503, 504 -> Upstream
                    else -> Unknown(httpCode)
                }
            }
        }
    }
}

class ApiException(val error: ApiError, cause: Throwable? = null) : Exception(error.toString(), cause)
