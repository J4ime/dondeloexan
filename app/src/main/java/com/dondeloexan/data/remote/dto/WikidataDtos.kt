package com.dondeloexan.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class WikidataSparqlResponse(
    val results: WikidataResults
)

@Serializable
data class WikidataResults(
    val bindings: List<Map<String, WikidataValue>>
)

@Serializable
data class WikidataValue(
    val type: String,
    val value: String
)
