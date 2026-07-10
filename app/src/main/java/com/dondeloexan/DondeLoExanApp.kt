package com.dondeloexan

import android.app.Application
import com.dondeloexan.di.dataModule
import com.dondeloexan.di.networkModule
import com.dondeloexan.di.viewModelModule
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
    }
}
