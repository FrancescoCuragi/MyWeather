package com.francesco.myweather.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.francesco.myweather.MainActivity
import com.francesco.myweather.R

/** Creates the notification channels and posts notifications; tapping one opens the app. */
object Notifier {
    const val CHANNEL_RAIN = "rain"
    const val CHANNEL_WARNINGS = "warnings"
    const val CHANNEL_SUMMARY = "summary"

    const val ID_RAIN = 1001
    const val ID_SUMMARY = 1002
    const val ID_WARNING_INFO = 1003  // "no warnings" and similar status messages
    private const val ID_WARNING_BASE = 2000

    /** Warnings get an id of their own, so several can be on screen and a re-sent one replaces itself. */
    fun warningId(key: String) = ID_WARNING_BASE + Math.floorMod(key.hashCode(), 1000)

    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CHANNEL_RAIN, "Rain soon", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A heads-up shortly before rain starts" },
            NotificationChannel(CHANNEL_WARNINGS, "Weather warnings", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Official warnings for your area" },
            NotificationChannel(CHANNEL_SUMMARY, "Morning summary", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "The day's weather in one glance" },
        ).forEach(manager::createNotificationChannel)
    }

    fun post(context: Context, id: Int, channel: String, title: String, text: String, subText: String? = null) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_weather)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(subText)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission")  // canPost() above has just checked it
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
