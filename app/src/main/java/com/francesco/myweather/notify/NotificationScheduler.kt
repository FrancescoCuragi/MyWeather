package com.francesco.myweather.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.francesco.myweather.data.NotificationPrefs
import java.util.concurrent.TimeUnit

/** Starts or stops the background check to match which alerts are switched on. */
object NotificationScheduler {
    private const val WORK_NAME = "weather-checks"

    fun sync(context: Context, prefs: NotificationPrefs) {
        val work = WorkManager.getInstance(context)
        if (!prefs.anyEnabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE keeps an already-scheduled check running instead of restarting its clock on every app launch.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One immediate run in which every enabled check reports what it finds. */
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<WeatherWorker>().setInputData(workDataOf(WeatherWorker.KEY_FORCE to true)).build(),
        )
    }
}
