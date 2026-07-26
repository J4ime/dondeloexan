package com.dondeloexan.domain.model

data class CriticReview(
    val author: String,
    val publication: String,
    val text: String,
    val rating: String? = null,
    val url: String? = null,
    val sentiment: Sentiment = Sentiment.NEUTRAL
)

enum class Sentiment {
    POSITIVE, NEGATIVE, NEUTRAL
}
