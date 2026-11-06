package com.example.hyundaiapp.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hyundaiapp.appContainer
import com.example.hyundaiapp.data.calendar.CalendarRepository
import com.example.hyundaiapp.data.calendar.SyncResult
import com.example.hyundaiapp.data.local.CalendarEventEntity
import com.example.hyundaiapp.data.remote.ApiError
import com.example.hyundaiapp.data.remote.ApiException
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
)

class CalendarViewModel(
    private val repository: CalendarRepository,
    val isFakeServer: Boolean,
) : ViewModel() {

    val events: StateFlow<List<CalendarEventEntity>> =
        repository.observeEvents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(CalendarUiState(lastSyncMillis = repository.lastSyncMillis))
    val state: StateFlow<CalendarUiState> = _state

    fun refresh() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            val result = repository.refresh()
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
        val Factory = viewModelFactory {
            initializer {
                val container = this[APPLICATION_KEY]!!.appContainer
                CalendarViewModel(container.calendarRepository, container.isFakeServer)
            }
        }
    }
}
