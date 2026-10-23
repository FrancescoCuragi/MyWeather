package com.francesco.myweather.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The widget in the picker's big size (4x3). It can still be resized down to a strip or a tile. */
class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

/** The same widget offered at a small 2x2 size; the layout is chosen by size, so it can be resized up again. */
class WeatherWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}
