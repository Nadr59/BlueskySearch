package com.bluesky.search.data.repository

import com.bluesky.search.data.api.RetrofitClient
import com.bluesky.search.data.model.FollowersResponse
import com.bluesky.search.data.model.FollowsResponse
import com.bluesky.search.data.model.ProfileViewDetailed
import kotlinx.coroutines.delay

class BlueskyRepository {

    private val api = RetrofitClient.api

    suspend fun getProfile(handle: String): ProfileViewDetailed {
        return api.getProfile(handle)
    }

    suspend fun getFollowers(handle: String, cursor: String? = null): FollowersResponse {
        if (cursor != null) delay(500)
        return api.getFollowers(handle, cursor = cursor)
    }

    suspend fun getFollows(handle: String, cursor: String? = null): FollowsResponse {
        if (cursor != null) delay(500)
        return api.getFollows(handle, cursor = cursor)
    }
}
