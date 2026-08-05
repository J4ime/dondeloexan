package com.dondeloexan.domain.model.detail

enum class SocialLinkType { INSTAGRAM, TWITTER, FACEBOOK, YOUTUBE }

data class CastSocialInfo(
    val url: String,
    val type: SocialLinkType
)
