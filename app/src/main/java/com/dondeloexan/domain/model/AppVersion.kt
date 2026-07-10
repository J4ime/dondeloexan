package com.dondeloexan.domain.model

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val tagName: String,
    val releaseDate: String? = null,
    val changelog: String = "",
    val htmlUrl: String = "",
    val apkDownloadUrl: String? = null
) : Comparable<AppVersion> {

    val displayName: String get() = "$major.$minor.$patch"

    fun isNewerThan(other: AppVersion): Boolean = this > other

    override fun compareTo(other: AppVersion): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        return patch.compareTo(other.patch)
    }

    companion object {
        fun fromTag(tagName: String): AppVersion? {
            val cleaned = tagName.removePrefix("v").removePrefix("V").trim()
            val parts = cleaned.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size < 2) return null
            return AppVersion(
                major = parts.getOrElse(0) { 0 },
                minor = parts.getOrElse(1) { 0 },
                patch = parts.getOrElse(2) { 0 },
                tagName = tagName
            )
        }
    }
}

data class GitHubRelease(
    val version: AppVersion,
    val name: String,
    val publishedAt: String? = null,
    val changelog: String = "",
    val htmlUrl: String,
    val apkDownloadUrl: String? = null
)
