package com.bluesky.search.data.api

import com.bluesky.search.data.model.FollowersResponse
import com.bluesky.search.data.model.FollowsResponse
import com.bluesky.search.data.model.ProfileViewDetailed
import retrofit2.http.GET
import retrofit2.http.Query

interface BlueskyApi {

    @GET("app.bsky.actor.getProfile")
    suspend fun getProfile(
        @Query("actor") actor: String
    ): ProfileViewDetailed

    @GET("app.bsky.graph.getFollowers")
    suspend fun getFollowers(
        @Query("actor") actor: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null
    ): FollowersResponse

    @GET("app.bsky.graph.getFollows")
    suspend fun getFollows(
        @Query("actor") actor: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null
    ): FollowsResponse
}
