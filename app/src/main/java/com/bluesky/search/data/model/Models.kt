package com.bluesky.search.data.model

data class ProfileViewDetailed(
    val did: String = "",
    val handle: String = "",
    val displayName: String? = null,
    val description: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val followersCount: Int = 0,
    val followsCount: Int = 0,
    val postsCount: Int = 0
)

data class ProfileView(
    val did: String = "",
    val handle: String = "",
    val displayName: String? = null,
    val description: String? = null,
    val avatar: String? = null
)

data class FollowersResponse(
    val subject: ProfileView? = null,
    val followers: List<ProfileView> = emptyList(),
    val cursor: String? = null
)

data class FollowsResponse(
    val subject: ProfileView? = null,
    val follows: List<ProfileView> = emptyList(),
    val cursor: String? = null
)
