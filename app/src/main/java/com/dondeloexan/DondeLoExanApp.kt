package com.dondeloexan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dondeloexan.di.dataModule
import com.dondeloexan.di.networkModule
import com.dondeloexan.di.viewModelModule
import com.dondeloexan.worker.SeriesCheckWorker
import com.dondeloexan.worker.WorkScheduler
import org.koin.android.ext.koin.androidContext
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
