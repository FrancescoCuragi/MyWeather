package com.francesco.myweather.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.ForecastResponse
import com.francesco.myweather.data.UserSettings
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(state: WeatherUiState, models: ModelsUiState?, onRefresh: () -> Unit, onLoadModels: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            WeatherUiState.Loading -> CircularProgressIndicator()
            is WeatherUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(state.message, textAlign = TextAlign.Center)
                Button(onClick = onRefresh) { Text("Retry") }
            }
            is WeatherUiState.Success -> ForecastContent(state, models, onRefresh, onLoadModels)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForecastContent(
    state: WeatherUiState.Success,
    models: ModelsUiState?,
    onRefresh: () -> Unit,
    onLoadModels: () -> Unit,
) {
    val forecast = state.forecast
    var openDay by rememberSaveable { mutableIntStateOf(-1) }  // the expanded day in the list, or -1
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeroCard(state.placeName, forecast) }
            item { UpdatedCaption(state) }
            item { HourlyStrip(forecast) }
            item { MeteogramCard(forecast) }
            item { DetailsGrid(state) }
            item { Text("Next 14 days", style = MaterialTheme.typography.titleMedium) }
            items(forecast.daily.time.indices.toList()) { i ->
                DailyRow(forecast, i, expanded = openDay == i, onClick = { openDay = if (openDay == i) -1 else i })
            }
            item { ModelComparisonCard(forecast, models, onLoadModels) }
        }
    }
}

@Composable
private fun UpdatedCaption(state: WeatherUiState.Success) {
    val time = LocalSettings.current.clock(
        Instant.ofEpochMilli(state.updatedAt).atZone(ZoneId.systemDefault()).toLocalDateTime(),
    )
    Text(
        if (state.isOffline) "Offline · showing data from $time" else "Updated $time",
        style = MaterialTheme.typography.labelMedium,
        color = if (state.isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HeroCard(placeName: String, forecast: ForecastResponse) {
    val c = forecast.current
    val settings = LocalSettings.current
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(placeName, style = MaterialTheme.typography.titleLarge)
            Text(settings.temperature(c.temperature), style = MaterialTheme.typography.displayLarge)
            Text(
                "${weatherIcon(c.weatherCode, c.isDay == 1)} ${weatherLabel(c.weatherCode)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Feels like ${settings.temperature(c.feelsLike)} · " +
                    "H ${settings.temperature(forecast.daily.temperatureMax[0])} " +
                    "L ${settings.temperature(forecast.daily.temperatureMin[0])}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Wind ${settings.wind(c.windSpeed)} · Humidity ${c.humidity}%",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HourlyStrip(forecast: ForecastResponse) {
    val h = forecast.hourly
    val settings = LocalSettings.current
    val now = LocalDateTime.parse(forecast.current.time).withMinute(0)
    val start = h.time.indexOfFirst { !LocalDateTime.parse(it).isBefore(now) }.coerceAtLeast(0)
    val hours = (start until minOf(start + 48, h.time.size)).toList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Next 48 hours", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hours) { i ->
                Card {
                    Column(
                        Modifier.padding(12.dp).width(56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            settings.hourLabel(LocalDateTime.parse(h.time[i])),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(weatherIcon(h.weatherCode[i], h.isDay[i] == 1))
                        Text(settings.temperature(h.temperature[i]), style = MaterialTheme.typography.titleMedium)
                        Text("${h.precipitationProbability[i] ?: 0}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private data class Detail(val title: String, val value: String, val note: String? = null)

private fun uvNote(uv: Double) = when {
    uv < 3 -> "Low"
    uv < 6 -> "Moderate"
    uv < 8 -> "High"
    uv < 11 -> "Very high"
    else -> "Extreme"
}

private fun aqiNote(aqi: Int) = when {
    aqi <= 50 -> "Good"
    aqi <= 100 -> "Moderate"
    aqi <= 150 -> "Unhealthy for sensitive groups"
    aqi <= 200 -> "Unhealthy"
    aqi <= 300 -> "Very unhealthy"
    else -> "Hazardous"
}

private fun detailsOf(state: WeatherUiState.Success, settings: UserSettings): List<Detail> {
    val c = state.forecast.current
    val d = state.forecast.daily
    val moon = moonPhase(LocalDate.parse(d.time[0]))
    return listOfNotNull(
        c.uvIndex?.let { Detail("UV index", "%.0f".format(Locale.ROOT, it), uvNote(it)) },
        state.airQuality?.aqi?.let { Detail("Air quality", "$it", aqiNote(it)) },
        Detail("Humidity", "${c.humidity}%"),
        c.pressure?.let { Detail("Pressure", "${it.roundToInt()} hPa") },
        c.visibility?.let { Detail("Visibility", settings.distance(it)) },
        c.dewPoint?.let { Detail("Dew point", settings.temperature(it)) },
        Detail("Sunrise", settings.clock(LocalDateTime.parse(d.sunrise[0]))),
        Detail("Sunset", settings.clock(LocalDateTime.parse(d.sunset[0]))),
        Detail("Moon", moon.icon, moon.name),
    )
}

@Composable
private fun DetailsGrid(state: WeatherUiState.Success) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Details", style = MaterialTheme.typography.titleMedium)
        detailsOf(state, LocalSettings.current).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { DetailCard(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailCard(detail: Detail, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(detail.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(detail.value, style = MaterialTheme.typography.headlineSmall)
            Text(detail.note ?: " ", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DailyRow(forecast: ForecastResponse, i: Int, expanded: Boolean, onClick: () -> Unit) {
    val d = forecast.daily
    val settings = LocalSettings.current
    val date = LocalDate.parse(d.time[i])
    val day = if (i == 0) {
        "Today"
    } else {
        "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.dayOfMonth}"
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(day, Modifier.width(72.dp))
            Text("${weatherIcon(d.weatherCode[i])} ${d.precipitationProbabilityMax[i] ?: 0}%", Modifier.width(88.dp))
            Text("${settings.temperature(d.temperatureMin[i])} / ${settings.temperature(d.temperatureMax[i])}")
            Text(if (expanded) "▴" else "▾", Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = expanded) { DayDetail(forecast, i) }
    }
}
