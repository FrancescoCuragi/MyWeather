package com.francesco.myweather.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.francesco.myweather.data.LengthUnit
import com.francesco.myweather.data.NotificationPrefs
import com.francesco.myweather.data.PrecipitationUnit
import com.francesco.myweather.data.TemperatureUnit
import com.francesco.myweather.data.UserSettings
import com.francesco.myweather.data.WindUnit
import com.francesco.myweather.widget.WeatherWidgetReceiver

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onTemperatureUnit: (TemperatureUnit) -> Unit,
    onWindUnit: (WindUnit) -> Unit,
    onPrecipitationUnit: (PrecipitationUnit) -> Unit,
    onLengthUnit: (LengthUnit) -> Unit,
    onUse24Hour: (Boolean) -> Unit,
    notificationPrefs: NotificationPrefs,
    onNotificationChange: ((NotificationPrefs) -> NotificationPrefs) -> Unit,
    onCheckNow: () -> Unit,
) {
    val context = LocalContext.current
    // Turning an alert on is what asks for the notification permission; the change is applied once it is granted.
    var pendingChange by remember { mutableStateOf<((NotificationPrefs) -> NotificationPrefs)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingChange?.let(onNotificationChange)
        pendingChange = null
    }
    fun change(enabling: Boolean, change: (NotificationPrefs) -> NotificationPrefs) {
        val needsPermission = enabling && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingChange = change
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onNotificationChange(change)
        }
    }

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
        SettingGroup("Rain and snow") {
            PrecipitationUnit.entries.forEach {
                Choice(it.label, settings.precipitationUnit == it) { onPrecipitationUnit(it) }
            }
        }
        SettingGroup("Waves and distance") {
            LengthUnit.entries.forEach {
                Choice(it.label, settings.lengthUnit == it) { onLengthUnit(it) }
            }
        }
        SettingGroup("Time format") {
            Choice("24-hour", settings.use24Hour) { onUse24Hour(true) }
            Choice("12-hour", !settings.use24Hour) { onUse24Hour(false) }
        }

        SettingGroup("Home screen") {
            val context = LocalContext.current
            Button(onClick = { requestPinWidget(context) }) { Text("Add weather widget") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Text(
                "About your first place: the 📍 current location if allowed, otherwise the first saved one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ToggleRow("Rain soon", "A heads-up shortly before rain starts", notificationPrefs.rainSoon) { on ->
                change(on) { it.copy(rainSoon = on) }
            }
            ToggleRow("Weather warnings", "Official warnings (MeteoAlarm). Belgium only for now.", notificationPrefs.warnings) { on ->
                change(on) { it.copy(warnings = on) }
            }
            if (notificationPrefs.warnings) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2 to "🟡 Yellow and up", 3 to "🟠 Orange and up", 4 to "🔴 Red only").forEach { (level, label) ->
                        FilterChip(
                            selected = notificationPrefs.warningMinLevel == level,
                            onClick = { onNotificationChange { it.copy(warningMinLevel = level) } },
                            label = { Text(label) },
                        )
                    }
                }
            }
            ToggleRow("Morning summary", "The day's weather at ${formatMinutes(notificationPrefs.morningMinutes)}", notificationPrefs.morningSummary) { on ->
                change(on) { it.copy(morningSummary = on) }
            }
            if (notificationPrefs.anyEnabled) {
                Button(onClick = onCheckNow) { Text("Check now") }
                Text(
                    "If alerts do not arrive, set MyWeather to \"Unrestricted\" in Android's battery settings for the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            "Weather data: Open-Meteo.com (CC BY 4.0). Map data © OpenStreetMap contributors.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatMinutes(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Asks the launcher to pin the widget; the user confirms in the system prompt. */
private fun requestPinWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    if (manager.isRequestPinAppWidgetSupported) {
        manager.requestPinAppWidget(ComponentName(context, WeatherWidgetReceiver::class.java), null, null)
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
