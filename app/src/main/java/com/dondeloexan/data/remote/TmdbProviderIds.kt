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
        "Filmin" to 5681,
        "Atresplayer" to 5765,
        "Mitele" to 4378,
        "RTVE Play" to 5764
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
