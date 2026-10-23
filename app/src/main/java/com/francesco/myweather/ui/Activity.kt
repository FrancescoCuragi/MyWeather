package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.ForecastResponse
import kotlin.math.pow

/** How good a day looks for an outdoor activity, 0 to 100. */
data class ActivityScore(val name: String, val icon: String, val score: Int) {
    val verdict: String get() = when {
        score >= 75 -> "Great"
        score >= 50 -> "Okay"
        score >= 25 -> "Poor"
        else -> "Bad"
    }
}

private const val DAY_START = 8
private const val DAY_END = 20  // exclusive: the scores judge 08:00 to 20:00, when people are out

/** 1 inside [idealLow, idealHigh], falling linearly to 0 at [zeroLow] and [zeroHigh]. */
private fun band(value: Double, zeroLow: Double, idealLow: Double, idealHigh: Double, zeroHigh: Double): Double = when {
    value < zeroLow || value > zeroHigh -> 0.0
    value < idealLow -> (value - zeroLow) / (idealLow - zeroLow)
    value > idealHigh -> (zeroHigh - value) / (zeroHigh - idealHigh)
    else -> 1.0
}

private data class Profile(
    val name: String,
    val icon: String,
    val temperature: DoubleArray,  // zeroLow, idealLow, idealHigh, zeroHigh in °C
    val calmWind: Double,          // km/h up to which wind costs nothing
    val windLimit: Double,         // km/h at which the wind is too much
)

private val PROFILES = listOf(
    Profile("Cycling", "🚴", doubleArrayOf(0.0, 13.0, 24.0, 35.0), calmWind = 15.0, windLimit = 45.0),
    Profile("Running", "🏃", doubleArrayOf(-5.0, 6.0, 16.0, 29.0), calmWind = 20.0, windLimit = 55.0),
    Profile("Walking", "🚶", doubleArrayOf(-2.0, 11.0, 24.0, 33.0), calmWind = 25.0, windLimit = 60.0),
)

/**
 * Scores for one day from its hourly forecast, judging the daytime hours. Rain counts most (a dry day matters more
 * than the perfect temperature), then temperature, then wind. Returns an empty list when the data is missing.
 */
fun activityScores(forecast: ForecastResponse, date: String): List<ActivityScore> {
    val h = forecast.hourly
    val hours = h.time.indices.filter { i ->
        h.time[i].startsWith(date) && h.time[i].substring(11, 13).toInt() in DAY_START until DAY_END
    }
    if (hours.isEmpty()) return emptyList()

    val temperature = hours.map { h.temperature[it] }.average()
    // Forecasts cached before these fields existed have empty lists until the next refresh.
    val wind = hours.mapNotNull { h.windSpeed.getOrNull(it) }.takeIf { it.isNotEmpty() }?.average() ?: 10.0
    val gusts = hours.mapNotNull { h.windGusts.getOrNull(it) }.maxOrNull() ?: wind
    val rainMm = hours.sumOf { h.precipitation.getOrNull(it) ?: 0.0 }
    val chance = hours.map { (h.precipitationProbability[it] ?: 0) / 100.0 }.average()

    val rain = (1.0 - (rainMm / 3.0).coerceAtMost(1.0) * 0.8 - chance * 0.2).coerceIn(0.0, 1.0)

    return PROFILES.map { p ->
        val t = band(temperature, p.temperature[0], p.temperature[1], p.temperature[2], p.temperature[3])
        val windScore = band((wind * 0.6 + gusts * 0.4), -1.0, 0.0, p.calmWind, p.windLimit)
        val score = 100.0 * t.pow(0.30) * rain.pow(0.45) * windScore.pow(0.25)
        ActivityScore(p.name, p.icon, score.toInt().coerceIn(0, 100))
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 75 -> Color(0xFF6FD08C)
    score >= 50 -> Color(0xFFF2C94C)
    score >= 25 -> Color(0xFFF2994A)
    else -> Color(0xFFEB5757)
}

/** Today's outdoor conditions at a glance. */
@Composable
fun ActivityCard(forecast: ForecastResponse) {
    val scores = activityScores(forecast, forecast.daily.time[0])
    if (scores.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Outdoors today", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                scores.forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.icon, style = MaterialTheme.typography.headlineMedium)
                        Text("${s.score}", style = MaterialTheme.typography.headlineSmall, color = scoreColor(s.score))
                        Text(s.name, style = MaterialTheme.typography.labelMedium)
                        Text(s.verdict, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Judged from 08:00 to 20:00 on temperature, rain and wind.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(300.dp),
            )
        }
    }
}

/** One line of scores for an expanded day. */
@Composable
fun ActivityLine(forecast: ForecastResponse, date: String) {
    val scores = activityScores(forecast, date)
    if (scores.isEmpty()) return
    Text(
        scores.joinToString("  ·  ") { "${it.icon} ${it.score}" },
        style = MaterialTheme.typography.bodyMedium,
    )
}
