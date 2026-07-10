package com.dondeloexan.di

import com.dondeloexan.presentation.discover.DiscoverViewModel
import com.dondeloexan.presentation.platforms.PlatformsViewModel
import com.dondeloexan.presentation.settings.LogViewerViewModel
import com.dondeloexan.presentation.settings.SettingsViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::DiscoverViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LogViewerViewModel)
    viewModelOf(::PlatformsViewModel)
}
