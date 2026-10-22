package com.francesco.myweather.ui

import java.time.LocalDate
import java.time.ZoneOffset

data class MoonPhase(val name: String, val icon: String)

private const val SYNODIC_MONTH_DAYS = 29.530588853

// A known new moon: 2000-01-06 (UTC), in days since the Unix epoch.
private val REFERENCE_NEW_MOON_EPOCH_DAY = LocalDate.of(2000, 1, 6).toEpochDay()

/** Approximate moon phase for a date; accurate to about a day, which is plenty for a label. */
fun moonPhase(date: LocalDate): MoonPhase {
    val age = ((date.toEpochDay() - REFERENCE_NEW_MOON_EPOCH_DAY) % SYNODIC_MONTH_DAYS + SYNODIC_MONTH_DAYS) %
        SYNODIC_MONTH_DAYS
    return when ((age / SYNODIC_MONTH_DAYS * 8 + 0.5).toInt() % 8) {
        0 -> MoonPhase("New moon", "🌑")
        1 -> MoonPhase("Waxing crescent", "🌒")
        2 -> MoonPhase("First quarter", "🌓")
        3 -> MoonPhase("Waxing gibbous", "🌔")
        4 -> MoonPhase("Full moon", "🌕")
        5 -> MoonPhase("Waning gibbous", "🌖")
        6 -> MoonPhase("Last quarter", "🌗")
        else -> MoonPhase("Waning crescent", "🌘")
    }
}
