package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.ForecastResponse
import com.francesco.myweather.data.HistoryComparison
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Today against the same date a year ago and the average of the past few years. */
@Composable
fun HistoryCard(forecast: ForecastResponse, state: HistoryUiState?, onLoad: () -> Unit) {
    LaunchedEffect(Unit) { onLoad() }  // fetched the first time this card scrolls into view
    val settings = LocalSettings.current
    val today = LocalDate.parse(forecast.daily.time[0])

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This day in past years", style = MaterialTheme.typography.titleMedium)
            when (state) {
                null, HistoryUiState.Loading -> CircularProgressIndicator(Modifier.padding(8.dp))
                HistoryUiState.Failed -> Text("Could not load the history.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                is HistoryUiState.Ready -> HistoryBody(forecast, state.comparison, today, settings)
            }
        }
    }
}

@Composable
private fun HistoryBody(forecast: ForecastResponse, h: HistoryComparison, today: LocalDate, settings: com.francesco.myweather.data.UserSettings) {
    val last = h.lastYear
    if (last == null && h.averageHigh == null) {
        Text("No history is available for this date yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val high = forecast.daily.temperatureMax[0]
    val low = forecast.daily.temperatureMin[0]

    if (last != null) {
        val date = LocalDate.parse(last.date).format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(date, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(weatherLabel(last.weatherCode), style = MaterialTheme.typography.bodyMedium)
            }
            Text(weatherIcon(last.weatherCode), style = MaterialTheme.typography.headlineMedium)
            Text("${settings.temperature(last.low)} / ${settings.temperature(last.high)}", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Rain ${settings.precipitation(last.rainMm)} · wind up to ${settings.wind(last.windMaxKmh)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    h.averageHigh?.let { avgHigh ->
        // Differences are in degrees of the chosen unit, so convert the two temperatures and subtract.
        val diff = settings.temperatureValue(high) - settings.temperatureValue(avgHigh)
        val word = when {
            abs(diff) < 1.0 -> "about the usual temperature"
            diff > 0 -> "${abs(diff).roundToInt()}° warmer than usual"
            else -> "${abs(diff).roundToInt()}° colder than usual"
        }
        Text(
            "Today's high of ${settings.temperature(high)} is $word (average high ${settings.temperature(avgHigh)} " +
                "and low ${settings.temperature(h.averageLow ?: low)} over ${h.years} years).",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
