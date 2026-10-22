package com.francesco.myweather.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            temperatureUnit = prefs.enumOrDefault(TEMPERATURE_KEY, TemperatureUnit.Celsius),
            windUnit = prefs.enumOrDefault(WIND_KEY, WindUnit.KmH),
            use24Hour = prefs[USE_24_HOUR_KEY] ?: true,
        )
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
    }
}
