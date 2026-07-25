 #!/bin/bash
echo "📁 إنشاء مشروع BlueskySearch..."

# ═══════════════════════════════════════
# إنشاء المجلدات
# ═══════════════════════════════════════
mkdir -p app/src/main/java/com/bluesky/search/data/model
mkdir -p app/src/main/java/com/bluesky/search/data/api
mkdir -p app/src/main/java/com/bluesky/search/data/repository
mkdir -p app/src/main/java/com/bluesky/search/viewmodel
mkdir -p app/src/main/java/com/bluesky/search/ui/screens
mkdir -p app/src/main/java/com/bluesky/search/ui/components
mkdir -p app/src/main/res/values
mkdir -p gradle/wrapper
mkdir -p .github/workflows

# ═══════════════════════════════════════
# build.gradle.kts (Project)
# ═══════════════════════════════════════
cat > build.gradle.kts << 'EOF'
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
EOF

# ═══════════════════════════════════════
# settings.gradle.kts
# ═══════════════════════════════════════
cat > settings.gradle.kts << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "BlueskySearch"
include(":app")
EOF

# ═══════════════════════════════════════
# gradle.properties
# ═══════════════════════════════════════
cat > gradle.properties << 'EOF'
org.gradle.jvmargs=-Xmx2048m
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
EOF

# ═══════════════════════════════════════
# gradle-wrapper.properties
# ═══════════════════════════════════════
cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# ═══════════════════════════════════════
# .gitignore
# ═══════════════════════════════════════
cat > .gitignore << 'EOF'
.gradle/
build/
*.iml
local.properties
.DS_Store
EOF

