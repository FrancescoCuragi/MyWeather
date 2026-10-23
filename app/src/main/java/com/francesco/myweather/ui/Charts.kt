package com.francesco.myweather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.francesco.myweather.data.FORECAST_MODELS
import com.francesco.myweather.data.ForecastResponse
import com.francesco.myweather.data.ModelComparison
import java.time.LocalDateTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private const val METEOGRAM_HOURS = 120

/** One line on a chart; null values leave a gap, as when a model's forecast ends earlier than the others. */
class ChartLine(val label: String, val color: Color, val values: List<Double?>)

/**
 * A time-series chart: horizontal grid with value labels, day dividers with weekday names, optional bars and any
 * number of lines. All series line up with [times], one value per hour.
 */
@Composable
fun TimeChart(
    times: List<LocalDateTime>,
    lines: List<ChartLine>,
    modifier: Modifier = Modifier,
    bars: List<Double?>? = null,
    barColor: Color = Color.Unspecified,
    minZero: Boolean = false,
    height: Dp = 130.dp,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val resolvedBarColor = if (barColor == Color.Unspecified) MaterialTheme.colorScheme.primary else barColor

    Canvas(modifier.fillMaxWidth().height(height)) {
        val left = 38.dp.toPx()
        val right = 4.dp.toPx()
        val top = 6.dp.toPx()
        val bottom = 16.dp.toPx()
        val plotWidth = size.width - left - right
        val plotHeight = size.height - top - bottom
        val everything = lines.flatMap { it.values }.filterNotNull() + (bars?.filterNotNull() ?: emptyList())
        if (everything.isEmpty() || times.size < 2) return@Canvas

        var low = if (minZero) 0.0 else everything.min()
        var high = everything.max()
        if (high - low < 1e-6) high = low + 1.0
        val margin = (high - low) * 0.1
        if (!minZero) low -= margin
        high += margin

        fun xAt(i: Int) = left + plotWidth * i / (times.size - 1)
        fun yAt(v: Double) = top + plotHeight * (1f - ((v - low) / (high - low)).toFloat())

        // Horizontal grid and value labels: bottom, middle, top.
        for (step in 0..2) {
            val v = low + (high - low) * step / 2
            val y = yAt(v)
            drawLine(gridColor, Offset(left, y), Offset(left + plotWidth, y), strokeWidth = 1f)
            val text = if (high - low >= 10) "%.0f".format(Locale.ROOT, v) else "%.1f".format(Locale.ROOT, v)
            val layout = measurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(left - 4.dp.toPx() - layout.size.width, y - layout.size.height / 2f))
        }

        // Day dividers with the weekday name centred under each day.
        var dayStart = 0
        for (i in 1..times.size) {
            val newDay = i == times.size || times[i].toLocalDate() != times[dayStart].toLocalDate()
            if (!newDay) continue
            if (i < times.size) drawLine(dividerColor, Offset(xAt(i), top), Offset(xAt(i), top + plotHeight), strokeWidth = 1f)
            if (xAt(i - 1) - xAt(dayStart) > 34.dp.toPx()) {
                val name = times[dayStart].dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())
                val layout = measurer.measure(name, labelStyle)
                val centre = (xAt(dayStart) + xAt(i - 1)) / 2
                drawText(layout, topLeft = Offset(centre - layout.size.width / 2f, top + plotHeight + 2.dp.toPx()))
            }
            dayStart = i
        }

        // Bars grow up from the baseline.
        if (bars != null) {
            val barWidth = plotWidth / times.size * 0.8f
            val baseline = yAt(maxOf(low, 0.0))
            bars.forEachIndexed { i, v ->
                if (v == null || v <= 0.0) return@forEachIndexed
                val y = yAt(v)
                drawRect(resolvedBarColor, Offset(xAt(i) - barWidth / 2, y), Size(barWidth, baseline - y))
            }
        }

        // Lines, broken wherever a value is missing.
        lines.forEach { line ->
            val path = Path()
            var drawing = false
            line.values.forEachIndexed { i, v ->
                if (v == null) { drawing = false; return@forEachIndexed }
                if (drawing) path.lineTo(xAt(i), yAt(v)) else { path.moveTo(xAt(i), yAt(v)); drawing = true }
            }
            drawPath(path, line.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
private fun ChartTitle(title: String, unit: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Index of the first hourly entry at or after the current hour, and the hours from there on. */
private fun hoursFromNow(times: List<String>, nowIso: String): List<Int> {
    val now = LocalDateTime.parse(nowIso).withMinute(0)
    val start = times.indexOfFirst { !LocalDateTime.parse(it).isBefore(now) }.coerceAtLeast(0)
    return (start until times.size).toList()
}

/** Temperature, rain, wind and pressure for the next five days, stacked on a shared time axis. */
@Composable
fun MeteogramCard(forecast: ForecastResponse) {
    val settings = LocalSettings.current
    val hourly = forecast.hourly
    // Forecasts cached before these fields existed have empty lists until the next refresh.
    if (hourly.precipitation.isEmpty() || hourly.windSpeed.isEmpty() || hourly.pressure.isEmpty()) return

    val indices = hoursFromNow(hourly.time, forecast.current.time).take(METEOGRAM_HOURS)
    val times = indices.map { LocalDateTime.parse(hourly.time[it]) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Next 5 days", style = MaterialTheme.typography.titleMedium)

            ChartTitle("Temperature", settings.temperatureLabel)
            TimeChart(
                times,
                listOf(ChartLine("Temperature", primary, indices.map { settings.temperatureValue(hourly.temperature[it]) })),
            )

            ChartTitle("Rain", "${settings.precipitationLabel}/h")
            TimeChart(
                times, emptyList(),
                bars = indices.map { hourly.precipitation[it]?.let(settings::precipitationValue) },
                barColor = Color(0xFF5AA9FF), minZero = true, height = 90.dp,
            )

            ChartTitle("Wind and gusts", settings.windLabel)
            TimeChart(
                times,
                listOf(
                    ChartLine("Gusts", secondary.copy(alpha = 0.6f), indices.map { hourly.windGusts.getOrNull(it)?.let(settings::windValue) }),
                    ChartLine("Wind", primary, indices.map { hourly.windSpeed[it]?.let(settings::windValue) }),
                ),
                minZero = true, height = 100.dp,
            )

            ChartTitle("Pressure", "hPa")
            TimeChart(
                times,
                listOf(ChartLine("Pressure", secondary, indices.map { hourly.pressure[it] })),
                height = 90.dp,
            )
        }
    }
}

private val MODEL_COLORS = mapOf(
    "ecmwf_ifs025" to Color(0xFF6AA9FF),
    "icon_seamless" to Color(0xFF6FD08C),
    "gfs_seamless" to Color(0xFFFFB357),
    "meteofrance_seamless" to Color(0xFFFF7FA8),
    "ukmo_seamless" to Color(0xFFB58CFF),
)

private enum class Comparable(val title: String) {
    Temperature("Temp"),
    Rain("Rain"),
    Wind("Wind"),
    Pressure("Pressure"),
}

/** The same forecast from several weather models, on one chart so disagreements are easy to see. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelComparisonCard(forecast: ForecastResponse, state: ModelsUiState?, onLoad: () -> Unit) {
    LaunchedEffect(Unit) { onLoad() }  // fetched the first time this card scrolls into view
    var selected by rememberSaveable { mutableIntStateOf(0) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Compare models", style = MaterialTheme.typography.titleMedium)
            when (state) {
                null, ModelsUiState.Loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ModelsUiState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Could not load the models.")
                    Button(onClick = onLoad) { Text("Retry") }
                }
                is ModelsUiState.Ready -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Comparable.entries.forEachIndexed { i, c ->
                            FilterChip(selected = selected == i, onClick = { selected = i }, label = { Text(c.title) })
                        }
                    }
                    ComparisonChart(forecast, state.comparison, Comparable.entries[selected])
                }
            }
        }
    }
}

@Composable
private fun ComparisonChart(forecast: ForecastResponse, comparison: ModelComparison, what: Comparable) {
    val settings = LocalSettings.current
    val indices = hoursFromNow(comparison.times, forecast.current.time)
    val times = indices.map { LocalDateTime.parse(comparison.times[it]) }
    val variable = when (what) {
        Comparable.Temperature -> "temperature_2m"
        Comparable.Rain -> "precipitation"
        Comparable.Wind -> "wind_speed_10m"
        Comparable.Pressure -> "pressure_msl"
    }
    val unit = when (what) {
        Comparable.Temperature -> settings.temperatureLabel
        Comparable.Rain -> "${settings.precipitationLabel}, running total"
        Comparable.Wind -> settings.windLabel
        Comparable.Pressure -> "hPa"
    }

    val lines = FORECAST_MODELS.mapNotNull { model ->
        val all = comparison.values[variable]?.get(model.id) ?: return@mapNotNull null
        val slice = indices.map { all.getOrNull(it) }
        val values = when (what) {
            Comparable.Temperature -> slice.map { it?.let(settings::temperatureValue) }
            Comparable.Wind -> slice.map { it?.let(settings::windValue) }
            Comparable.Rain -> {  // a running total shows at a glance how much the models disagree
                var total = 0.0
                slice.map { v -> v?.let { total += settings.precipitationValue(it); total } }
            }
            Comparable.Pressure -> slice
        }
        ChartLine(model.label, MODEL_COLORS.getValue(model.id), values)
    }

    ChartTitle(what.title, unit)
    TimeChart(times, lines, minZero = what == Comparable.Rain || what == Comparable.Wind, height = 170.dp)
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(line.color, CircleShape))
                Text(line.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
