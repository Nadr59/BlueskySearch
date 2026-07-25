package com.bluesky.search.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluesky.search.data.model.ProfileView
import com.bluesky.search.data.model.ProfileViewDetailed
import com.bluesky.search.data.repository.BlueskyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class BlueskyUiState(
    val searchQuery: String = "",
    val profile: ProfileViewDetailed? = null,
    val isLoadingProfile: Boolean = false,
    val activeTab: Int = 0,
    val followers: List<ProfileView> = emptyList(),
    val followersCursor: String? = null,
    val isLoadingFollowers: Boolean = false,
    val isLoadingMoreFollowers: Boolean = false,
    val hasMoreFollowers: Boolean = true,
    val following: List<ProfileView> = emptyList(),
    val followingCursor: String? = null,
    val isLoadingFollowing: Boolean = false,
    val isLoadingMoreFollowing: Boolean = false,
    val hasMoreFollowing: Boolean = true,
    val error: String? = null
)

class BlueskyViewModel : ViewModel() {

    private val repository = BlueskyRepository()

    private val _uiState = MutableStateFlow(BlueskyUiState())
    val uiState: StateFlow<BlueskyUiState> = _uiState.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, error = null)
    }

    fun searchProfile() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please enter a handle")
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoadingProfile = true,
            error = null,
            profile = null,
            followers = emptyList(),
            following = emptyList(),
            followersCursor = null,
            followingCursor = null,
            hasMoreFollowers = true,
            hasMoreFollowing = true
        )

        viewModelScope.launch {
            try {
                val profile = repository.getProfile(query)
                _uiState.value = _uiState.value.copy(
                    profile = profile,
                    isLoadingProfile = false
                )
                loadFollowers(query)
                loadFollowing(query)
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(
                    isLoadingProfile = false,
                    error = when (e.code()) {
                        400 -> "Account not found: $query"
                        else -> "Server error (${e.code()})"
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingProfile = false,
                    error = "Connection failed: ${e.message}"
                )
            }
        }
    }

    fun onTabSelected(tab: Int) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    private fun loadFollowers(handle: String) {
        _uiState.value = _uiState.value.copy(isLoadingFollowers = true)

        viewModelScope.launch {
            try {
                val response = repository.getFollowers(handle)
                _uiState.value = _uiState.value.copy(
                    followers = response.followers,
                    followersCursor = response.cursor,
                    isLoadingFollowers = false,
                    hasMoreFollowers = response.cursor != null
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingFollowers = false)
            }
        }
    }

    private fun loadFollowing(handle: String) {
        _uiState.value = _uiState.value.copy(isLoadingFollowing = true)

        viewModelScope.launch {
            try {
                val response = repository.getFollows(handle)
                _uiState.value = _uiState.value.copy(
                    following = response.follows,
                    followingCursor = response.cursor,
                    isLoadingFollowing = false,
                    hasMoreFollowing = response.cursor != null
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingFollowing = false)
            }
        }
    }

    fun loadMoreFollowers() {
        val s = _uiState.value
        val handle = s.profile?.handle ?: return
        val cursor = s.followersCursor ?: return
        if (s.isLoadingMoreFollowers || !s.hasMoreFollowers) return

        _uiState.value = s.copy(isLoadingMoreFollowers = true)

        viewModelScope.launch {
            try {
                val response = repository.getFollowers(handle, cursor)
                _uiState.value = _uiState.value.copy(
                    followers = _uiState.value.followers + response.followers,
                    followersCursor = response.cursor,
                    isLoadingMoreFollowers = false,
                    hasMoreFollowers = response.cursor != null
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMoreFollowers = false)
            }
        }
    }

    fun loadMoreFollowing() {
        val s = _uiState.value
        val handle = s.profile?.handle ?: return
        val cursor = s.followingCursor ?: return
        if (s.isLoadingMoreFollowing || !s.hasMoreFollowing) return

        _uiState.value = s.copy(isLoadingMoreFollowing = true)

        viewModelScope.launch {
            try {
                val response = repository.getFollows(handle, cursor)
                _uiState.value = _uiState.value.copy(
                    following = _uiState.value.following + response.follows,
                    followingCursor = response.cursor,
                    isLoadingMoreFollowing = false,
                    hasMoreFollowing = response.cursor != null
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMoreFollowing = false)
            }
        }
    }

    fun searchUser(handle: String) {
        _uiState.value = _uiState.value.copy(searchQuery = handle)
        searchProfile()
    }

    fun onBackToSearch() {
        _uiState.value = BlueskyUiState(searchQuery = _uiState.value.searchQuery)
    }
}
