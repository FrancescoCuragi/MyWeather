package com.francesco.myweather.notify

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.francesco.myweather.data.BelgiumRegions
import com.francesco.myweather.data.MeteoAlarmRepository
import com.francesco.myweather.data.NotificationPrefs
import com.francesco.myweather.data.NotificationState
import com.francesco.myweather.data.Place
import com.francesco.myweather.data.SettingsRepository
import com.francesco.myweather.data.UserSettings
import com.francesco.myweather.data.WeatherRepository
import com.francesco.myweather.ui.precipitation
import com.francesco.myweather.ui.temperature
import com.francesco.myweather.ui.weatherLabel
import com.francesco.myweather.ui.wind
import com.francesco.myweather.widget.WeatherWidget
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * The background check, run about every half hour and on demand from Settings. It looks at the first place in the
 * app, the same one the widget shows, and sends whichever alerts are switched on. A run never throws: one failing
 * check (say, no network) must not stop the others, and the next run simply tries again.
 *
 * With [KEY_FORCE] set (the "Check now" button) every enabled check reports even if it would normally stay quiet,
 * so you can see what it finds.
 */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val force = inputData.getBoolean(KEY_FORCE, false)
        val settingsRepository = SettingsRepository(context)
        val prefs = settingsRepository.notificationPrefs.first()
        val place = settingsRepository.primaryPlace.first() ?: return Result.success()
        if (!Notifier.canPost(context)) return Result.success()

        val units = settingsRepository.settings.first()
        val weather = WeatherRepository(context.cacheDir)
        var state = settingsRepository.notificationState()
        val now = System.currentTimeMillis()

        if (prefs.rainSoon) state = runCatching { checkRain(context, weather, place, state, force, now) }.getOrDefault(state)
        if (prefs.warnings) state = runCatching { checkWarnings(context, place, prefs, state, force, now) }.getOrDefault(state)
        if (prefs.morningSummary) state = runCatching { checkMorning(context, weather, place, units, prefs, state, force) }.getOrDefault(state)

        settingsRepository.saveNotificationState(state)
        return Result.success()
    }

    private suspend fun checkRain(
        context: Context, weather: WeatherRepository, place: Place, state: NotificationState, force: Boolean, now: Long,
    ): NotificationState {
        val outlook = weather.rainOutlook(place.latitude, place.longitude).getOrNull() ?: return state
        val minutes = outlook.minutesToRain
        val startsSoon = !outlook.raining && minutes != null && minutes <= RAIN_LOOKAHEAD_MINUTES
        val sentRecently = now - state.lastRainNotify < RAIN_QUIET_MS

        if (startsSoon && (force || !sentRecently)) {
            val text = if (minutes!! <= 5) "Rain is about to start" else "Rain expected in about ${(minutes + 2) / 5 * 5} min"
            Notifier.post(context, Notifier.ID_RAIN, Notifier.CHANNEL_RAIN, "Rain soon in ${place.name}", text)
            return state.copy(lastRainNotify = now)
        }
        if (force) {
            val text = if (outlook.raining) "It is raining there right now." else "No rain expected in the next two hours."
            Notifier.post(context, Notifier.ID_RAIN, Notifier.CHANNEL_RAIN, "Rain check for ${place.name}", text)
        }
        return state
    }

    private suspend fun checkWarnings(
        context: Context, place: Place, prefs: NotificationPrefs, state: NotificationState, force: Boolean, now: Long,
    ): NotificationState {
        if (!force && now - state.lastWarningCheck < WARNING_CHECK_INTERVAL_MS) return state
        if (!BelgiumRegions.contains(place.latitude, place.longitude)) {
            if (force) {
                Notifier.post(context, Notifier.ID_WARNING_INFO, Notifier.CHANNEL_WARNINGS, "Weather warnings",
                    "Warnings are only available for Belgium so far, and ${place.name} is outside it.")
            }
            return state
        }

        val warnings = MeteoAlarmRepository().warningsFor(place.latitude, place.longitude, prefs.warningMinLevel)
            .getOrNull() ?: return state  // feed unreachable: leave the check time alone so the next run retries
        val fresh = warnings.filter { force || it.key !in state.notifiedWarnings }
        fresh.forEach { warning ->
            val emoji = when (warning.level) { 2 -> "🟡"; 3 -> "🟠"; else -> "🔴" }
            Notifier.post(
                context, Notifier.warningId(warning.key), Notifier.CHANNEL_WARNINGS,
                "$emoji ${warning.event}",
                warning.description.ifBlank { warning.event },
                subText = warning.area,
            )
        }
        if (force && warnings.isEmpty()) {
            Notifier.post(context, Notifier.ID_WARNING_INFO, Notifier.CHANNEL_WARNINGS, "Weather warnings",
                "No active warnings near ${place.name}.")
        }
        // Remember only warnings still in the feed, so the list cannot grow forever and a re-issued alert counts as new.
        val current = warnings.map { it.key }.toSet()
        return state.copy(lastWarningCheck = now, notifiedWarnings = (state.notifiedWarnings intersect current) + fresh.map { it.key })
    }

    private suspend fun checkMorning(
        context: Context, weather: WeatherRepository, place: Place, units: UserSettings,
        prefs: NotificationPrefs, state: NotificationState, force: Boolean,
    ): NotificationState {
        val today = LocalDate.now().toString()
        val time = LocalTime.now()
        val minutes = time.hour * 60 + time.minute
        val due = minutes >= prefs.morningMinutes && minutes < prefs.morningMinutes + MORNING_WINDOW_MINUTES
        if (!force && (!due || state.lastSummaryDate == today)) return state

        val fetched = weather.fetch(place.latitude, place.longitude).getOrNull()
        if (fetched != null) runCatching { WeatherWidget().updateAll(context) }  // keep the widget in step too
        val forecast = (fetched ?: weather.loadCached(place.latitude, place.longitude))?.forecast ?: return state

        val daily = forecast.daily
        val hourly = forecast.hourly
        val rain = if (hourly.precipitation.isEmpty()) null else
            hourly.time.indices.filter { hourly.time[it].startsWith(daily.time[0]) }.sumOf { hourly.precipitation[it] ?: 0.0 }
        val strongestWind = hourly.time.indices.filter { hourly.time[it].startsWith(daily.time[0]) }
            .mapNotNull { hourly.windSpeed.getOrNull(it) }.maxOrNull()
        val text = listOfNotNull(
            "High ${units.temperature(daily.temperatureMax[0])}, low ${units.temperature(daily.temperatureMin[0])}",
            rain?.takeIf { it >= 0.1 }?.let { "rain ${units.precipitation(it)}" },
            strongestWind?.let { "wind up to ${units.wind(it)}" },
        ).joinToString(" · ")
        Notifier.post(context, Notifier.ID_SUMMARY, Notifier.CHANNEL_SUMMARY,
            "${place.name}: ${weatherLabel(daily.weatherCode[0]).lowercase()} today", text)
        return state.copy(lastSummaryDate = today)
    }

    companion object {
        const val KEY_FORCE = "force"
        private const val RAIN_LOOKAHEAD_MINUTES = 120
        private const val RAIN_QUIET_MS = 3 * 60 * 60 * 1000L           // at most one rain alert per three hours
        private const val WARNING_CHECK_INTERVAL_MS = 60 * 60 * 1000L   // the warnings file is large; hourly is plenty
        private const val MORNING_WINDOW_MINUTES = 120                  // still send late if the phone slept through it
    }
}
