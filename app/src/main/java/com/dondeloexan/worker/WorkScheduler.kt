package com.dondeloexan.worker

import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val WORK_NAME = "series_daily_check"
    private const val LIBRARY_WORK_NAME = "library_daily_refresh"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val delay = calculateDelayUntil8AM()

        val seriesCheckRequest = PeriodicWorkRequestBuilder<SeriesCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(delay.toMinutes().coerceAtLeast(1), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            seriesCheckRequest
        )

        val libraryRequest = PeriodicWorkRequestBuilder<LibraryRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(delay.toMinutes().coerceAtLeast(1), TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            LIBRARY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            libraryRequest
        )
    }

    private fun calculateDelayUntil8AM(): Duration {
        val now = LocalDateTime.now()
        var eightAM = now.with(LocalTime.of(8, 0))

        if (now.isAfter(eightAM)) {
            eightAM = eightAM.plusDays(1)
        }

        val duration = Duration.between(now, eightAM)
        return if (duration.isNegative()) Duration.ofMinutes(1) else duration
    }
}
