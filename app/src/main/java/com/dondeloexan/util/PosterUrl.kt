package com.dondeloexan.util

const val TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500"

fun normalizePosterUrl(url: String?): String? {
    if (url.isNullOrBlank()) return url
    if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) return url
    val path = if (url.startsWith("/")) url else "/$url"
    return "$TMDB_POSTER_BASE$path"
}