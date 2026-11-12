package com.example.hyundai.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hyundai.appContainer
import com.example.hyundai.data.places.FrequentPlace
import com.example.hyundai.data.places.PersonalMatch
import com.example.hyundai.data.places.PlaceRepository
import com.example.hyundai.data.places.PlaceRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlacesUiState(
    val places: List<FrequentPlace> = emptyList(),
    val lastQuery: String? = null,
    val match: PersonalMatch? = null,
    val suggestion: FrequentPlace? = null,
)

/** 자주 가는 곳 확인 화면. 분류·점수·"유치원 → 장소" 결과를 눈으로 확인하는 용도 */
class PlacesViewModel(private val repository: PlaceRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state

    fun load() {
        viewModelScope.launch { reload() }
    }

    fun seedDemo() {
        viewModelScope.launch {
            repository.replaceWithDemoVisits()
            _state.update { PlacesUiState() }
            reload()
        }
    }

    fun clear() {
        viewModelScope.launch {
            repository.clearAll()
            _state.update { PlacesUiState() }
            reload()
        }
    }

    fun resolve(query: String) {
        viewModelScope.launch {
            val match = repository.resolvePersonal(query)
            _state.update { it.copy(lastQuery = query, match = match) }
        }
    }

    fun setRole(placeKey: String, role: PlaceRole?) {
        viewModelScope.launch {
            repository.setRole(placeKey, role)
            reload()
            _state.value.lastQuery?.let { query ->
                _state.update { it.copy(match = null) }
                resolve(query)
            }
        }
    }

    private suspend fun reload() {
        val places = repository.places()
        // 확인 화면에는 현재 위치가 없으므로 위치 제외 없이 추천한다
        val suggestion = repository.suggestDestination(null, null)
        _state.update { it.copy(places = places, suggestion = suggestion) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PlacesViewModel(this[APPLICATION_KEY]!!.appContainer.placeRepository) }
        }
    }
}
