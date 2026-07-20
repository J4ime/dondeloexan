package com.dondeloexan.presentation.settings

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dondeloexan.MainActivity
import com.dondeloexan.R
import com.dondeloexan.util.AppLogger

data class NewEpisodeDateInfo(
    val seriesTitle: String,
    val season: Int,
    val episode: Int,
    val airDate: String
)

data class NewPlatformInfo(
    val movieTitle: String,
    val platformNames: List<String>
)

class LibraryNotificationManager(private val context: Context) {

    fun notifyChanges(
        newEpisodeDates: List<NewEpisodeDateInfo>,
        newPlatforms: List<NewPlatformInfo>
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        if (newEpisodeDates.isNotEmpty()) {
            notifyNewEpisodeDates(newEpisodeDates, pendingIntent)
        }
        if (newPlatforms.isNotEmpty()) {
            notifyNewPlatforms(newPlatforms, pendingIntent)
        }
    }

    private fun notifyNewEpisodeDates(
        items: List<NewEpisodeDateInfo>,
        pendingIntent: PendingIntent
    ) {
        if (items.size == 1) {
            val ep = items.first()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_popcorn)
                .setContentTitle("Nueva fecha de estreno")
                .setContentText("${ep.seriesTitle} — T${ep.season}E${ep.episode} (${ep.airDate})")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            safeNotify(NOTIFICATION_ID_NEW_DATE, notification)
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("${items.size} nuevas fechas de estreno")
            items.forEach { ep ->
                inboxStyle.addLine("${ep.seriesTitle} — T${ep.season}E${ep.episode} (${ep.airDate})")
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_popcorn)
                .setContentTitle("${items.size} nuevas fechas de estreno")
                .setContentText("Toca para ver detalles")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            safeNotify(NOTIFICATION_ID_NEW_DATE, notification)
        }
    }

    private fun notifyNewPlatforms(
        items: List<NewPlatformInfo>,
        pendingIntent: PendingIntent
    ) {
        if (items.size == 1) {
            val movie = items.first()
            val platformsText = movie.platformNames.joinToString(", ")
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_popcorn)
                .setContentTitle("Nueva plataforma disponible")
                .setContentText("${movie.movieTitle} — $platformsText")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            safeNotify(NOTIFICATION_ID_NEW_PLATFORM, notification)
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle("${items.size} películas con nuevas plataformas")
            items.forEach { movie ->
                val platformsText = movie.platformNames.joinToString(", ")
                inboxStyle.addLine("${movie.movieTitle} — $platformsText")
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_popcorn)
                .setContentTitle("${items.size} películas con nuevas plataformas")
                .setContentText("Toca para ver detalles")
                .setStyle(inboxStyle)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            safeNotify(NOTIFICATION_ID_NEW_PLATFORM, notification)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun safeNotify(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: Exception) {
            AppLogger.e("LibraryNotificationMgr", "Error showing notification $id", e)
        }
    }

    companion object {
        const val CHANNEL_ID = "series_new_episodes"
        private const val NOTIFICATION_ID_NEW_DATE = 1001
        private const val NOTIFICATION_ID_NEW_PLATFORM = 1002
    }
}
