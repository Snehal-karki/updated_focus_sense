package com.example.ui.screens.child.youtube

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FeaturedPlayList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.model.AllowedYouTubeChannelEntity
import com.example.data.remote.YouTubeChannelSearchResult
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.Navy900
import com.example.ui.theme.PurpleAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusYouTubeScreen(
    channels: List<AllowedYouTubeChannelEntity>,
    searchResults: List<YouTubeChannelSearchResult>,
    isSearching: Boolean,
    onSearchChannels: (String) -> Unit,
    onClearSearch: () -> Unit,
    onAddChannelFromSearch: (YouTubeChannelSearchResult) -> Unit,
    onToggleChannelAllowed: (channelId: String, isAllowed: Boolean) -> Unit,
    onRemoveChannel: (channelId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeChannelForView by remember { mutableStateOf<AllowedYouTubeChannelEntity?>(null) }
    var activeUrlForView by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var shortsBlockedNotice by remember { mutableStateOf<String?>(null) }

    if (activeChannelForView != null && activeUrlForView != null) {
        BackHandler {
            activeChannelForView = null
            activeUrlForView = null
        }
        RealYouTubeChannelFullView(
            channel = activeChannelForView!!,
            initialUrl = activeUrlForView!!,
            onClose = {
                activeChannelForView = null
                activeUrlForView = null
            },
            onShortsBlocked = {
                shortsBlockedNotice = "YouTube Shorts are blocked during study mode."
            }
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StudyLoungeHeader(allowedCount = channels.count { it.isAllowed })
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("yt_channel_search_input"),
                        placeholder = { Text("Search study channels...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = IndigoPrimary)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        onClearSearch()
                                    }
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IndigoPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                onSearchChannels(searchQuery.trim())
                            }
                        },
                        enabled = searchQuery.isNotBlank() && !isSearching,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("yt_btn_search_channels")
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Searching...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Search Channels", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(listOf("Striver", "take U forward", "Khan Academy", "freeCodeCamp")) { suggestion ->
                            FilterChip(
                                selected = searchQuery.equals(suggestion, ignoreCase = true),
                                onClick = {
                                    searchQuery = suggestion
                                    onSearchChannels(suggestion)
                                },
                                label = { Text(suggestion, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = IndigoPrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }

            if (searchResults.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Results (${searchResults.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                        TextButton(
                            onClick = onClearSearch,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }

                items(searchResults) { result ->
                    val alreadyAllowed = channels.any {
                        it.channelName.equals(result.title, ignoreCase = true) ||
                            it.handle.equals(result.handle, ignoreCase = true) ||
                            it.channelId == result.channelId
                    }

                    YouTubeSearchResultCard(
                        result = result,
                        isAlreadyAllowed = alreadyAllowed,
                        onAdd = { onAddChannelFromSearch(result) },
                        onViewVideos = {
                            val tempChannel = AllowedYouTubeChannelEntity(
                                channelId = result.channelId,
                                childId = "child-default",
                                channelName = result.title,
                                handle = result.handle,
                                description = result.description,
                                avatarUrl = result.avatarUrl,
                                channelUrl = result.channelUrl,
                                isAllowed = true
                            )
                            activeChannelForView = tempChannel
                            activeUrlForView = "${result.channelUrl}/playlists"
                        }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Allowed Channels",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = IndigoPrimary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${channels.count { it.isAllowed }} active",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (channels.isEmpty()) {
                item {
                    EmptyChannelsPlaceholder(
                        onQuickSearch = { suggested ->
                            searchQuery = suggested
                            onSearchChannels(suggested)
                        }
                    )
                }
            } else {
                items(channels) { channel ->
                    AllowedChannelCard(
                        channel = channel,
                        onViewVideos = {
                            val handleClean = channel.handle.removePrefix("@").trim()
                            val url = if (channel.channelUrl.isNotBlank()) {
                                if (channel.channelUrl.contains("/playlists")) channel.channelUrl else "${channel.channelUrl}/playlists"
                            } else {
                                "https://m.youtube.com/@$handleClean/playlists"
                            }
                            activeChannelForView = channel
                            activeUrlForView = url
                        },
                        onRemove = { onRemoveChannel(channel.channelId) }
                    )
                }
            }
        }
    }

    shortsBlockedNotice?.let { msg ->
        AlertDialog(
            onDismissRequest = { shortsBlockedNotice = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CoralDangerBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CoralDanger, modifier = Modifier.size(26.dp))
                }
            },
            title = {
                Text("FocusSense Study Shield", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            },
            text = {
                Text(
                    text = msg,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { shortsBlockedNotice = null },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Stay Focused", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun YouTubeSearchResultCard(
    result: YouTubeChannelSearchResult,
    isAlreadyAllowed: Boolean,
    onAdd: () -> Unit,
    onViewVideos: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("yt_result_${result.title.replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (result.avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = result.avatarUrl,
                            contentDescription = result.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Subscriptions,
                            contentDescription = null,
                            tint = IndigoPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = result.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = IndigoPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            result.handle.takeIf { it.isNotBlank() },
                            result.subscriberCount.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isAlreadyAllowed) {
                    Surface(
                        color = EmeraldSafeBg,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Allowed", color = EmeraldSafe, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = onAdd,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("yt_btn_allow_${result.title.replace(" ", "_")}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                OutlinedButton(
                    onClick = onViewVideos,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("yt_btn_view_videos_${result.title.replace(" ", "_")}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.FeaturedPlayList, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Playlists", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AllowedChannelCard(
    channel: AllowedYouTubeChannelEntity,
    onViewVideos: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onViewVideos() }
            .testTag("yt_allowed_${channel.channelName.replace(" ", "_")}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(IndigoPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (channel.avatarUrl.isNotBlank()) {
                            AsyncImage(
                                model = channel.avatarUrl,
                                contentDescription = channel.channelName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Subscriptions,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.channelName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = channel.handle,
                            fontSize = 11.sp,
                            color = EmeraldSafe,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove Channel",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onViewVideos,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("yt_btn_open_allowed_${channel.channelName.replace(" ", "_")}")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Study Videos", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealYouTubeChannelFullView(
    channel: AllowedYouTubeChannelEntity,
    initialUrl: String,
    onClose: () -> Unit,
    onShortsBlocked: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf(channel.channelName) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }

    val handleClean = channel.handle.removePrefix("@").trim()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Surface(
            color = Navy900,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (canGoBack && webViewInstance?.canGoBack() == true) {
                                    webViewInstance?.goBack()
                                } else {
                                    onClose()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("yt_view_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Column(modifier = Modifier.padding(start = 2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = pageTitle.ifBlank { channel.channelName },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(14.dp))
                            }
                            Text(
                                text = "Shorts Blocked",
                                fontSize = 11.sp,
                                color = EmeraldSafe,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White, modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Navy800)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (currentUrl.contains("playlist", ignoreCase = true)) IndigoPrimary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                webViewInstance?.loadUrl("https://m.youtube.com/@$handleClean/playlists")
                            }
                    ) {
                        Text(
                            text = "Playlists",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        color = if (currentUrl.contains("video", ignoreCase = true)) IndigoPrimary else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                webViewInstance?.loadUrl("https://m.youtube.com/@$handleClean/videos")
                            }
                    ) {
                        Text(
                            text = "All Videos",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        color = EmeraldSafe,
                        trackColor = Navy800,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT

                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank() && !title.contains("YouTube", ignoreCase = true)) {
                                    pageTitle = title
                                }
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 85
                                if (newProgress > 60) {
                                    injectStudyDistractionBlocker(view)
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString().orEmpty()
                                if (url.contains("/shorts/", ignoreCase = true) ||
                                    url.contains("#shorts", ignoreCase = true) ||
                                    url.contains("&shorts", ignoreCase = true)
                                ) {
                                    onShortsBlocked()
                                    return true
                                }
                                currentUrl = url
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                currentUrl = url.orEmpty()
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                currentUrl = url.orEmpty()
                                canGoBack = view?.canGoBack() == true
                                injectStudyDistractionBlocker(view)
                            }
                        }

                        loadUrl(initialUrl)
                    }
                },
                update = { webView -> webViewInstance = webView }
            )
        }
    }
}

@Composable
private fun StudyLoungeHeader(allowedCount: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(IndigoPrimary, IndigoDark, PurpleAccent))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Study YouTube",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Study YouTube",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = EmeraldSafeBg, shape = RoundedCornerShape(8.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldSafe, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Shorts Blocked",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSafe
                            )
                        }
                    }
                    Surface(color = IndigoPrimary.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = "$allowedCount Channels",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChannelsPlaceholder(
    onQuickSearch: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(IndigoPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Subscriptions,
                    contentDescription = null,
                    tint = IndigoPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "No Study Channels Yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Search for your teachers to watch distraction-free.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onQuickSearch("Striver") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("yt_btn_try_striver")
            ) {
                Text("Search 'Striver'", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

private fun injectStudyDistractionBlocker(webView: WebView?) {
    if (webView == null) return
    val js = """
        (function() {
            var css = `
                [tab-identifier="FEshorts"],
                [aria-label*="Shorts"],
                [title*="Shorts"],
                ytm-reel-shelf-renderer,
                ytd-reel-shelf-renderer,
                ytd-rich-shelf-renderer[is-shorts],
                .ytm-reel-shelf-renderer,
                .reel-shelf,
                ytm-pivot-bar-item-renderer:nth-child(2) {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                }
            `;
            var style = document.getElementById('focussense-study-blocker');
            if (!style) {
                style = document.createElement('style');
                style.id = 'focussense-study-blocker';
                style.type = 'text/css';
                style.appendChild(document.createTextNode(css));
                document.head.appendChild(style);
            }
        })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}
