package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.ForecastResponse
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

/** One day opened up: a summary line and every hour of that day, scrolling sideways. */
@Composable
fun DayDetail(forecast: ForecastResponse, dayIndex: Int) {
    val settings = LocalSettings.current
    val hourly = forecast.hourly
    val daily = forecast.daily
    val date = daily.time[dayIndex]
    val hours = hourly.time.indices.filter { hourly.time[it].startsWith(date) }
    if (hours.isEmpty()) return

    val now = LocalDateTime.parse(forecast.current.time).withMinute(0)
    // Forecasts cached before these fields existed have empty lists until the next refresh.
    val rainTotal = if (hourly.precipitation.isEmpty()) null else hours.sumOf { hourly.precipitation[it] ?: 0.0 }
    val strongestWind = hours.mapNotNull { hourly.windSpeed.getOrNull(it) }.maxOrNull()
    // Short lines that each stay whole instead of wrapping mid-phrase on a narrow screen.
    val temperatures = "High ${settings.temperature(daily.temperatureMax[dayIndex])} · " +
        "Low ${settings.temperature(daily.temperatureMin[dayIndex])}"
    val conditions = listOfNotNull(
        rainTotal?.let { "Rain %.1f mm".format(Locale.ROOT, it) },
        strongestWind?.let { "Wind up to ${settings.wind(it)}" },
    ).joinToString(" · ")
    val sun = "Sunrise ${settings.clock(LocalDateTime.parse(daily.sunrise[dayIndex]))} · " +
        "Sunset ${settings.clock(LocalDateTime.parse(daily.sunset[dayIndex]))}"

    Column(Modifier.padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(temperatures, style = MaterialTheme.typography.bodyMedium)
            if (conditions.isNotEmpty()) Text(conditions, style = MaterialTheme.typography.bodyMedium)
            Text(sun, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(hours) { k ->
                val time = LocalDateTime.parse(hourly.time[k])
                HourCard(forecast, k, time, isNow = time == now)
            }
        }
    }
}

@Composable
private fun HourCard(forecast: ForecastResponse, k: Int, time: LocalDateTime, isNow: Boolean) {
    val settings = LocalSettings.current
    val h = forecast.hourly
    val rain = h.precipitation.getOrNull(k) ?: 0.0
    Card(
        colors = if (isNow) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp).width(60.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(settings.hourLabel(time), style = MaterialTheme.typography.labelMedium)
            Text(weatherIcon(h.weatherCode[k], h.isDay[k] == 1))
            Text(settings.temperature(h.temperature[k]), style = MaterialTheme.typography.titleMedium)
            Text("${h.precipitationProbability[k] ?: 0}%", style = MaterialTheme.typography.labelSmall)
            // Always reserve the line, so cards stay the same height whether or not rain is expected.
            Text(
                if (rain >= 0.1) "%.1f mm".format(Locale.ROOT, rain) else " ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            h.windSpeed.getOrNull(k)?.let {
                Text(settings.wind(it), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
