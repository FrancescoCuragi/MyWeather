package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

/** The forecast for a point tapped on the map: now, the next 24 hours and the next days, with a button to keep the place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointForecastSheet(state: PointUiState, onSave: (PointUiState.Ready) -> Unit, onDismiss: () -> Unit) {
    if (state is PointUiState.Idle) return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        when (state) {
            PointUiState.Loading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            PointUiState.Failed -> Column(
                Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Could not load the forecast for this point.", textAlign = TextAlign.Center)
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
            is PointUiState.Ready -> PointContent(state, onSave, onDismiss)
            PointUiState.Idle -> Unit
        }
    }
}

@Composable
private fun PointContent(state: PointUiState.Ready, onSave: (PointUiState.Ready) -> Unit, onDismiss: () -> Unit) {
    val settings = LocalSettings.current
    val forecast = state.forecast
    val current = forecast.current
    val today = forecast.daily

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(state.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                listOfNotNull(state.subtitle, "%.3f, %.3f".format(Locale.ROOT, state.latitude, state.longitude)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(weatherIcon(current.weatherCode, current.isDay == 1), style = MaterialTheme.typography.displayMedium)
            Column {
                Text(settings.temperature(current.temperature), style = MaterialTheme.typography.displayMedium)
                Text(weatherLabel(current.weatherCode), style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            "Feels like ${settings.temperature(current.feelsLike)} · H ${settings.temperature(today.temperatureMax[0])} " +
                "L ${settings.temperature(today.temperatureMin[0])}\n" +
                "Wind ${settings.wind(current.windSpeed)} · Humidity ${current.humidity}%",
            style = MaterialTheme.typography.bodyMedium,
        )

        PointHours(forecast)
        PointDays(forecast)

        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onSave(state) }) { Text("Save this place") }
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun PointHours(forecast: ForecastResponse) {
    val settings = LocalSettings.current
    val hourly = forecast.hourly
    val now = LocalDateTime.parse(forecast.current.time).withMinute(0)
    val start = hourly.time.indexOfFirst { !LocalDateTime.parse(it).isBefore(now) }.coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Next 24 hours", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items((start until minOf(start + 24, hourly.time.size)).toList()) { i ->
                Card {
                    Column(
                        Modifier.padding(horizontal = 8.dp, vertical = 10.dp).width(54.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(settings.hourLabel(LocalDateTime.parse(hourly.time[i])), style = MaterialTheme.typography.labelMedium)
                        Text(weatherIcon(hourly.weatherCode[i], hourly.isDay[i] == 1))
                        Text(settings.temperature(hourly.temperature[i]), style = MaterialTheme.typography.titleMedium)
                        Text("${hourly.precipitationProbability[i] ?: 0}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PointDays(forecast: ForecastResponse) {
    val settings = LocalSettings.current
    val daily = forecast.daily
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Next days", style = MaterialTheme.typography.titleMedium)
        for (i in 0 until minOf(7, daily.time.size)) {
            val date = LocalDate.parse(daily.time[i])
            val day = if (i == 0) "Today" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " ${date.dayOfMonth}"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(day, Modifier.width(72.dp))
                Text("${weatherIcon(daily.weatherCode[i])} ${daily.precipitationProbabilityMax[i] ?: 0}%", Modifier.width(88.dp))
                Text("${settings.temperature(daily.temperatureMin[i])} / ${settings.temperature(daily.temperatureMax[i])}")
            }
        }
    }
}