# ═══════════════════════════════════════
# .github/workflows/build.yml
# ═══════════════════════════════════════
cat > .github/workflows/build.yml << 'EOF'
name: Build APK

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v3
      - name: Generate Wrapper
        run: gradle wrapper --gradle-version 8.5
      - name: Build Debug APK
        run: ./gradlew assembleDebug --no-daemon
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: bluesky-search-debug
          path: app/build/outputs/apk/debug/*.apk
EOF

# ═══════════════════════════════════════
# app/build.gradle.kts
# ═══════════════════════════════════════
cat > app/build.gradle.kts << 'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.bluesky.search"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bluesky.search"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.core:core-ktx:1.12.0")

    // Retrofit + Gson
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Coil (image loading)
    implementation("io.coil-kt:coil-compose:2.5.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
EOF

# ═══════════════════════════════════════
# AndroidManifest.xml
# ═══════════════════════════════════════
cat > app/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".BlueskyApp"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

    </application>
</manifest>
EOF

# ═══════════════════════════════════════
# strings.xml
# ═══════════════════════════════════════
cat > app/src/main/res/values/strings.xml << 'EOF'
<resources>
    <string name="app_name">Bluesky Search</string>
</resources>
EOF

# ═══════════════════════════════════════
# BlueskyApp.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/BlueskyApp.kt << 'EOF'
package com.bluesky.search

import android.app.Application

class BlueskyApp : Application()
EOF

# ═══════════════════════════════════════
# Models.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/data/model/Models.kt << 'EOF'
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
EOF

# ═══════════════════════════════════════
# BlueskyApi.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/data/api/BlueskyApi.kt << 'EOF'
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
EOF

# ═══════════════════════════════════════
# RetrofitClient.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/data/api/RetrofitClient.kt << 'EOF'
package com.bluesky.search.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL = "https://public.api.bsky.app/xrpc/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: BlueskyApi = retrofit.create(BlueskyApi::class.java)
}
EOF

# ═══════════════════════════════════════
# BlueskyRepository.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/data/repository/BlueskyRepository.kt << 'EOF'
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
EOF

# ═══════════════════════════════════════
# BlueskyViewModel.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/viewmodel/BlueskyViewModel.kt << 'EOF'
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
EOF

# ═══════════════════════════════════════
# Theme.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/ui/Theme.kt << 'EOF'
package com.bluesky.search.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF0085FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFF535F70),
    surface = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFE8E8E8),
    background = Color(0xFFF5F5F5),
    error = Color(0xFFBA1A1A)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A7C),
    secondary = Color(0xFFBBC7DB),
    surface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFF2C2C2C),
    background = Color(0xFF121212),
    error = Color(0xFFFFB4AB)
)

@Composable
fun BlueskyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
EOF

# ═══════════════════════════════════════
# SearchScreen.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/ui/screens/SearchScreen.kt << 'EOF'
package com.bluesky.search.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SearchScreen(
    searchQuery: String,
    isLoading: Boolean,
    error: String?,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "\uD83E\uDD8B", fontSize = 64.sp)

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Bluesky Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Enter a Bluesky handle to view profile,\nfollowers and following",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChanged,
            label = { Text("Bluesky Handle") },
            placeholder = { Text("e.g. jay.bsky.social") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            enabled = !isLoading
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isLoading && searchQuery.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Search", fontWeight = FontWeight.Bold)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Try: jay.bsky.social \u2022 pfrazee.com \u2022 why.bsky.social",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
EOF

# ═══════════════════════════════════════
# UserCard.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/ui/components/UserCard.kt << 'EOF'
package com.bluesky.search.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bluesky.search.data.model.ProfileView

@Composable
fun UserCard(
    user: ProfileView,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (user.avatar != null) {
                AsyncImage(
                    model = user.avatar,
                    contentDescription = user.displayName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .padding(10.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName ?: user.handle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@${user.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (user.description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = user.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    HorizontalDivider()
}
EOF

# ═══════════════════════════════════════
# ProfileScreen.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/ui/screens/ProfileScreen.kt << 'EOF'
package com.bluesky.search.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bluesky.search.data.model.ProfileView
import com.bluesky.search.data.model.ProfileViewDetailed
import com.bluesky.search.ui.components.UserCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profile: ProfileViewDetailed?,
    isLoading: Boolean,
    activeTab: Int,
    followers: List<ProfileView>,
    following: List<ProfileView>,
    isLoadingFollowers: Boolean,
    isLoadingFollowing: Boolean,
    isLoadingMoreFollowers: Boolean,
    isLoadingMoreFollowing: Boolean,
    hasMoreFollowers: Boolean,
    hasMoreFollowing: Boolean,
    error: String?,
    onTabSelected: (Int) -> Unit,
    onLoadMoreFollowers: () -> Unit,
    onLoadMoreFollowing: () -> Unit,
    onUserClick: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile?.handle ?: "Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading profile...")
                    }
                }
            }

            profile != null -> {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    ProfileHeader(profile)

                    TabRow(selectedTabIndex = activeTab) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { onTabSelected(0) },
                            text = { Text("Followers (${profile.followersCount})") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { onTabSelected(1) },
                            text = { Text("Following (${profile.followsCount})") }
                        )
                    }

                    when {
                        activeTab == 0 && isLoadingFollowers -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        activeTab == 1 && isLoadingFollowing -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        activeTab == 0 && followers.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No followers", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        activeTab == 1 && following.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Not following anyone", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> {
                            val users = if (activeTab == 0) followers else following
                            val loadingMore = if (activeTab == 0) isLoadingMoreFollowers else isLoadingMoreFollowing
                            val hasMore = if (activeTab == 0) hasMoreFollowers else hasMoreFollowing
                            val loadMore = if (activeTab == 0) onLoadMoreFollowers else onLoadMoreFollowing

                            UserList(users, loadingMore, hasMore, loadMore, onUserClick)
                        }
                    }
                }
            }

            error != null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: ProfileViewDetailed) {
    val context = LocalContext.current

    Column {
        // Banner
        if (profile.banner != null) {
            AsyncImage(
                model = profile.banner,
                contentDescription = "Banner",
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        )
                    )
            )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // Avatar
            Box(modifier = Modifier.offset(y = (-32).dp)) {
                if (profile.avatar != null) {
                    AsyncImage(
                        model = profile.avatar,
                        contentDescription = profile.displayName,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .padding(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(modifier = Modifier.offset(y = (-16).dp)) {
                if (profile.displayName != null) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "@${profile.handle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (profile.description != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = profile.description,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatItem("Posts", profile.postsCount)
                    StatItem("Followers", profile.followersCount)
                    StatItem("Following", profile.followsCount)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://bsky.app/profile/${profile.handle}")
                                )
                            )
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open in Bluesky")
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        HorizontalDivider()
    }
}

@Composable
private fun StatItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatCount(count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

@Composable
private fun UserList(
    users: List<ProfileView>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(users.size, hasMore, isLoadingMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= users.size - 5 && hasMore && !isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(items = users, key = { it.did }) { user ->
            UserCard(
                user = user,
                onClick = { onUserClick(user.handle) }
            )
        }

        if (isLoadingMore) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}
EOF

# ═══════════════════════════════════════
# MainActivity.kt
# ═══════════════════════════════════════
cat > app/src/main/java/com/bluesky/search/MainActivity.kt << 'EOF'
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
EOF

# ═══════════════════════════════════════
# التحقق
# ═══════════════════════════════════════
echo ""
echo "✅ تم إنشاء جميع الملفات!"
echo ""
echo "📁 ملفات Kotlin:"
find app/src -name "*.kt" | sort
echo ""
echo "📋 ملفات أخرى:"
find app/src -name "*.xml" | sort
echo ""
echo "📋 ملفات البناء:"
ls -la build.gradle.kts settings.gradle.kts gradle.properties .gitignore
