package com.dondeloexan.presentation.feedback

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class FeedbackManager {

    private val _events = Channel<String?>(Channel.BUFFERED)
    val events: Flow<String?> = _events.receiveAsFlow()

    fun emit(message: String) {
        _events.trySend(message)
    }
}