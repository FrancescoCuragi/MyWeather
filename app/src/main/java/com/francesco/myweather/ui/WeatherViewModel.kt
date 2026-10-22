package com.francesco.myweather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.francesco.myweather.data.AirQuality
import com.francesco.myweather.data.CachedForecast
import com.francesco.myweather.data.ForecastResponse
import com.francesco.myweather.data.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState

    data class Success(
        val placeName: String,
        val forecast: ForecastResponse,
        val updatedAt: Long,
        val airQuality: AirQuality? = null,
        val isRefreshing: Boolean = false,
        /** True when the last refresh failed and the saved forecast is shown instead. */
        val isOffline: Boolean = false,
    ) : WeatherUiState

    data class Error(val message: String) : WeatherUiState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application.cacheDir)

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Shows the saved forecast straight away (if any), then replaces it with a fresh one. */
    fun refresh() {
        viewModelScope.launch {
            val shown = (_state.value as? WeatherUiState.Success)
                ?: repository.loadCached(BRUSSELS_LAT, BRUSSELS_LON)?.toUiState()
            _state.value = shown?.copy(isRefreshing = true) ?: WeatherUiState.Loading

            repository.fetch(BRUSSELS_LAT, BRUSSELS_LON)
                .onSuccess { _state.value = it.toUiState() }
                .onFailure {
                    _state.value = shown?.copy(isRefreshing = false, isOffline = true)
                        ?: WeatherUiState.Error(it.message ?: "Could not load forecast")
                }
        }
    }

    private fun CachedForecast.toUiState() =
        WeatherUiState.Success("Brussels", forecast, fetchedAt, airQuality)

    private companion object {
        // Placeholder until saved locations / GPS arrive in Phase 2.
        const val BRUSSELS_LAT = 50.8503
        const val BRUSSELS_LON = 4.3517
    }
}
