package com.francesco.myweather.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.ForecastResponse
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(state: WeatherUiState, onRefresh: () -> Unit) {
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
            is WeatherUiState.Success -> ForecastContent(state, onRefresh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ForecastContent(state: WeatherUiState.Success, onRefresh: () -> Unit) {
    val forecast = state.forecast
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeroCard(state.placeName, forecast) }
            item { UpdatedCaption(state) }
            item { HourlyStrip(forecast) }
            item { DetailsGrid(state) }
            item { Text("Next 14 days", style = MaterialTheme.typography.titleMedium) }
            items(forecast.daily.time.indices.toList()) { i -> DailyRow(forecast, i) }
        }
    }
}

@Composable
private fun UpdatedCaption(state: WeatherUiState.Success) {
    val time = Instant.ofEpochMilli(state.updatedAt).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
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
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(placeName, style = MaterialTheme.typography.titleLarge)
            Text("${c.temperature.roundToInt()}°", style = MaterialTheme.typography.displayLarge)
            Text(
                "${weatherIcon(c.weatherCode, c.isDay == 1)} ${weatherLabel(c.weatherCode)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Feels like ${c.feelsLike.roundToInt()}° · H ${forecast.daily.temperatureMax[0].roundToInt()}° " +
                    "L ${forecast.daily.temperatureMin[0].roundToInt()}°",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Wind ${c.windSpeed.roundToInt()} km/h · Humidity ${c.humidity}%",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HourlyStrip(forecast: ForecastResponse) {
    val h = forecast.hourly
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
                            "%02d:00".format(LocalDateTime.parse(h.time[i]).hour),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(weatherIcon(h.weatherCode[i], h.isDay[i] == 1))
                        Text("${h.temperature[i].roundToInt()}°", style = MaterialTheme.typography.titleMedium)
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

private fun clockTime(isoDateTime: String) =
    LocalDateTime.parse(isoDateTime).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun detailsOf(state: WeatherUiState.Success): List<Detail> {
    val c = state.forecast.current
    val d = state.forecast.daily
    val moon = moonPhase(LocalDate.parse(d.time[0]))
    return listOfNotNull(
        c.uvIndex?.let { Detail("UV index", "%.0f".format(Locale.ROOT, it), uvNote(it)) },
        state.airQuality?.aqi?.let { Detail("Air quality", "$it", aqiNote(it)) },
        Detail("Humidity", "${c.humidity}%"),
        c.pressure?.let { Detail("Pressure", "${it.roundToInt()} hPa") },
        c.visibility?.let { Detail("Visibility", "%.1f km".format(Locale.ROOT, it / 1000)) },
        c.dewPoint?.let { Detail("Dew point", "${it.roundToInt()}°") },
        Detail("Sunrise", clockTime(d.sunrise[0])),
        Detail("Sunset", clockTime(d.sunset[0])),
        Detail("Moon", moon.icon, moon.name),
    )
}

@Composable
private fun DetailsGrid(state: WeatherUiState.Success) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Details", style = MaterialTheme.typography.titleMedium)
        detailsOf(state).chunked(2).forEach { pair ->
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
private fun DailyRow(forecast: ForecastResponse, i: Int) {
    val d = forecast.daily
    val day = if (i == 0) {
        "Today"
    } else {
        LocalDate.parse(d.time[i]).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(day, Modifier.width(64.dp))
        Text("${weatherIcon(d.weatherCode[i])} ${d.precipitationProbabilityMax[i] ?: 0}%", Modifier.width(88.dp))
        Text("${d.temperatureMin[i].roundToInt()}° / ${d.temperatureMax[i].roundToInt()}°")
    }
}
