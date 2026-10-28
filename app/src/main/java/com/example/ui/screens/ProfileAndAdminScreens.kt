package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ArenaNotification
import com.example.data.model.Challenge
import com.example.data.model.Follow
import com.example.data.model.GameProfile
import com.example.data.model.ModerationReport
import com.example.data.model.Post
import com.example.data.model.UserBlock
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
import com.example.ui.viewmodel.VerificationScanState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    myProfile: UserProfile?,
    inspectedProfile: UserProfile?,
    myGameProfiles: List<GameProfile>,
    myFollows: List<Follow>,
    allPosts: List<Post>,
    notifications: List<ArenaNotification>,
    blockedUsers: List<UserBlock>,
    myReports: List<ModerationReport>,
    disputedChallenges: List<Challenge>,
    verificationScanState: VerificationScanState,
    onClearInspectedProfile: () -> Unit,
    onToggleFollow: (UserProfile) -> Unit,
    onMessageUser: (UserProfile) -> Unit,
    onChallengeUser: (UserProfile) -> Unit,
    onBlockOrUnblockUser: (String, String) -> Unit,
    onReportTarget: (String, String, String) -> Unit,
    onSaveProfileSettings: (
        username: String,
        displayName: String,
        bio: String,
        primaryGame: String,
        language: String,
        playTime: String,
        isPrivate: Boolean
    ) -> Unit,
    onGenerateAndScanSampleScreenshot: (
        game: String,
        ign: String,
        uid: String,
        rank: String,
        level: Int,
        code: String
    ) -> Unit,
    onScanGalleryBitmap: (android.graphics.Bitmap, String, String) -> Unit,
    onConfirmExtractedVerification: (com.example.data.model.ExtractedPlayerVerification) -> Unit,
    onResetVerificationScan: () -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val targetProfile = inspectedProfile ?: myProfile
    val isViewingOther = inspectedProfile != null && inspectedProfile.userId != myProfile?.userId

    if (isViewingOther) {
        BackHandler {
            onClearInspectedProfile()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    // 0 = AI Verify & Games, 1 = Posts & Videos, 2 = Edit Profile, 3 = Notifications, 4 = Admin & Safety
    var showReportUserDialog by remember { mutableStateOf(false) }

    // Verification form states
    var verifyGame by remember(myProfile) { mutableStateOf(myProfile?.primaryGame ?: "BGMI") }
    var sampleIgn by remember(myProfile) {
        mutableStateOf(myProfile?.primaryIgn?.ifBlank { "AX_${myProfile.username.take(10)}" } ?: "AX_Slayer")
    }
    var sampleUid by remember(myProfile) {
        mutableStateOf(myProfile?.primaryGameUid?.ifBlank { "5829104421" } ?: "5829104421")
    }
    var sampleRank by remember(verifyGame) {
        mutableStateOf(if (verifyGame == "FREE_FIRE") "Grandmaster" else "Conqueror")
    }
    var sampleLevel by remember { mutableStateOf("76") }

    // Zero-permission Android Photo Picker for real gallery screenshots
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && myProfile != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        onScanGalleryBitmap(bmp, verifyGame, myProfile.verificationCode)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val userPosts = remember(allPosts, targetProfile) {
        allPosts.filter { it.authorId == targetProfile?.userId }
    }
    val followingSet = remember(myFollows) { myFollows.map { it.followingId }.toSet() }
    val blockedSet = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isViewingOther && targetProfile != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClearInspectedProfile) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = "Player Profile: @${targetProfile.username}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
            }
        }

        // 1. Main Profile Header Card
        if (targetProfile != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.horizontalGradient(listOf(NeonPurple, CyberCyan)),
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                GamerAvatar(
                                    username = targetProfile.username,
                                    isVerified = targetProfile.isVerified,
                                    size = 64.dp
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = targetProfile.displayName.ifBlank { targetProfile.username },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "@${targetProfile.username}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CyberCyan
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (targetProfile.isVerified) {
                                        VerifiedBadgeChip()
                                    } else {
                                        Text(
                                            text = "Status: ${targetProfile.verificationStatus}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TrophyGold
                                        )
                                    }
                                }
                            }

                            if (!isViewingOther) {
                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = "Sign Out",
                                        tint = CrimsonDanger
                                    )
                                }
                            }
                        }

                        if (targetProfile.bio.isNotBlank()) {
                            Text(
                                text = targetProfile.bio,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }

                        // Verified Linked Game Card
                        Surface(
                            color = ArenaElevatedSurface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Column {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        GameBadgeTag(game = targetProfile.primaryGame)
                                        RankBadgePill(rank = targetProfile.primaryRank)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "IGN: ${targetProfile.primaryIgn.ifBlank { "Not Linked" }} • UID: ${targetProfile.primaryGameUid.ifBlank { "Unverified" }}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = "LVL ${targetProfile.primaryLevel}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CyberCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Stats Grid: Elo, W/L, Points, Followers
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProfileStatBox(label = "ELO RATING", value = "${targetProfile.eloRating}", color = TrophyGold)
                            ProfileStatBox(label = "WIN / LOSS", value = "${targetProfile.wins}W-${targetProfile.losses}L", color = VictoryEmerald)
                            ProfileStatBox(label = "POINTS", value = "${targetProfile.arenaPoints}", color = CyberCyan)
                            ProfileStatBox(label = "FOLLOWERS", value = "${targetProfile.followersCount}", color = Color.White)
                        }

                        // Badges Row
                        if (targetProfile.badges.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                targetProfile.badges.forEach { badge ->
                                    Surface(
                                        color = NeonPurple.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = "🏅 $badge",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Action Buttons when viewing another user's profile
                        if (isViewingOther) {
                            val isFollowing = followingSet.contains(targetProfile.userId)
                            val isBlocked = blockedSet.contains(targetProfile.userId)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { onToggleFollow(targetProfile) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (isFollowing) "Unfollow" else "Follow")
                                }
                                OutlinedButton(
                                    onClick = { onMessageUser(targetProfile) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Message")
                                }
                                Button(
                                    onClick = { onChallengeUser(targetProfile) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .testTag("profile_challenge_button")
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Challenge")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onBlockOrUnblockUser(targetProfile.userId, targetProfile.username) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Block, contentDescription = null, tint = CrimsonDanger, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isBlocked) "Unblock" else "Block", color = CrimsonDanger)
                                }
                                OutlinedButton(
                                    onClick = { showReportUserDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Flag, contentDescription = null, tint = TrophyGold, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Report Player", color = TrophyGold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sub-tabs for own profile
        if (!isViewingOther && myProfile != null) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val unreadCount = notifications.count { !it.isRead }
                    listOf(
                        0 to "🛡️ AI Verify",
                        1 to "📸 Posts (${userPosts.size})",
                        2 to "⚙️ Edit Profile",
                        3 to "🔔 Alerts ($unreadCount)",
                        4 to "⚖️ Admin & Safety"
                    ).forEach { (idx, label) ->
                        FilterChip(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    // Gemini Vision Screenshot Verification Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = CyberCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "GEMINI VISION PLAYER VERIFICATION",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    color = NeonPurple.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Anti-Fraud Verification Code: ${myProfile.verificationCode}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CyberCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Include [${myProfile.verificationCode}] in your BGMI or Free Fire in-game signature/bio before taking a screenshot. Gemini 3.8 Flash Vision checks code visibility & extracts your IGN, UID, Level, and Rank.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextSecondarySlate
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("BGMI", "FREE_FIRE").forEach { g ->
                                        FilterChip(
                                            selected = verifyGame == g,
                                            onClick = { verifyGame = g },
                                            label = { Text(if (g == "FREE_FIRE") "Free Fire" else "BGMI") }
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = sampleIgn,
                                        onValueChange = { sampleIgn = it },
                                        label = { Text("Sample IGN") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = sampleUid,
                                        onValueChange = { sampleUid = it },
                                        label = { Text("Sample Game UID") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = sampleRank,
                                        onValueChange = { sampleRank = it },
                                        label = { Text("Sample Rank") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = sampleLevel,
                                        onValueChange = { sampleLevel = it.filter { c -> c.isDigit() }.take(3) },
                                        label = { Text("Level") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.6f)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Upload Screenshot")
                                    }

                                    Button(
                                        onClick = {
                                            onGenerateAndScanSampleScreenshot(
                                                verifyGame,
                                                sampleIgn,
                                                sampleUid,
                                                sampleRank,
                                                sampleLevel.toIntOrNull() ?: 75,
                                                myProfile.verificationCode
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("gemini_scan_sample_button")
                                    ) {
                                        Text("Scan with Gemini AI")
                                    }
                                }

                                // Verification Scan Result State
                                when (verificationScanState) {
                                    is VerificationScanState.Idle -> {}
                                    is VerificationScanState.Scanning -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            CircularProgressIndicator(color = CyberCyan, modifier = Modifier.size(28.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Gemini 3.8 Flash Vision is extracting IGN, UID, Rank & checking ${myProfile.verificationCode}...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = CyberCyan
                                            )
                                        }
                                    }

                                    is VerificationScanState.Extracted -> {
                                        val res = verificationScanState.result
                                        Surface(
                                            color = ArenaElevatedSurface,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Image(
                                                    bitmap = verificationScanState.bitmap.asImageBitmap(),
                                                    contentDescription = "Scanned Profile Screenshot",
                                                    contentScale = ContentScale.FillWidth,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(140.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                                Text(
                                                    text = "EXTRACTED DETAILS (READ-ONLY)",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = TrophyGold
                                                )
                                                Text("• Game: ${res.game}", color = Color.White)
                                                Text("• In-Game Name (IGN): ${res.inGameName}", color = Color.White, fontWeight = FontWeight.Bold)
                                                Text("• Game UID: ${res.gameUid}", color = CyberCyan, fontWeight = FontWeight.Bold)
                                                Text("• Rank / Tier: ${res.rankTier} (Level ${res.level})", color = Color.White)
                                                Text(
                                                    text = "• Code [${myProfile.verificationCode}] Visible: ${if (res.codeVisible) "YES ✅" else "NO ⚠️"} • Confidence: ${res.confidence}%",
                                                    color = if (res.codeVisible && res.confidence >= 75) VictoryEmerald else TrophyGold
                                                )
                                                Text(
                                                    text = res.reasoning,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondarySlate
                                                )

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { onConfirmExtractedVerification(res) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = VictoryEmerald,
                                                            contentColor = ArenaObsidian
                                                        ),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("confirm_verification_button")
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Confirm & Link UID", fontWeight = FontWeight.Bold)
                                                    }
                                                    OutlinedButton(
                                                        onClick = onResetVerificationScan,
                                                        modifier = Modifier.weight(0.8f)
                                                    ) {
                                                        Text("Re-upload")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is VerificationScanState.Confirmed -> {
                                        Surface(
                                            color = VictoryEmerald.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "✅ Verification Completed (${verificationScanState.status})!",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = VictoryEmerald,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }

                                    is VerificationScanState.Error -> {
                                        Surface(
                                            color = CrimsonDanger.copy(alpha = 0.18f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = verificationScanState.message,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = CrimsonDanger
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                OutlinedButton(onClick = onResetVerificationScan) {
                                                    Text("Try Again")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Linked Game Profiles History
                    if (myGameProfiles.isNotEmpty()) {
                        item {
                            Text(
                                text = "LINKED SEASON PROFILES",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyberCyan
                            )
                        }
                        items(myGameProfiles, key = { it.id }) { gp ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Text("${gp.game} • ${gp.inGameName}", style = MaterialTheme.typography.titleSmall, color = Color.White)
                                        Text("UID: ${gp.gameUid} • ${gp.rankTier} (Lv.${gp.level}) • ${gp.season}", style = MaterialTheme.typography.labelSmall, color = TextSecondarySlate)
                                    }
                                    Text(gp.status, style = MaterialTheme.typography.labelLarge, color = VictoryEmerald)
                                }
                            }
                        }
                    }
                }

                1 -> {
                    if (userPosts.isEmpty()) {
                        item {
                            Text("You haven't posted any highlights yet.", color = TextSecondarySlate)
                        }
                    } else {
                        items(userPosts, key = { it.id }) { post ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "${post.postType} • ${post.gameTag}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CyberCyan
                                    )
                                    Text(post.caption, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                    Text(
                                        text = "❤️ ${post.likesCount} Likes • 💬 ${post.commentsCount} Comments",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondarySlate
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        EditProfileCard(
                            myProfile = myProfile,
                            onSave = onSaveProfileSettings
                        )
                    }
                }

                3 -> {
                    if (notifications.isEmpty()) {
                        item {
                            Text("No notifications yet.", color = TextSecondarySlate)
                        }
                    } else {
                        items(notifications, key = { it.id }) { notif ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notif.isRead) ArenaCardSurface else ArenaElevatedSurface
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = CyberCyan)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(notif.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                                            Text(notif.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondarySlate)
                                        }
                                    }
                                    if (!notif.isRead) {
                                        OutlinedButton(onClick = { onMarkNotificationRead(notif.id) }) {
                                            Text("Mark Read", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    item {
                        AdminAndSafetyPanel(
                            myGameProfiles = myGameProfiles,
                            disputedChallenges = disputedChallenges,
                            reports = myReports,
                            blockedUsers = blockedUsers,
                            onUnblockUser = { uid, uname -> onBlockOrUnblockUser(uid, uname) }
                        )
                    }
                }
            }
        }
    }

    if (showReportUserDialog && targetProfile != null) {
        ReportModalDialog(
            targetType = "USER",
            targetId = targetProfile.userId,
            targetLabel = "@${targetProfile.username}",
            onDismiss = { showReportUserDialog = false },
            onSubmitReport = { reason ->
                onReportTarget("USER", targetProfile.userId, reason)
            }
        )
    }
}

@Composable
private fun ProfileStatBox(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondarySlate
        )
    }
}

@Composable
private fun EditProfileCard(
    myProfile: UserProfile,
    onSave: (String, String, String, String, String, String, Boolean) -> Unit
) {
    var username by remember(myProfile) { mutableStateOf(myProfile.username) }
    var displayName by remember(myProfile) { mutableStateOf(myProfile.displayName) }
    var bio by remember(myProfile) { mutableStateOf(myProfile.bio) }
    var primaryGame by remember(myProfile) { mutableStateOf(myProfile.primaryGame) }
    var language by remember(myProfile) { mutableStateOf(myProfile.language) }
    var playTime by remember(myProfile) { mutableStateOf(myProfile.playTime) }
    var isPrivate by remember(myProfile) { mutableStateOf(myProfile.visibility == "private") }

    Card(
        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Edit Gamer Identity & Privacy", style = MaterialTheme.typography.titleMedium, color = Color.White)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Unique @username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio & Roles (IGL / Assaulter / Sniper)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Language") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = playTime,
                    onValueChange = { playTime = it },
                    label = { Text("Active Play Time") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("Private Account", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text("Hide profile from public Explore search", style = MaterialTheme.typography.labelSmall, color = TextSecondarySlate)
                }
                Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
            }
            Button(
                onClick = {
                    onSave(username, displayName, bio, primaryGame, language, playTime, isPrivate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Profile Changes")
            }
        }
    }
}

@Composable
private fun AdminAndSafetyPanel(
    myGameProfiles: List<GameProfile>,
    disputedChallenges: List<Challenge>,
    reports: List<ModerationReport>,
    blockedUsers: List<UserBlock>,
    onUnblockUser: (String, String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = TrophyGold)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MODERATION, DISPUTES & BLOCK LIST",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "• Pending Verification Reviews: ${myGameProfiles.count { it.status == "PENDING_REVIEW" }}",
                color = CyberCyan
            )
            Text(
                text = "• Disputed Challenges in Queue: ${disputedChallenges.size}",
                color = TrophyGold
            )
            disputedChallenges.forEach { d ->
                Surface(
                    color = ArenaElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Disputed: @${d.challengerUsername} (${d.challengerSubmittedScore}) vs @${d.opponentUsername} (${d.opponentSubmittedScore})",
                            style = MaterialTheme.typography.labelLarge,
                            color = CrimsonDanger
                        )
                        Text(
                            text = "Evidence: ${d.challengerProofNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondarySlate
                        )
                    }
                }
            }

            Text(
                text = "• Submitted Moderation Reports: ${reports.size}",
                color = Color.White
            )
            reports.forEach { rep ->
                Surface(
                    color = ArenaElevatedSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${rep.targetType} • Status: ${rep.status}", color = TrophyGold, style = MaterialTheme.typography.labelLarge)
                        Text(rep.reason, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Text(
                text = "• Blocked Players (${blockedUsers.size})",
                color = Color.White
            )
            blockedUsers.forEach { b ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("@${b.blockedUsername}", color = Color.White)
                    OutlinedButton(onClick = { onUnblockUser(b.blockedId, b.blockedUsername) }) {
                        Text("Unblock")
                    }
                }
            }
        }
    }
}
