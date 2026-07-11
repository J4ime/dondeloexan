package com.dondeloexan.di

import com.dondeloexan.data.backup.BackupManager
import com.dondeloexan.data.backup.BackupRepositoryImpl
import com.dondeloexan.data.local.AppDatabase
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.SearchHistoryDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.repository.DiscoverRepositoryImpl
import com.dondeloexan.data.repository.SettingsRepositoryImpl
import com.dondeloexan.data.update.SilentUpdateManager
import com.dondeloexan.domain.repository.BackupRepository
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.domain.repository.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {

    // Room Database
    single { AppDatabase.create(androidContext()) }

    // DAOs
    single<MovieDao> { get<AppDatabase>().movieDao() }
    single<TvShowDao> { get<AppDatabase>().tvShowDao() }
    single<TvShowProgressDao> { get<AppDatabase>().tvShowProgressDao() }
    single<SearchHistoryDao> { get<AppDatabase>().searchHistoryDao() }
    single<UserPlatformDao> { get<AppDatabase>().userPlatformDao() }

    // Backup
    single { BackupManager(get(), androidContext().contentResolver) }
    single<BackupRepository> { BackupRepositoryImpl(get()) }

    // Silent Update
    single { SilentUpdateManager(androidContext()) }

    // Repositories
    single<DiscoverRepository> {
        DiscoverRepositoryImpl(
            filmAffinityApi = get(),
            tmdbApi = get(),
            omdbApi = get(),
            userPlatformDao = get(),
            movieDao = get(),
            tvShowDao = get()
        )
    }

    single<SettingsRepository> {
        SettingsRepositoryImpl(
            gitHubApi = get()
        )
    }
}
