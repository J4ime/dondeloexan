package com.dondeloexan.worker

import android.content.Context
import android.os.Build
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

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val delay = calculateDelayUntil8AM()

        val request = PeriodicWorkRequestBuilder<SeriesCheckWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setInitialDelay(delay.toMinutes().coerceAtLeast(1), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
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
