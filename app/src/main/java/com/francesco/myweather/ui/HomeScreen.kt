package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.ForecastResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(state: WeatherUiState, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            WeatherUiState.Loading -> CircularProgressIndicator()
            is WeatherUiState.Error -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(state.message, textAlign = TextAlign.Center)
                Button(onClick = onRetry) { Text("Retry") }
            }
            is WeatherUiState.Success -> ForecastContent(state.placeName, state.forecast)
        }
    }
}

@Composable
private fun ForecastContent(placeName: String, forecast: ForecastResponse) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { HeroCard(placeName, forecast) }
        item { HourlyStrip(forecast) }
        item { Text("Next 14 days", style = MaterialTheme.typography.titleMedium) }
        items(forecast.daily.time.indices.toList()) { i -> DailyRow(forecast, i) }
    }
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
