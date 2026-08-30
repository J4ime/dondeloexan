package com.dondeloexan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dondeloexan.presentation.settings.LibraryRefresher
import com.dondeloexan.util.AppLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class LibraryRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val refresher: LibraryRefresher by inject()

    override suspend fun doWork(): Result {
        return try {
            val result = refresher.refresh()
            AppLogger.i(
                "LibraryRefreshWorker",
                "Library refreshed: series=${result.seriesUpdated}, movies=${result.moviesUpdated}"
            )
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLogger.w("LibraryRefreshWorker", "Trabajo cancelado (scope): ${e.message}")
            throw e
        } catch (e: Exception) {
            AppLogger.e("LibraryRefreshWorker", "Error refreshing library", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}