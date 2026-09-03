package com.dondeloexan.di

import com.dondeloexan.data.backup.BackupManager
import com.dondeloexan.data.backup.BackupRepositoryImpl
import com.dondeloexan.data.catalog.CloudCatalogRepository
import com.dondeloexan.data.local.AppDatabase
import com.dondeloexan.data.local.dao.BlacklistDao
import com.dondeloexan.data.local.dao.CriticReviewDao
import com.dondeloexan.data.local.dao.FaMovieDataDao
import com.dondeloexan.data.local.dao.MovieDao
import com.dondeloexan.data.local.dao.SearchHistoryDao
import com.dondeloexan.data.local.dao.TvShowDao
import com.dondeloexan.data.local.dao.TvShowProgressDao
import com.dondeloexan.data.local.dao.UserPlatformDao
import com.dondeloexan.data.local.datastore.UserPreferencesDataStore
import com.dondeloexan.data.remote.filmaffinity.FilmaffinityScraper
import com.dondeloexan.data.repository.AvailabilityRepositoryImpl
import com.dondeloexan.data.repository.BlacklistRepositoryImpl
import com.dondeloexan.data.repository.DiscoverRepositoryImpl
import com.dondeloexan.data.repository.MovieRepositoryImpl
import com.dondeloexan.data.repository.PlatformRepositoryImpl
import com.dondeloexan.data.repository.SeriesRepositoryImpl
import com.dondeloexan.data.repository.SettingsRepositoryImpl
import com.dondeloexan.data.repository.TrackingRepositoryImpl
import com.dondeloexan.data.sync.AccountRepositoryImpl
import com.dondeloexan.data.sync.SeriesMetadataEnricher
import com.dondeloexan.data.sync.SessionStore
import com.dondeloexan.data.sync.SyncManager
import com.dondeloexan.data.update.SilentUpdateManager
import com.dondeloexan.domain.repository.AccountRepository
import com.dondeloexan.domain.repository.AvailabilityRepository
import com.dondeloexan.domain.repository.BackupRepository
import com.dondeloexan.domain.repository.BlacklistRepository
import com.dondeloexan.domain.repository.DiscoverRepository
import com.dondeloexan.domain.repository.MovieRepository
import com.dondeloexan.domain.repository.PlatformRepository
import com.dondeloexan.domain.repository.SeriesRepository
import com.dondeloexan.domain.repository.SettingsRepository
import com.dondeloexan.domain.repository.TrackingRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import com.dondeloexan.presentation.settings.LibraryNotificationManager
import com.dondeloexan.presentation.settings.LibraryRefresher
import com.dondeloexan.util.RefreshCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
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
    single<BlacklistDao> { get<AppDatabase>().blacklistDao() }
    single<CriticReviewDao> { get<AppDatabase>().criticReviewDao() }
    single<FaMovieDataDao> { get<AppDatabase>().faMovieDataDao() }

    // Backup
    single { BackupManager(get(), androidContext().contentResolver) }
    single<BackupRepository> { BackupRepositoryImpl(get()) }

    // Blacklist
    single<BlacklistRepository> { BlacklistRepositoryImpl(get()) }

    // Platforms
    single<PlatformRepository> { PlatformRepositoryImpl(get()) }

    // Availability preferences
    single<AvailabilityRepository> { AvailabilityRepositoryImpl(get()) }

    // Movies
    single<MovieRepository> { MovieRepositoryImpl(get(), get(), get()) }

    // Series
    single<SeriesRepository> { SeriesRepositoryImpl(get(), get(), get(), get(), get()) }

    // Tracking (estado de películas/series + reconciliación)
    single<TrackingRepository> {
        TrackingRepositoryImpl(
            movieDao = get(),
            tvShowDao = get(),
            tvShowProgressDao = get(),
            tmdbApi = get(),
            cloudCatalog = get(),
            syncManager = get(),
            sessionStore = get()
        )
    }

    // Silent Update
    single { SilentUpdateManager(androidContext()) }

    // Feedback
    single { FeedbackManager() }

    // DataStore
    single { UserPreferencesDataStore(androidContext()) }

    // Cuenta (login + sync)
    single { SessionStore(androidContext()) }
    single { CloudCatalogRepository(syncApi = get(), json = get()) }
    single {
        SyncManager(
            syncApi = get(),
            cloudCatalog = get(),
            movieDao = get(),
            tvShowDao = get(),
            tvShowProgressDao = get(),
            searchHistoryDao = get(),
            userPlatformDao = get(),
            blacklistDao = get(),
            criticReviewDao = get(),
            faMovieDataDao = get(),
            json = get()
        )
    }
    single<AccountRepository> {
        AccountRepositoryImpl(
            authApi = get(),
            syncManager = get(),
            sessionStore = get(),
            seriesMetadataEnricher = get()
        )
    }
    single { SeriesMetadataEnricher(tmdbApi = get(), tvShowDao = get()) }

    // Library
    single { LibraryNotificationManager(androidContext()) }
    single(named("background")) { RefreshCoordinator() }
    single {
        LibraryRefresher(
            tvShowDao = get(),
            movieDao = get(),
            tmdbApi = get(),
            omdbApi = get(),
            refreshCoordinator = get(named("background")),
            userPreferencesDataStore = get(),
            notificationManager = get()
        )
    }

    // Filmaffinity Scraper
    single { FilmaffinityScraper(httpClient = get(named("filmaffinity"))) }

    // Repositories
    single<DiscoverRepository> {
        DiscoverRepositoryImpl(
            tmdbApi = get(),
            omdbApi = get(),
            wikidataApi = get(),
            userPlatformDao = get(),
            movieDao = get(),
            tvShowDao = get(),
            tvShowProgressDao = get(),
            userPreferencesDataStore = get(),
            filmaffinityScraper = get(),
            criticReviewDao = get(),
            faMovieDataDao = get(),
            cloudCatalog = get(),
            syncManager = get(),
            sessionStore = get(),
            trackingRepository = get()
        )
    }

    single<SettingsRepository> {
        SettingsRepositoryImpl(
            gitHubApi = get()
        )
    }
}
