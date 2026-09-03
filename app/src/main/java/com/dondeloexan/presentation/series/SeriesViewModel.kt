package com.dondeloexan.presentation.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dondeloexan.domain.model.SeriesItem
import com.dondeloexan.domain.repository.SeriesRepository
import com.dondeloexan.presentation.feedback.FeedbackManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SeriesViewModel(
    private val repository: SeriesRepository,
    private val feedbackManager: FeedbackManager
) : ViewModel() {

    private fun List<SeriesItem>.sortedByLastWatched(): List<SeriesItem> =
        sortedWith(compareByDescending<SeriesItem> { it.lastWatchedAt ?: Long.MIN_VALUE }.thenBy { it.addedAt })

    val pending: StateFlow<List<SeriesItem>> = repository.all.map { list ->
        list.filter { s -> s.watchedCount == 0 }.sortedByLastWatched()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inProgress: StateFlow<List<SeriesItem>> = repository.all.map { list ->
        list.filter { s -> s.watchedCount > 0 && !s.isCaughtUp() && !s.isFinished() }.sortedByLastWatched()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finished: StateFlow<List<SeriesItem>> = repository.all.map { list ->
        list.filter { s -> s.isFinished() }.sortedByLastWatched()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingAgenda: StateFlow<List<SeriesItem>> = repository.all.map { list ->
        list.filter { s ->
            s.isCaughtUp() && s.hasFutureSeasons() && !s.isFinished()
        }.sortedWith(compareBy(nullsLast<String>()) { it.nextEpisodeAirDate })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refreshSeriesData() {
        viewModelScope.launch { repository.refreshData() }
    }

    fun deleteSeries(show: SeriesItem) {
        viewModelScope.launch {
            repository.delete(show.id)
            feedbackManager.emit("Serie eliminada")
        }
    }

    fun toggleWatched(show: SeriesItem) {
        viewModelScope.launch {
            val nowWatched = repository.toggleWatched(show.id)
            feedbackManager.emit(
                if (nowWatched) "Serie marcada como vista"
                else "Serie quitada de vistos"
            )
        }
    }
}
