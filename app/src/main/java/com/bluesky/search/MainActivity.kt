package com.bluesky.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bluesky.search.ui.BlueskyTheme
import com.bluesky.search.ui.screens.ProfileScreen
import com.bluesky.search.ui.screens.SearchScreen
import com.bluesky.search.viewmodel.BlueskyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueskyTheme {
                val vm: BlueskyViewModel = viewModel()
                val s by vm.uiState.collectAsState()

                if (s.profile != null || s.isLoadingProfile) {
                    ProfileScreen(
                        profile = s.profile,
                        isLoading = s.isLoadingProfile,
                        activeTab = s.activeTab,
                        followers = s.followers,
                        following = s.following,
                        isLoadingFollowers = s.isLoadingFollowers,
                        isLoadingFollowing = s.isLoadingFollowing,
                        isLoadingMoreFollowers = s.isLoadingMoreFollowers,
                        isLoadingMoreFollowing = s.isLoadingMoreFollowing,
                        hasMoreFollowers = s.hasMoreFollowers,
                        hasMoreFollowing = s.hasMoreFollowing,
                        error = s.error,
                        onTabSelected = { vm.onTabSelected(it) },
                        onLoadMoreFollowers = { vm.loadMoreFollowers() },
                        onLoadMoreFollowing = { vm.loadMoreFollowing() },
                        onUserClick = { vm.searchUser(it) },
                        onBack = { vm.onBackToSearch() }
                    )
                } else {
                    SearchScreen(
                        searchQuery = s.searchQuery,
                        isLoading = s.isLoadingProfile,
                        error = s.error,
                        onQueryChanged = { vm.onSearchQueryChanged(it) },
                        onSearch = { vm.searchProfile() }
                    )
                }
            }
        }
    }
}
