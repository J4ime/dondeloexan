package com.dondeloexan.domain.model

data class SessionState(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val email: String
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > expiresAt - 60_000
}
