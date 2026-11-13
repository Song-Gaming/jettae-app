package com.example.hyundai.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hyundai.appContainer
import com.example.hyundai.data.calendar.CalendarRepository
import com.example.hyundai.data.calendar.SyncResult
import com.example.hyundai.data.local.CalendarEventEntity
import com.example.hyundai.data.remote.ApiError
import com.example.hyundai.data.remote.ApiException
import com.example.hyundai.data.remote.ServerModeStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LinkState { UNKNOWN, LINKED, NEEDS_LINK }

/** 저장된 일정 위에 보여 줄 안내. 일정 목록은 실패해도 캐시를 그대로 보여 준다 */
enum class Notice { OFFLINE, SERVER_ERROR, RATE_LIMITED, UNLINK_FAILED }

data class CalendarUiState(
    val link: LinkState = LinkState.UNKNOWN,
    val syncing: Boolean = false,
    val lastSyncMillis: Long? = null,
    val notice: Notice? = null,
    /** 429를 받아 다시 요청할 수 있을 때까지 남은 초. null이면 제한 없음 */
    val retryAfterSec: Int? = null,
)

class CalendarViewModel(
    private val repository: CalendarRepository,
    val serverMode: ServerModeStore,
    private val chooseServerMode: (Int) -> Unit,
) : ViewModel() {

    val events: StateFlow<List<CalendarEventEntity>> =
        repository.observeEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(CalendarUiState(lastSyncMillis = repository.lastSyncMillis))
    val state: StateFlow<CalendarUiState> = _state

    private var cooldownJob: Job? = null

    /** 화면에 들어올 때: 연동 여부를 먼저 확인하고 일정을 받는다 */
    fun start() = sync { repository.syncIfLinked() }

    /** 새로고침 버튼 */
    fun refresh() = sync { repository.refresh() }

    private fun sync(block: suspend () -> SyncResult) {
        val current = _state.value
        if (current.syncing || current.retryAfterSec != null) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            val result = block()
            _state.update { current ->
                when (result) {
                    is SyncResult.Success -> current.copy(
                        link = LinkState.LINKED,
                        lastSyncMillis = repository.lastSyncMillis,
                        notice = null,
                    )
                    SyncResult.NeedsLink -> current.copy(link = LinkState.NEEDS_LINK, notice = null)
                    is SyncResult.Failed -> current.copy(notice = result.error.toNotice())
                }.copy(syncing = false)
            }
            val error = (result as? SyncResult.Failed)?.error
            if (error is ApiError.RateLimited) startCooldown(error.retryAfterSec ?: DEFAULT_RETRY_SEC)
        }
    }

    /** 계약서: 429면 Retry-After 초만큼 기다린다. 그동안 새로고침을 막고 남은 초를 보여 준다 */
    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _state.update { it.copy(retryAfterSec = remaining) }
                delay(1_000)
            }
            _state.update { it.copy(retryAfterSec = null, notice = null) }
        }
    }

    /** 실제 서버(0)나 가짜 일정 시나리오(1~)로 바꾸고 그 서버에서 일정을 다시 받는다 */
    fun changeServerMode(mode: Int) {
        if (mode == serverMode.mode) return
        chooseServerMode(mode)
        viewModelScope.launch {
            repository.clearCache()
            _state.update { CalendarUiState() }
            start()
        }
    }

    fun unlink() {
        viewModelScope.launch {
            try {
                repository.unlink()
                _state.update { CalendarUiState(link = LinkState.NEEDS_LINK) }
            } catch (e: ApiException) {
                _state.update { it.copy(notice = Notice.UNLINK_FAILED) }
            }
        }
    }

    private fun ApiError.toNotice(): Notice = when (this) {
        ApiError.Network -> Notice.OFFLINE
        is ApiError.RateLimited -> Notice.RATE_LIMITED
        else -> Notice.SERVER_ERROR
    }

    companion object {
        private const val DEFAULT_RETRY_SEC = 10

        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                CalendarViewModel(container.calendarRepository, container.serverMode, container::chooseServerMode)
            }
        }
    }
}
