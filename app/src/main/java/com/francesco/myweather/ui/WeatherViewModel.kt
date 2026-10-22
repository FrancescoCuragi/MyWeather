package com.francesco.myweather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.francesco.myweather.data.ForecastResponse
import com.francesco.myweather.data.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Success(val placeName: String, val forecast: ForecastResponse) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(
    private val repository: WeatherRepository = WeatherRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            repository.forecast(BRUSSELS_LAT, BRUSSELS_LON)
                .onSuccess { _state.value = WeatherUiState.Success("Brussels", it) }
                .onFailure { _state.value = WeatherUiState.Error(it.message ?: "Could not load forecast") }
        }
    }

    private companion object {
        // Placeholder until saved locations / GPS arrive in Phase 2.
        const val BRUSSELS_LAT = 50.8503
        const val BRUSSELS_LON = 4.3517
    }
}
