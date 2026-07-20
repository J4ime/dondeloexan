package com.dondeloexan.di

import com.dondeloexan.presentation.availability.AvailabilityViewModel
import com.dondeloexan.presentation.blacklist.BlacklistViewModel
import com.dondeloexan.presentation.detail.MediaDetailViewModel
import com.dondeloexan.presentation.discover.DiscoverViewModel
import com.dondeloexan.presentation.movies.MoviesViewModel
import com.dondeloexan.presentation.platforms.PlatformsViewModel
import com.dondeloexan.presentation.series.SeriesViewModel
import com.dondeloexan.presentation.settings.LogViewerViewModel
import com.dondeloexan.presentation.settings.SettingsViewModel
import com.dondeloexan.util.RefreshCoordinator
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val viewModelModule = module {
    singleOf(::RefreshCoordinator)

    viewModelOf(::AvailabilityViewModel)
    viewModelOf(::DiscoverViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LogViewerViewModel)
    viewModelOf(::PlatformsViewModel)
    viewModelOf(::MediaDetailViewModel)
    viewModelOf(::MoviesViewModel)
    viewModelOf(::SeriesViewModel)
    viewModelOf(::BlacklistViewModel)
}
