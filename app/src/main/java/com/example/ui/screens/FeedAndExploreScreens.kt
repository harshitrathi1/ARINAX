package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.core.YouTubeParser
import com.example.data.model.Follow
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.data.model.UserProfile
import com.example.ui.components.GameBadgeTag
import com.example.ui.components.GamerAvatar
import com.example.ui.components.RankBadgePill
import com.example.ui.components.ReportModalDialog
import com.example.ui.components.VerifiedBadgeChip
import com.example.ui.theme.ArenaCardSurface
import com.example.ui.theme.ArenaElevatedSurface
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextSecondarySlate
import com.example.ui.theme.TrophyGold
import com.example.ui.theme.VictoryEmerald

@Composable
fun HomeFeedScreen(
    myProfile: UserProfile?,
    publicProfiles: List<UserProfile>,
    myFollows: List<Follow>,
    posts: List<Post>,
    blockedIds: Set<String>,
    activePostComments: List<PostComment>,
    onNavigateToVerify: () -> Unit,
    onInspectProfile: (UserProfile) -> Unit,
    onToggleFollow: (UserProfile) -> Unit,
    onChallengeUser: (UserProfile) -> Unit,
    onLikePost: (Post) -> Unit,
    onSavePost: (Post) -> Unit,
    onDeletePost: (Post) -> Unit,
    onOpenComments: (Post) -> Unit,
    onCloseComments: () -> Unit,
    onAddComment: (Post, String) -> Unit,
    onReportTarget: (targetType: String, targetId: String, reason: String) -> Unit
) {
    val followingSet = remember(myFollows) { myFollows.map { it.followingId }.toSet() }
    val visiblePosts = remember(posts, blockedIds) {
        posts.filterNot { blockedIds.contains(it.authorId) }
    }
    val suggestedRivals = remember(publicProfiles, myProfile, blockedIds) {
        publicProfiles.filter {
            it.userId != myProfile?.userId && !blockedIds.contains(it.userId)
        }
    }

    // Ensure only one YouTube video plays at a time
    var activePlayingPostId by remember { mutableStateOf<String?>(null) }
    var isVideoUnmuted by remember { mutableStateOf(false) }
    var commentingPost by remember { mutableStateOf<Post?>(null) }
    var reportingPost by remember { mutableStateOf<Post?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_feed_list"),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Verification & Elo Status Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(
                        1.dp,
                        Brush.horizontalGradient(listOf(NeonPurple, CyberCyan)),
                        RoundedCornerShape(18.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (myProfile != null) "@${myProfile.username}" else "Loading Fighter...",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (myProfile?.isVerified == true) {
                                VerifiedBadgeChip()
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (myProfile?.isVerified == true) {
                                "${myProfile.primaryGame} • ${myProfile.primaryRank} • ${myProfile.eloRating} Elo • ${myProfile.arenaPoints} Pts"
                            } else {
                                "Unverified Account — Scan your BGMI/Free Fire screenshot to unlock Challenges"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (myProfile?.isVerified == true) CyberCyan else TrophyGold
                        )
                    }
                    if (myProfile?.isVerified != true) {
                        Button(
                            onClick = onNavigateToVerify,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("home_verify_now_button")
                        ) {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Verify ID", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // 2. Suggested Verified Rivals Carousel
        if (suggestedRivals.isNotEmpty()) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SUGGESTED PRO RIVALS • READY FOR 1V1",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberCyan
                        )
                        Text(
                            text = "${suggestedRivals.size} Online",
                            style = MaterialTheme.typography.labelSmall,
                            color = VictoryEmerald
                        )
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(suggestedRivals, key = { it.userId }) { rival ->
                            val isFollowing = followingSet.contains(rival.userId)
                            SuggestedRivalCard(
                                rival = rival,
                                isFollowing = isFollowing,
                                onInspect = { onInspectProfile(rival) },
                                onFollowToggle = { onToggleFollow(rival) },
                                onChallenge = { onChallengeUser(rival) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Feed Posts (Followed + Public Highlights + Challenge Result Cards)
        if (visiblePosts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Text("No Feed Posts Yet", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Tap the + button below to share a BGMI/Free Fire photo or YouTube video clip!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondarySlate
                        )
                    }
                }
            }
        } else {
            items(visiblePosts, key = { it.id }) { post ->
                val authorProfile = publicProfiles.firstOrNull { it.userId == post.authorId }
                FeedPostCard(
                    post = post,
                    currentUserId = myProfile?.userId ?: "",
                    isFollowedAuthor = followingSet.contains(post.authorId),
                    isPlayingYouTube = activePlayingPostId == post.id,
                    isUnmuted = isVideoUnmuted,
                    onTogglePlayYouTube = {
                        if (activePlayingPostId == post.id) {
                            activePlayingPostId = null
                        } else {
                            activePlayingPostId = post.id
                        }
                    },
                    onToggleMute = { isVideoUnmuted = !isVideoUnmuted },
                    onInspectAuthor = {
                        if (authorProfile != null) onInspectProfile(authorProfile)
                    },
                    onChallengeAuthor = {
                        if (authorProfile != null) onChallengeUser(authorProfile)
                    },
                    onLike = { onLikePost(post) },
                    onSave = { onSavePost(post) },
                    onComment = {
                        commentingPost = post
                        onOpenComments(post)
                    },
                    onDelete = { onDeletePost(post) },
                    onReport = { reportingPost = post }
                )
            }
        }
    }

    if (commentingPost != null) {
        CommentsModalSheet(
            post = commentingPost!!,
            comments = activePostComments,
            onDismiss = {
                commentingPost = null
                onCloseComments()
            },
            onSendComment = { text ->
                onAddComment(commentingPost!!, text)
            }
        )
    }

    if (reportingPost != null) {
        ReportModalDialog(
            targetType = "POST",
            targetId = reportingPost!!.id,
            targetLabel = "@${reportingPost!!.authorUsername}",
            onDismiss = { reportingPost = null },
            onSubmitReport = { reason ->
                onReportTarget("POST", reportingPost!!.id, reason)
            }
        )
    }
}

@Composable
private fun SuggestedRivalCard(
    rival: UserProfile,
    isFollowing: Boolean,
    onInspect: () -> Unit,
    onFollowToggle: () -> Unit,
    onChallenge: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(205.dp)
            .border(1.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onInspect() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GamerAvatar(username = rival.username, isVerified = rival.isVerified, size = 42.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${rival.username}",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${rival.eloRating} Elo • ${rival.wins}W-${rival.losses}L",
                        style = MaterialTheme.typography.labelSmall,
                        color = TrophyGold
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GameBadgeTag(game = rival.primaryGame)
                RankBadgePill(rank = rival.primaryRank)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onFollowToggle,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isFollowing) "Following" else "Follow",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Button(
                    onClick = onChallenge,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("challenge_rival_${rival.username}")
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("1v1", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FeedPostCard(
    post: Post,
    currentUserId: String,
    isFollowedAuthor: Boolean,
    isPlayingYouTube: Boolean,
    isUnmuted: Boolean,
    onTogglePlayYouTube: () -> Unit,
    onToggleMute: () -> Unit,
    onInspectAuthor: () -> Unit,
    onChallengeAuthor: () -> Unit,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit
) {
    val isLiked = post.likedByUids.contains(currentUserId)
    val isSaved = post.savedByUids.contains(currentUserId)
    val isChallengeResult = post.postType == "CHALLENGE_RESULT"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isChallengeResult) ArenaElevatedSurface else ArenaCardSurface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(
                width = 1.dp,
                color = if (isChallengeResult) TrophyGold.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onInspectAuthor() }
                ) {
                    GamerAvatar(username = post.authorUsername, isVerified = post.authorVerified, size = 40.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${post.authorUsername}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            if (isFollowedAuthor) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• Following",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GameBadgeTag(game = post.gameTag)
                            if (post.authorRank.isNotBlank()) {
                                RankBadgePill(rank = post.authorRank)
                            }
                        }
                    }
                }

                if (post.authorId != currentUserId) {
                    Button(
                        onClick = onChallengeAuthor,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple.copy(alpha = 0.85f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Challenge", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Post", tint = CrimsonDanger)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Post Body Media
            when (post.postType) {
                "CHALLENGE_RESULT" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        NeonPurple.copy(alpha = 0.35f),
                                        ArenaObsidian,
                                        CyberCyan.copy(alpha = 0.25f)
                                    )
                                )
                            )
                            .border(1.dp, TrophyGold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = null,
                                tint = TrophyGold,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "OFFICIAL ARENAX MATCH RESULT",
                                style = MaterialTheme.typography.labelLarge,
                                color = TrophyGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = post.caption,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                "YOUTUBE" -> {
                    val videoId = post.youtubeVideoId.ifBlank { "dQw4w9WgXcQ" }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ArenaObsidian)
                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    ) {
                        if (isPlayingYouTube) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            ) {
                                val muteParam = if (isUnmuted) "0" else "1"
                                val embedHtml = remember(videoId, muteParam) {
                                    """
                                    <!DOCTYPE html>
                                    <html>
                                    <body style="margin:0;padding:0;background:#000;">
                                    <iframe width="100%" height="100%"
                                      src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=$muteParam&playsinline=1&rel=0"
                                      frameborder="0" allow="autoplay; encrypted-media" allowfullscreen>
                                    </iframe>
                                    </body>
                                    </html>
                                    """.trimIndent()
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.mediaPlaybackRequiresUserGesture = false
                                            webChromeClient = WebChromeClient()
                                            webViewClient = WebViewClient()
                                        }
                                    },
                                    update = { webView ->
                                        webView.loadDataWithBaseURL(
                                            "https://www.youtube.com",
                                            embedHtml,
                                            "text/html",
                                            "utf-8",
                                            null
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(195.dp)
                                    .clickable { onTogglePlayYouTube() },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = YouTubeParser.thumbnailUrl(videoId),
                                    contentDescription = post.youtubeTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f))
                                )
                                Surface(
                                    color = NeonPurple.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Play YouTube Highlight",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = post.youtubeTitle.ifBlank { "YouTube Gameplay Clip" },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CyberCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "YouTube ID: $videoId • Single-active player",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondarySlate
                                )
                            }
                            Row {
                                IconButton(onClick = onToggleMute) {
                                    Icon(
                                        imageVector = if (isUnmuted) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                        contentDescription = "Toggle Mute",
                                        tint = CyberCyan
                                    )
                                }
                                IconButton(onClick = onTogglePlayYouTube) {
                                    Icon(
                                        imageVector = if (isPlayingYouTube) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Stop",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    // Photo Highlight Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF1E1038),
                                        Color(0xFF0F293A),
                                        Color(0xFF120B24)
                                    )
                                )
                            )
                            .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (post.mediaUrl) {
                                    "preset_tdm_stats" -> "🎯 TDM MVP SCOREBOARD • 24 KILLS / 3 DEATHS"
                                    "preset_conqueror_tier" -> "🏆 CONQUEROR TIER PROMOTION VERIFIED"
                                    else -> "🔥 1v4 SQUAD WIPE HIGHLIGHT CAPTURE"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = CyberCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = post.caption,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (post.postType != "CHALLENGE_RESULT") {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }

            if (post.hashtags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.hashtags.forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyberCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Bar: Like, Comment, Save, Report
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isLiked) CrimsonDanger else Color.White
                        )
                    }
                    Text("${post.likesCount}", style = MaterialTheme.typography.labelLarge, color = Color.White)

                    Spacer(modifier = Modifier.width(12.dp))

                    IconButton(onClick = onComment) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Comments",
                            tint = CyberCyan
                        )
                    }
                    Text("${post.commentsCount}", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Save Post",
                            tint = if (isSaved) TrophyGold else TextSecondarySlate
                        )
                    }
                    IconButton(onClick = onReport) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = "Report Post",
                            tint = TextSecondarySlate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentsModalSheet(
    post: Post,
    comments: List<PostComment>,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit
) {
    var commentInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCardSurface,
        title = {
            Text("Comments (${comments.size})", style = MaterialTheme.typography.titleLarge, color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (comments.isEmpty()) {
                    Text(
                        text = "No comments yet. Drop the first GG!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondarySlate
                    )
                } else {
                    comments.takeLast(8).forEach { c ->
                        Surface(
                            color = ArenaElevatedSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "@${c.authorUsername}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyberCyan
                                )
                                Text(
                                    text = c.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text("Add a comment...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                onSendComment(commentInput)
                                commentInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = NeonPurple)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun ExploreScreen(
    myProfile: UserProfile?,
    publicProfiles: List<UserProfile>,
    myFollows: List<Follow>,
    blockedIds: Set<String>,
    onInspectProfile: (UserProfile) -> Unit,
    onToggleFollow: (UserProfile) -> Unit,
    onChallengeUser: (UserProfile) -> Unit,
    onMessageUser: (UserProfile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGameFilter by remember { mutableStateOf("ALL") }
    var selectedRankFilter by remember { mutableStateOf("ALL") }
    val followingSet = remember(myFollows) { myFollows.map { it.followingId }.toSet() }

    val filteredPlayers = remember(publicProfiles, searchQuery, selectedGameFilter, selectedRankFilter, blockedIds) {
        publicProfiles.filter { player ->
            if (blockedIds.contains(player.userId)) return@filter false
            val matchesQuery = searchQuery.isBlank() ||
                player.username.contains(searchQuery, ignoreCase = true) ||
                player.primaryIgn.contains(searchQuery, ignoreCase = true) ||
                player.primaryGameUid.contains(searchQuery, ignoreCase = true)
            val matchesGame = selectedGameFilter == "ALL" || player.primaryGame == selectedGameFilter
            val matchesRank = selectedRankFilter == "ALL" ||
                player.primaryRank.contains(selectedRankFilter, ignoreCase = true)
            matchesQuery && matchesGame && matchesRank
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyberCyan) },
                label = { Text("Search by @username, IGN, or Game UID") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explore_search_input")
            )
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("ALL" to "All Games", "BGMI" to "BGMI", "FREE_FIRE" to "Free Fire").forEach { (key, label) ->
                    FilterChip(
                        selected = selectedGameFilter == key,
                        onClick = { selectedGameFilter = key },
                        label = { Text(label) }
                    )
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ranks = listOf("ALL", "Conqueror", "Grandmaster", "Ace", "Heroic")
                items(ranks) { rank ->
                    FilterChip(
                        selected = selectedRankFilter == rank,
                        onClick = { selectedRankFilter = rank },
                        label = { Text(if (rank == "ALL") "All Ranks" else rank) }
                    )
                }
            }
        }

        item {
            Text(
                text = "VERIFIED PLAYERS (${filteredPlayers.size})",
                style = MaterialTheme.typography.labelLarge,
                color = CyberCyan
            )
        }

        items(filteredPlayers, key = { it.userId }) { player ->
            val isMe = player.userId == myProfile?.userId
            val isFollowing = followingSet.contains(player.userId)
            Card(
                colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .clickable { onInspectProfile(player) }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            GamerAvatar(username = player.username, isVerified = player.isVerified, size = 48.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "@${player.username}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White
                                    )
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(You)", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                                    }
                                }
                                Text(
                                    text = "IGN: ${player.primaryIgn.ifBlank { "Unlinked" }} • UID: ${player.primaryGameUid.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondarySlate
                                )
                                Text(
                                    text = "${player.language} • ${player.playTime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondarySlate
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${player.eloRating} ELO",
                                style = MaterialTheme.typography.titleMedium,
                                color = TrophyGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${player.wins}W - ${player.losses}L",
                                style = MaterialTheme.typography.labelSmall,
                                color = VictoryEmerald
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GameBadgeTag(game = player.primaryGame)
                        RankBadgePill(rank = player.primaryRank)
                        if (player.isVerified) {
                            VerifiedBadgeChip()
                        }
                    }

                    if (!isMe) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onToggleFollow(player) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isFollowing) "Unfollow" else "Follow")
                            }
                            OutlinedButton(
                                onClick = { onMessageUser(player) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Message")
                            }
                            Button(
                                onClick = { onChallengeUser(player) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                modifier = Modifier.weight(1.1f)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Challenge")
                            }
                        }
                    }
                }
            }
        }
    }
}
