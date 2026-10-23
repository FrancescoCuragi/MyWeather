package com.francesco.myweather.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class TemperatureUnit(val label: String) { Celsius("°C"), Fahrenheit("°F") }

enum class WindUnit(val label: String) {
    KmH("km/h"),
    Ms("m/s"),
    Knots("kt"),
    Beaufort("Bft"),
}

data class UserSettings(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.Celsius,
    val windUnit: WindUnit = WindUnit.KmH,
    val use24Hour: Boolean = true,
)

/** Which background alerts are on. All start off: turning one on is also what asks for the notification permission. */
data class NotificationPrefs(
    val rainSoon: Boolean = false,
    val warnings: Boolean = false,
    val morningSummary: Boolean = false,
    /** Lowest MeteoAlarm awareness level that notifies: 2 = yellow, 3 = orange, 4 = red. */
    val warningMinLevel: Int = 2,
    /** When the morning summary goes out, in minutes after midnight. */
    val morningMinutes: Int = 7 * 60 + 30,
) {
    val anyEnabled: Boolean get() = rainSoon || warnings || morningSummary
}

/** What the background checks remember between runs, so the same alert is never sent twice. */
data class NotificationState(
    val lastRainNotify: Long = 0,
    val lastWarningCheck: Long = 0,
    val lastSummaryDate: String = "",
    val notifiedWarnings: Set<String> = emptySet(),
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            temperatureUnit = prefs.enumOrDefault(TEMPERATURE_KEY, TemperatureUnit.Celsius),
            windUnit = prefs.enumOrDefault(WIND_KEY, WindUnit.KmH),
            use24Hour = prefs[USE_24_HOUR_KEY] ?: true,
        )
    }

    /** The place the home-screen widget shows: the first place in the pager. Null until the app has run once. */
    val primaryPlace: Flow<Place?> = context.dataStore.data.map { prefs ->
        val name = prefs[PRIMARY_NAME_KEY]
        val lat = prefs[PRIMARY_LAT_KEY]
        val lon = prefs[PRIMARY_LON_KEY]
        if (name != null && lat != null && lon != null) Place(name, null, lat, lon) else null
    }

    suspend fun setPrimaryPlace(place: Place) = edit {
        it[PRIMARY_NAME_KEY] = place.name
        it[PRIMARY_LAT_KEY] = place.latitude
        it[PRIMARY_LON_KEY] = place.longitude
    }

    val notificationPrefs: Flow<NotificationPrefs> = context.dataStore.data.map { prefs ->
        val defaults = NotificationPrefs()
        NotificationPrefs(
            rainSoon = prefs[NOTIFY_RAIN_KEY] ?: defaults.rainSoon,
            warnings = prefs[NOTIFY_WARNINGS_KEY] ?: defaults.warnings,
            morningSummary = prefs[NOTIFY_MORNING_KEY] ?: defaults.morningSummary,
            warningMinLevel = prefs[WARNING_LEVEL_KEY] ?: defaults.warningMinLevel,
            morningMinutes = prefs[MORNING_MINUTES_KEY] ?: defaults.morningMinutes,
        )
    }

    /** Applies [change] to the notification settings and returns the result. */
    suspend fun updateNotificationPrefs(change: (NotificationPrefs) -> NotificationPrefs): NotificationPrefs {
        var result = NotificationPrefs()
        edit { prefs ->
            val current = NotificationPrefs(
                rainSoon = prefs[NOTIFY_RAIN_KEY] ?: false,
                warnings = prefs[NOTIFY_WARNINGS_KEY] ?: false,
                morningSummary = prefs[NOTIFY_MORNING_KEY] ?: false,
                warningMinLevel = prefs[WARNING_LEVEL_KEY] ?: result.warningMinLevel,
                morningMinutes = prefs[MORNING_MINUTES_KEY] ?: result.morningMinutes,
            )
            result = change(current)
            prefs[NOTIFY_RAIN_KEY] = result.rainSoon
            prefs[NOTIFY_WARNINGS_KEY] = result.warnings
            prefs[NOTIFY_MORNING_KEY] = result.morningSummary
            prefs[WARNING_LEVEL_KEY] = result.warningMinLevel
            prefs[MORNING_MINUTES_KEY] = result.morningMinutes
        }
        return result
    }

    suspend fun notificationState(): NotificationState {
        val prefs = context.dataStore.data.first()
        return NotificationState(
            lastRainNotify = prefs[LAST_RAIN_KEY] ?: 0,
            lastWarningCheck = prefs[LAST_WARNING_CHECK_KEY] ?: 0,
            lastSummaryDate = prefs[LAST_SUMMARY_KEY] ?: "",
            notifiedWarnings = prefs[NOTIFIED_WARNINGS_KEY] ?: emptySet(),
        )
    }

    suspend fun saveNotificationState(state: NotificationState) = edit {
        it[LAST_RAIN_KEY] = state.lastRainNotify
        it[LAST_WARNING_CHECK_KEY] = state.lastWarningCheck
        it[LAST_SUMMARY_KEY] = state.lastSummaryDate
        it[NOTIFIED_WARNINGS_KEY] = state.notifiedWarnings
    }

    suspend fun setTemperatureUnit(unit: TemperatureUnit) = edit { it[TEMPERATURE_KEY] = unit.name }
    suspend fun setWindUnit(unit: WindUnit) = edit { it[WIND_KEY] = unit.name }
    suspend fun setUse24Hour(value: Boolean) = edit { it[USE_24_HOUR_KEY] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified E : Enum<E>> Preferences.enumOrDefault(
        key: Preferences.Key<String>,
        default: E,
    ): E = this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private companion object {
        val TEMPERATURE_KEY = stringPreferencesKey("temperature_unit")
        val WIND_KEY = stringPreferencesKey("wind_unit")
        val USE_24_HOUR_KEY = booleanPreferencesKey("use_24_hour")
        val NOTIFY_RAIN_KEY = booleanPreferencesKey("notify_rain")
        val NOTIFY_WARNINGS_KEY = booleanPreferencesKey("notify_warnings")
        val NOTIFY_MORNING_KEY = booleanPreferencesKey("notify_morning")
        val WARNING_LEVEL_KEY = intPreferencesKey("warning_min_level")
        val MORNING_MINUTES_KEY = intPreferencesKey("morning_minutes")
        val LAST_RAIN_KEY = longPreferencesKey("last_rain_notify")
        val LAST_WARNING_CHECK_KEY = longPreferencesKey("last_warning_check")
        val LAST_SUMMARY_KEY = stringPreferencesKey("last_summary_date")
        val NOTIFIED_WARNINGS_KEY = stringSetPreferencesKey("notified_warnings")
        val PRIMARY_NAME_KEY = stringPreferencesKey("primary_name")
        val PRIMARY_LAT_KEY = doublePreferencesKey("primary_lat")
        val PRIMARY_LON_KEY = doublePreferencesKey("primary_lon")
    }
}
