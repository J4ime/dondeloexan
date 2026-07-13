package com.dondeloexan.worker

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.time.LocalDate

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

            val today = LocalDate.now().toString()
            val todayEpisodes = mutableListOf<EpisodeInfo>()

            for (show in likedShows) {
                val tmdbId = show.tmdbId

                if (tmdbId != null) {
                    updateFromTmdb(show.id, tmdbId)
                }

                if (show.nextEpisodeAirDate == today) {
                    todayEpisodes.add(
                        EpisodeInfo(
                            title = show.title,
                            season = show.nextEpisodeSeasonNumber ?: 0,
                            episode = show.nextEpisodeNumber ?: 0,
                            episodeName = null
                        )
                    )
                }
            }

            if (todayEpisodes.isNotEmpty()) {
                notifyNewEpisodes(todayEpisodes)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private suspend fun updateFromTmdb(showId: Long, tmdbId: Int) {
        try {
            val tv = tmdbApi.getTvDetail(tmdbId)
            tvShowDao.updateById(
                id = showId,
                totalEpisodes = tv.numberOfEpisodes,
                nextEpisodeAirDate = tv.nextEpisodeToAir?.airDate,
                nextEpisodeNumber = tv.nextEpisodeToAir?.episodeNumber,
                nextEpisodeSeasonNumber = tv.nextEpisodeToAir?.seasonNumber,
                seriesStatus = tv.status
            )
        } catch (_: Exception) { }
    }

    private fun notifyNewEpisodes(episodes: List<EpisodeInfo>) {
        if (!hasNotificationPermission()) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (episodes.size == 1) {
            val ep = episodes.first()
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_popcorn)
                .setContentTitle("Nuevo episodio")
                .setContentText("${ep.title} — T${ep.season}E${ep.episode}")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            safeNotify(ep.title.hashCode(), notification)
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
            episodes.forEach { ep ->
                inboxStyle.addLine("${ep.title} — T${ep.season}E${ep.episode}")
            }
            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_popcorn)
                .setContentTitle("${episodes.size} nuevos episodios")
                .setContentText("Hoy se estrenan ${episodes.size} episodios")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            safeNotify(episodes.hashCode(), notification)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun safeNotify(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (_: Exception) { }
    }

    private data class EpisodeInfo(
        val title: String,
        val season: Int,
        val episode: Int,
        val episodeName: String?
    )

    companion object {
        const val CHANNEL_ID = "series_new_episodes"
        const val CHANNEL_NAME = "Nuevos episodios"
    }
}
