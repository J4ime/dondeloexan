package com.dondeloexan.data.remote

object TmdbProviderIds {
    private val platformToProviderId = mapOf(
        "Netflix" to 8,
        "Prime Video" to 119,
        "Disney+" to 337,
        "HBO Max" to 1899,
        "Movistar+" to 2241,
        "Apple TV+" to 350,
        "SkyShowtime" to 1773,
        "Paramount+" to 5315,
        "Filmin" to 63,
        "Atresplayer" to 62,
        "RTVE Play" to 541
    )

    val providerIdToName: Map<Int, String> = platformToProviderId.entries.associateBy(
        { it.value },
        { it.key }
    )

    fun toProviderIds(platformNames: Set<String>): Set<Int> {
        return platformNames.mapNotNull { platformToProviderId[it] }.toSet()
    }

    fun toPipeSeparated(platformNames: Set<String>): String? {
        val ids = toProviderIds(platformNames)
        if (ids.isEmpty()) return null
        return ids.joinToString("|")
    }

    fun hasNoDiscoverIds(platformNames: Set<String>): Boolean {
        return platformNames.isNotEmpty() && platformNames.all { it !in platformToProviderId }
    }
}
