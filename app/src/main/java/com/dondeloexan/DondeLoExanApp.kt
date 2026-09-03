package com.dondeloexan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.di.dataModule
import com.dondeloexan.di.networkModule
import com.dondeloexan.di.viewModelModule
import com.dondeloexan.data.library.LibraryRefresher
import com.dondeloexan.util.TMDB_POSTER_BASE
import com.dondeloexan.worker.SeriesCheckWorker
import com.dondeloexan.worker.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class DondeLoExanApp : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@DondeLoExanApp)
            modules(
                networkModule,
                dataModule,
                viewModelModule
            )
        }

        createNotificationChannel()
        WorkScheduler.schedule(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.get()
                val moviesDao: MovieDao = koin.get()
                val tvShowsDao: TvShowDao = koin.get()
                val repairedMovies = moviesDao.repairRelativePosterUrls(TMDB_POSTER_BASE)
                val repairedTv = tvShowsDao.repairRelativePosterUrls(TMDB_POSTER_BASE)
                if (repairedMovies + repairedTv > 0) {
                    android.util.Log.i(
                        "DondeLoExanApp",
                        "Pósters reparados: $repairedMovies películas, $repairedTv series"
                    )
                }
                val dataStore: UserPreferencesDataStore = koin.get()
                val lastUpdate = dataStore.getLastLibraryUpdateTimestamp()
                if (lastUpdate == null || (System.currentTimeMillis() - lastUpdate) >= 21_600_000L) {
                    val refresher: LibraryRefresher = koin.get()
                    refresher.refresh()
                }
            } catch (e: CancellationException) {
                android.util.Log.w("DondeLoExanApp", "Auto-refresh cancelado (scope)", e)
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DondeLoExanApp", "Auto-refresh failed", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SeriesCheckWorker.CHANNEL_ID,
                SeriesCheckWorker.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
