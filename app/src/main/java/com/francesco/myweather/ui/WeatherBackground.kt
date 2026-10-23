package com.francesco.myweather.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** The look of the sky behind the forecast: a tinted gradient with a little slow motion, kept faint so text stays readable. */
enum class Sky(val tint: Color) {
    ClearDay(Color(0xFF1B3A5C)),
    ClearNight(Color(0xFF0B1230)),
    Cloudy(Color(0xFF2A3342)),
    Rain(Color(0xFF1D3248)),
    Storm(Color(0xFF2C2547)),
    Snow(Color(0xFF33404F)),
    Fog(Color(0xFF353A40)),
}

fun skyFor(weatherCode: Int, isDay: Boolean): Sky = when (weatherCode) {
    0, 1 -> if (isDay) Sky.ClearDay else Sky.ClearNight
    2, 3 -> Sky.Cloudy
    45, 48 -> Sky.Fog
    71, 73, 75, 77, 85, 86 -> Sky.Snow
    95, 96, 99 -> Sky.Storm
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Sky.Rain
    else -> Sky.Cloudy
}

/** A repeatable pseudo-random number in 0..1 from an index and a salt, so each drop or star keeps its own place. */
private fun random(index: Int, salt: Int): Float {
    val x = sin(index * 12.9898 + salt * 78.233) * 43758.5453
    return (x - floor(x)).toFloat()
}

/** Draws the sky behind whatever this modifier is applied to. The animation only redraws; it never recomposes. */
@Composable
fun Modifier.weatherBackground(sky: Sky): Modifier {
    val floorColor = MaterialTheme.colorScheme.background
    val transition = rememberInfiniteTransition(label = "sky")
    val time = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "skyTime",
    )
    return this.drawBehind {
        drawRect(Brush.verticalGradient(listOf(sky.tint, floorColor), endY = size.height * 0.75f))
        val t = time.value
        when (sky) {
            Sky.ClearDay -> sun(t)
            Sky.ClearNight -> stars(t)
            Sky.Cloudy -> clouds(t, count = 4, alpha = 0.10f)
            Sky.Rain -> { clouds(t, count = 3, alpha = 0.08f); rain(t, drops = 70, alpha = 0.16f) }
            Sky.Storm -> { clouds(t, count = 3, alpha = 0.10f); rain(t, drops = 110, alpha = 0.2f); lightning(t) }
            Sky.Snow -> snow(t)
            Sky.Fog -> fog(t)
        }
    }
}

private fun DrawScope.sun(t: Float) {
    val pulse = 0.85f + 0.15f * sin(t * 2 * PI.toFloat() * 3)  // three slow breaths per loop
    val radius = size.width * 0.75f * pulse
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFC857).copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width * 0.85f, 0f), radius = radius),
        radius = radius,
        center = Offset(size.width * 0.85f, 0f),
    )
}

private fun DrawScope.stars(t: Float) {
    repeat(45) { i ->
        val twinkle = 0.5f + 0.5f * sin((t * 4 + random(i, 3)) * 2 * PI.toFloat())
        drawCircle(
            Color.White.copy(alpha = 0.15f + 0.45f * twinkle * random(i, 4)),
            radius = 1.2f + 1.6f * random(i, 5),
            center = Offset(random(i, 1) * size.width, random(i, 2) * size.height * 0.55f),
        )
    }
}

private fun DrawScope.clouds(t: Float, count: Int, alpha: Float) {
    repeat(count) { i ->
        val radius = size.width * (0.35f + 0.25f * random(i, 6))
        // Each cloud drifts across the screen once per loop and re-enters on the other side.
        val x = ((random(i, 7) + t * (0.5f + 0.5f * random(i, 8))) % 1f) * (size.width + 2 * radius) - radius
        val y = size.height * (0.04f + 0.3f * random(i, 9))
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent), center = Offset(x, y), radius = radius),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

private fun DrawScope.rain(t: Float, drops: Int, alpha: Float) {
    repeat(drops) { i ->
        val speed = 4f + 4f * random(i, 11)  // loops of the time value per fall
        val y = ((random(i, 12) + t * speed) % 1f) * (size.height + 60f) - 30f
        val x = random(i, 13) * size.width + y * 0.12f
        drawLine(
            Color(0xFFBFD8FF).copy(alpha = alpha),
            Offset(x, y),
            Offset(x - 5f, y + 22f + 10f * random(i, 14)),
            strokeWidth = 1.5f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.snow(t: Float) {
    repeat(55) { i ->
        val y = ((random(i, 21) + t * (0.8f + 1.2f * random(i, 22))) % 1f) * size.height
        val sway = sin((t * 3 + random(i, 23)) * 2 * PI.toFloat()) * 14f
        drawCircle(
            Color.White.copy(alpha = 0.25f + 0.3f * random(i, 24)),
            radius = 1.5f + 2.2f * random(i, 25),
            center = Offset(random(i, 26) * size.width + sway, y),
        )
    }
}

private fun DrawScope.fog(t: Float) {
    repeat(3) { i ->
        val bandHeight = size.height * 0.16f
        val y = size.height * (0.08f + 0.22f * i)
        val shift = ((t * (0.4f + 0.2f * i) + i * 0.3f) % 1f) * size.width
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                startX = shift - size.width,
                endX = shift,
            ),
            topLeft = Offset(0f, y),
            size = androidx.compose.ui.geometry.Size(size.width, bandHeight),
        )
    }
}

/** A faint flash a couple of times per loop. */
private fun DrawScope.lightning(t: Float) {
    val phase = (t * 3f) % 1f
    val flash = if (phase < 0.03f) 1f - phase / 0.03f else 0f
    if (flash > 0f) drawRect(Color.White.copy(alpha = 0.07f * flash))
}
