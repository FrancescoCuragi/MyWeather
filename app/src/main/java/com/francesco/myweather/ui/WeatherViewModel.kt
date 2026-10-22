package com.francesco.myweather.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.francesco.myweather.data.AirQuality
import com.francesco.myweather.data.CachedForecast
import com.francesco.myweather.data.ForecastResponse
import com.francesco.myweather.data.GeoResult
import com.francesco.myweather.data.LocationProvider
import com.francesco.myweather.data.Place
import com.francesco.myweather.data.SettingsRepository
import com.francesco.myweather.data.TemperatureUnit
import com.francesco.myweather.data.UserSettings
import com.francesco.myweather.data.WindUnit
import com.francesco.myweather.data.WeatherRepository
import com.francesco.myweather.data.db.AppDatabase
import com.francesco.myweather.data.db.SavedPlace
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val places: List<GeoResult>) : SearchState
    data class Error(val message: String) : SearchState
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application.cacheDir)
    private val placeDao = AppDatabase.get(application).placeDao()
    private val locationProvider = LocationProvider(application)
    private val settingsRepository = SettingsRepository(application)

    val settings: StateFlow<UserSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    fun setTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { settingsRepository.setTemperatureUnit(unit) }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch { settingsRepository.setWindUnit(unit) }
    }

    fun setUse24Hour(value: Boolean) {
        viewModelScope.launch { settingsRepository.setUse24Hour(value) }
    }

    val savedPlaces: StateFlow<List<SavedPlace>> =
        placeDao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val gpsPlace = MutableStateFlow<Place?>(null)

    /** The GPS position (if known) followed by the saved places, in the order the pager shows them. */
    val places: StateFlow<List<Place>> = combine(gpsPlace, savedPlaces) { gps, saved ->
        listOfNotNull(gps) + saved.map { Place(it.name, it.subtitle, it.latitude, it.longitude) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _forecasts = MutableStateFlow<Map<String, WeatherUiState>>(emptyMap())
    val forecasts: StateFlow<Map<String, WeatherUiState>> = _forecasts.asStateFlow()

    private val _search = MutableStateFlow<SearchState>(SearchState.Idle)
    val search: StateFlow<SearchState> = _search.asStateFlow()
    private var searchJob: Job? = null

    /** Key of the place the pager should scroll to, set after adding a place. */
    private val _focusKey = MutableStateFlow<String?>(null)
    val focusKey: StateFlow<String?> = _focusKey.asStateFlow()

    private val loadedKeys = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            places.collect { list ->
                list.filter { loadedKeys.add(it.key) }.forEach(::load)
            }
        }
    }

    fun refresh(place: Place) = load(place)

    /** Call once a location permission is granted. */
    fun refreshGps() {
        viewModelScope.launch { locationProvider.currentPlace()?.let { gpsPlace.value = it } }
    }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _search.value = SearchState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // wait for the user to pause typing
            _search.value = SearchState.Loading
            repository.searchPlaces(query.trim())
                .onSuccess { _search.value = SearchState.Results(it) }
                .onFailure { _search.value = SearchState.Error(it.message ?: "Search failed") }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = SearchState.Idle
    }

    fun addPlace(result: GeoResult) {
        val place = Place(result.name, result.subtitle, result.latitude, result.longitude)
        viewModelScope.launch {
            if (places.value.none { it.key == place.key }) {
                placeDao.insert(
                    SavedPlace(
                        name = place.name,
                        subtitle = place.subtitle,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        sortOrder = savedPlaces.value.size,
                    ),
                )
            }
            _focusKey.value = place.key
        }
    }

    fun removePlace(place: SavedPlace) {
        viewModelScope.launch { placeDao.delete(place) }
    }

    /** Swaps a saved place with its neighbour; [offset] is -1 to move up, +1 to move down. */
    fun movePlace(place: SavedPlace, offset: Int) {
        val list = savedPlaces.value.toMutableList()
        val from = list.indexOfFirst { it.id == place.id }
        val to = from + offset
        if (from < 0 || to !in list.indices) return
        list.add(to, list.removeAt(from))
        viewModelScope.launch { placeDao.updateAll(list.mapIndexed { i, p -> p.copy(sortOrder = i) }) }
    }

    fun focusHandled() {
        _focusKey.value = null
    }

    /** Shows the saved forecast straight away (if any), then replaces it with a fresh one. */
    private fun load(place: Place) {
        viewModelScope.launch {
            val shown = (_forecasts.value[place.key] as? WeatherUiState.Success)
                ?: repository.loadCached(place.latitude, place.longitude)?.toUiState(place)
            setState(place, shown?.copy(isRefreshing = true) ?: WeatherUiState.Loading)

            repository.fetch(place.latitude, place.longitude)
                .onSuccess { setState(place, it.toUiState(place)) }
                .onFailure {
                    setState(
                        place,
                        shown?.copy(isRefreshing = false, isOffline = true)
                            ?: WeatherUiState.Error(it.message ?: "Could not load forecast"),
                    )
                }
        }
    }

    private fun setState(place: Place, state: WeatherUiState) {
        _forecasts.update { it + (place.key to state) }
    }

    private fun CachedForecast.toUiState(place: Place) =
        WeatherUiState.Success(place.name, forecast, fetchedAt, airQuality)
}
