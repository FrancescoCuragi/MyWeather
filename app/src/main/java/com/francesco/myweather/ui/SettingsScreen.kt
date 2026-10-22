package com.francesco.myweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.TemperatureUnit
import com.francesco.myweather.data.UserSettings
import com.francesco.myweather.data.WindUnit

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onTemperatureUnit: (TemperatureUnit) -> Unit,
    onWindUnit: (WindUnit) -> Unit,
    onUse24Hour: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingGroup("Temperature") {
            TemperatureUnit.entries.forEach {
                Choice(it.label, settings.temperatureUnit == it) { onTemperatureUnit(it) }
            }
        }
        SettingGroup("Wind speed") {
            WindUnit.entries.forEach {
                Choice(it.label, settings.windUnit == it) { onWindUnit(it) }
            }
        }
        SettingGroup("Time format") {
            Choice("24-hour", settings.use24Hour) { onUse24Hour(true) }
            Choice("12-hour", !settings.use24Hour) { onUse24Hour(false) }
        }

        Text(
            "Weather data: Open-Meteo.com (CC BY 4.0). Map data © OpenStreetMap contributors.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
