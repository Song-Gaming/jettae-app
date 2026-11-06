package com.example.hyundaiapp.ui.pairing

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hyundaiapp.appContainer
import com.example.hyundaiapp.data.calendar.CalendarRepository
import com.example.hyundaiapp.data.remote.ApiError
import com.example.hyundaiapp.data.remote.ApiException
import com.example.hyundaiapp.data.remote.PairingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PairingUiState {
    data object Loading : PairingUiState
    data class ShowQr(val qr: Bitmap, val remainingSec: Int) : PairingUiState
    data object Linked : PairingUiState
    data class Failed(val reason: FailReason) : PairingUiState
}

enum class FailReason { LOGIN_FAILED, EXPIRED, NETWORK }

/**
 * QR 연동 흐름: POST /pairing으로 QR을 받고, 3초마다 GET /pairing/status를 확인한다.
 * 화면이 안 보이는 동안(onStop 이후)에는 폴링을 멈췄다가 다시 보이면 이어서 한다.
 */
class PairingViewModel(private val repository: CalendarRepository) : ViewModel() {

    private val _state = MutableStateFlow<PairingUiState>(PairingUiState.Loading)
    val state: StateFlow<PairingUiState> = _state

    private val visible = MutableStateFlow(false)
    private var job: Job? = null

    init {
        start()
    }

    fun setVisible(isVisible: Boolean) {
        visible.value = isVisible
    }

    fun retry() = start()

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch { runPairing() }
    }

    private suspend fun runPairing() {
        var renewals = 0
        while (true) {
            _state.value = PairingUiState.Loading
            awaitVisible()
            val pairing = try {
                repository.createPairing()
            } catch (e: ApiException) {
                when (val error = e.error) {
                    ApiError.AlreadyLinked -> return linked()
                    is ApiError.RateLimited -> {
                        delay((error.retryAfterSec ?: DEFAULT_RETRY_SEC) * 1000L)
                        continue
                    }
                    else -> return fail(FailReason.NETWORK)
                }
            }
            val qr = withContext(Dispatchers.Default) { QrBitmap.encode(pairing.qrUrl, QR_SIZE_PX) }
            val expiresAt = SystemClock.elapsedRealtime() + pairing.expiresInSec * 1000L
            when (pollUntilDone(qr, expiresAt)) {
                PairingStatus.LINKED -> return linked()
                PairingStatus.FAILED -> return fail(FailReason.LOGIN_FAILED)
                // 만료되면 새 QR을 자동으로 받되, 아무도 찍지 않는 화면에서 무한히 발급하지 않게 횟수를 제한한다
                else -> if (++renewals >= MAX_AUTO_RENEWALS) return fail(FailReason.EXPIRED)
            }
        }
    }

    /** 끝난 상태(LINKED, FAILED, EXPIRED)를 돌려준다. 네트워크 오류는 다음 주기에 다시 묻는다 */
    private suspend fun pollUntilDone(qr: Bitmap, expiresAt: Long): PairingStatus {
        var tick = 0
        while (true) {
            awaitVisible()
            val remainingMs = expiresAt - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) return PairingStatus.EXPIRED
            _state.value = PairingUiState.ShowQr(qr, (remainingMs / 1000).toInt())
            delay(TICK_MS)
            if (++tick % POLL_EVERY_TICKS != 0) continue
            val status = try {
                repository.pairingStatus()
            } catch (e: ApiException) {
                val error = e.error
                if (error is ApiError.RateLimited) delay((error.retryAfterSec ?: DEFAULT_RETRY_SEC) * 1000L)
                continue
            }
            when (status) {
                PairingStatus.PENDING -> Unit
                PairingStatus.LINKED, PairingStatus.FAILED, PairingStatus.EXPIRED -> return status
                // QR을 받았는데 none이나 reauth_required가 오면 코드가 무효가 된 것으로 보고 새로 받는다
                PairingStatus.NONE, PairingStatus.REAUTH_REQUIRED -> return PairingStatus.EXPIRED
            }
        }
    }

    private suspend fun awaitVisible() {
        visible.first { it }
    }

    private fun linked() {
        _state.value = PairingUiState.Linked
    }

    private fun fail(reason: FailReason) {
        _state.value = PairingUiState.Failed(reason)
    }

    companion object {
        private const val QR_SIZE_PX = 640
        private const val TICK_MS = 1_000L
        private const val POLL_EVERY_TICKS = 3
        private const val MAX_AUTO_RENEWALS = 3
        private const val DEFAULT_RETRY_SEC = 10

        val Factory = viewModelFactory {
            initializer { PairingViewModel(this[APPLICATION_KEY]!!.appContainer.calendarRepository) }
        }
    }
}
