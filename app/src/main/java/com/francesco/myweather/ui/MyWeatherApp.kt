package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Tab(val label: String, val icon: String) {
    Forecast("Forecast", "🌤️"),
    Places("Places", "📍"),
}

@Composable
fun MyWeatherApp(viewModel: WeatherViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(Tab.Forecast.ordinal) }

    val places by viewModel.places.collectAsStateWithLifecycle()
    val forecasts by viewModel.forecasts.collectAsStateWithLifecycle()
    val saved by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val focusKey by viewModel.focusKey.collectAsStateWithLifecycle()

    // Adding a place jumps back to the forecast tab, where the pager scrolls to it.
    LaunchedEffect(focusKey) {
        if (focusKey != null) tab = Tab.Forecast.ordinal
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it.ordinal,
                        onClick = { tab = it.ordinal },
                        icon = { Text(it.icon) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.Forecast -> ForecastPager(
                    places = places,
                    forecasts = forecasts,
                    focusKey = focusKey,
                    onFocusHandled = viewModel::focusHandled,
                    onRefresh = viewModel::refresh,
                )
                Tab.Places -> LocationsScreen(
                    gpsPlace = places.firstOrNull { it.isCurrentLocation },
                    saved = saved,
                    search = search,
                    onQueryChange = viewModel::onQueryChange,
                    onAdd = viewModel::addPlace,
                    onRemove = viewModel::removePlace,
                    onMove = viewModel::movePlace,
                )
            }
        }
    }
}
