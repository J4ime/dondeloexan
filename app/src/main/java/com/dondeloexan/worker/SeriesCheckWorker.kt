package com.dondeloexan.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dondeloexan.MainActivity
import com.dondeloexan.R
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.remote.api.TmdbApi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SeriesCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {

    private val tvShowDao: TvShowDao by inject()
    private val tmdbApi: TmdbApi by inject()

    override suspend fun doWork(): Result {
        return try {
            val likedShows = tvShowDao.getAllLiked()
            if (likedShows.isEmpty()) return Result.success()

            var newEpisodesToday = 0
            val today = java.time.LocalDate.now().toString()

            for (show in likedShows) {
                val tmdbId = show.tmdbId ?: continue

                try {
                    val tv = tmdbApi.getTvDetail(tmdbId)

                    tvShowDao.update(
                        show.copy(
                            totalEpisodes = tv.numberOfEpisodes ?: show.totalEpisodes,
                            nextEpisodeAirDate = tv.nextEpisodeToAir?.airDate,
                            nextEpisodeNumber = tv.nextEpisodeToAir?.episodeNumber,
                            nextEpisodeSeasonNumber = tv.nextEpisodeToAir?.seasonNumber,
                            seriesStatus = tv.status
                        )
                    )

                    if (tv.nextEpisodeToAir?.airDate == today) {
                        notifyNewEpisode(
                            show.title,
                            tv.nextEpisodeToAir.seasonNumber,
                            tv.nextEpisodeToAir.episodeNumber,
                            tv.nextEpisodeToAir.name
                        )
                        newEpisodesToday++
                    }
                } catch (_: Exception) { }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun notifyNewEpisode(title: String, season: Int, episode: Int, episodeName: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            title.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_popcorn)
            .setContentTitle("Nuevo episodio")
            .setContentText("$title — T${season}E$episode: $episodeName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(applicationContext)
                .notify(title.hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL_ID = "series_new_episodes"
        const val CHANNEL_NAME = "Nuevos episodios"
    }
}
