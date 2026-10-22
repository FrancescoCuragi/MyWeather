package com.francesco.myweather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.francesco.myweather.MainActivity
import com.francesco.myweather.data.CachedForecast
import com.francesco.myweather.data.Place
import com.francesco.myweather.data.SettingsRepository
import com.francesco.myweather.data.UserSettings
import com.francesco.myweather.data.WeatherRepository
import com.francesco.myweather.ui.clock
import com.francesco.myweather.ui.hourLabel
import com.francesco.myweather.ui.temperature
import com.francesco.myweather.ui.weatherIcon
import com.francesco.myweather.ui.weatherLabel
import com.francesco.myweather.ui.wind
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

// The app is dark-only, so the widget uses fixed colours instead of the system day/night scheme.
private val Background = Color(0xFF151A26)
private val TextPrimary = Color(0xFFE6E8F0)
private val TextSecondary = Color(0xFF9AA0B8)

private const val FETCH_TIMEOUT_MS = 8_000L
private const val HOURS_SHOWN = 6
private const val DAYS_SHOWN = 4

/** The big home-screen widget: current conditions, the next hours and the next days for the primary place. */
class WeatherWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settingsRepository = SettingsRepository(context)
        val settings = settingsRepository.settings.first()
        val place = settingsRepository.primaryPlace.first()
        val data = place?.let { loadForecast(context, it) }

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Background))
                    .cornerRadius(24.dp)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (place == null || data == null) {
                    EmptyState(hasPlace = place != null)
                } else {
                    WidgetContent(place, data, settings)
                }
            }
        }
    }

    /** Fresh data when the network answers in time, otherwise whatever the app last saved. */
    private suspend fun loadForecast(context: Context, place: Place): CachedForecast? {
        val repository = WeatherRepository(context.cacheDir)
        return withTimeoutOrNull(FETCH_TIMEOUT_MS) { repository.fetch(place.latitude, place.longitude).getOrNull() }
            ?: repository.loadCached(place.latitude, place.longitude)
    }
}

@Composable
private fun EmptyState(hasPlace: Boolean) {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            if (hasPlace) "No forecast yet. Tap to open MyWeather." else "Open MyWeather once to choose a location.",
            style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 14.sp, textAlign = TextAlign.Center),
        )
    }
}

@Composable
private fun WidgetContent(place: Place, data: CachedForecast, settings: UserSettings) {
    val forecast = data.forecast
    val current = forecast.current
    val daily = forecast.daily
    val updated = settings.clock(Instant.ofEpochMilli(data.fetchedAt).atZone(ZoneId.systemDefault()).toLocalDateTime())

    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                place.name,
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(updated, style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp))
        }

        Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                weatherIcon(current.weatherCode, current.isDay == 1),
                style = TextStyle(fontSize = 36.sp),
            )
            Text(
                settings.temperature(current.temperature),
                modifier = GlanceModifier.padding(start = 8.dp),
                style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 44.sp, fontWeight = FontWeight.Medium),
            )
            Column(GlanceModifier.defaultWeight().padding(start = 12.dp)) {
                Text(
                    weatherLabel(current.weatherCode),
                    style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                Text(
                    "H ${settings.temperature(daily.temperatureMax[0])}  L ${settings.temperature(daily.temperatureMin[0])}",
                    style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 12.sp),
                )
                Text(
                    "Wind ${settings.wind(current.windSpeed)}",
                    style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.height(6.dp))
        HourlyRow(data, settings)
        Spacer(GlanceModifier.height(8.dp))
        DailyRow(data, settings)
    }
}

@Composable
private fun HourlyRow(data: CachedForecast, settings: UserSettings) {
    val hourly = data.forecast.hourly
    val now = LocalDateTime.parse(data.forecast.current.time).withMinute(0)
    val start = hourly.time.indexOfFirst { !LocalDateTime.parse(it).isBefore(now) }.coerceAtLeast(0)
    val indices = (start until minOf(start + HOURS_SHOWN, hourly.time.size)).toList()

    Row(GlanceModifier.fillMaxWidth()) {
        indices.forEach { i ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    settings.hourLabel(LocalDateTime.parse(hourly.time[i])),
                    style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp),
                    maxLines = 1,
                )
                Text(weatherIcon(hourly.weatherCode[i], hourly.isDay[i] == 1), style = TextStyle(fontSize = 18.sp))
                Text(
                    settings.temperature(hourly.temperature[i]),
                    style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun DailyRow(data: CachedForecast, settings: UserSettings) {
    val daily = data.forecast.daily
    Row(GlanceModifier.fillMaxWidth()) {
        (0 until minOf(DAYS_SHOWN, daily.time.size)).forEach { i ->
            val day = if (i == 0) {
                "Today"
            } else {
                LocalDate.parse(daily.time[i]).dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
            }
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day, style = TextStyle(color = ColorProvider(TextSecondary), fontSize = 11.sp), maxLines = 1)
                Text(weatherIcon(daily.weatherCode[i]), style = TextStyle(fontSize = 18.sp))
                Text(
                    "${settings.temperature(daily.temperatureMin[i])} / ${settings.temperature(daily.temperatureMax[i])}",
                    style = TextStyle(color = ColorProvider(TextPrimary), fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        }
    }
}
