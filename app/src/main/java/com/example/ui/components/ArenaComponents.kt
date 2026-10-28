package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.YouTubeParser
import com.example.data.model.Challenge
import com.example.data.model.UserProfile
import com.example.ui.theme.ArenaCardSurface
import com.example.ui.theme.ArenaElevatedSurface
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.CrimsonDanger
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextSecondarySlate
import com.example.ui.theme.TrophyGold
import com.example.ui.theme.VictoryEmerald
import kotlin.math.roundToInt

@Composable
fun GamerAvatar(
    username: String,
    isVerified: Boolean,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val initials = username.trim().removePrefix("@").take(2).uppercase().ifBlank { "AX" }
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(NeonPurple, CyberCyan)
                    )
                )
                .padding(2.dp)
                .clip(CircleShape)
                .background(ArenaElevatedSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        if (isVerified) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified Player",
                tint = CyberCyan,
                modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(ArenaObsidian)
            )
        }
    }
}

@Composable
fun GameBadgeTag(game: String, modifier: Modifier = Modifier) {
    val isFreeFire = game == "FREE_FIRE"
    val color = if (isFreeFire) TrophyGold else CyberCyan
    val label = if (isFreeFire) "FREE FIRE" else "BGMI"
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun RankBadgePill(rank: String, modifier: Modifier = Modifier) {
    Surface(
        color = NeonPurple.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
    ) {
        Text(
            text = rank.ifBlank { "Unranked" },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun VerifiedBadgeChip(modifier: Modifier = Modifier) {
    Surface(
        color = VictoryEmerald.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50),
        modifier = modifier.border(1.dp, VictoryEmerald.copy(alpha = 0.6f), RoundedCornerShape(50))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified Player",
                tint = VictoryEmerald,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "VERIFIED PLAYER",
                style = MaterialTheme.typography.labelSmall,
                color = VictoryEmerald,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChallengeCreationDialog(
    opponent: UserProfile,
    myProfile: UserProfile?,
    onDismiss: () -> Unit,
    onSubmitChallenge: (
        game: String,
        mode: String,
        mapName: String,
        weaponsRule: String,
        rounds: Int,
        customNote: String,
        scheduledTimeLabel: String,
        stakePoints: Int
    ) -> Unit
) {
    var selectedGame by remember { mutableStateOf(opponent.primaryGame.ifBlank { "BGMI" }) }
    var selectedMode by remember { mutableStateOf("1v1") }
    val bgmiMaps = listOf("Warehouse TDM", "Ruins TDM", "Erangel Pochinki", "SanHook Bootcamp")
    val ffMaps = listOf("Bermuda Clash Squad", "Iron Cage 1v1", "Purgatory Custom", "Kalahari CS")
    val currentMaps = if (selectedGame == "FREE_FIRE") ffMaps else bgmiMaps

    var selectedMap by remember(selectedGame) { mutableStateOf(currentMaps.first()) }
    var weaponsRule by remember(selectedGame) {
        mutableStateOf(if (selectedGame == "FREE_FIRE") "Desert Eagle + MP40 Only" else "M416 + Kar98k Only")
    }
    var rounds by remember { mutableIntStateOf(if (selectedGame == "FREE_FIRE") 7 else 1) }
    var scheduledTime by remember { mutableStateOf("Tonight 21:30 IST") }
    var stakeSlider by remember { mutableFloatStateOf(100f) }
    var customNote by remember { mutableStateOf("Room ID & pass shared 5 mins before start. No grenades/launchers.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCardSurface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CHALLENGE @${opponent.username}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondarySlate)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (myProfile?.isVerified != true) {
                    Surface(
                        color = CrimsonDanger.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ You must verify your BGMI or Free Fire screenshot in your Profile tab before sending challenges.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonDanger,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Text("Select Game", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BGMI", "FREE_FIRE").forEach { g ->
                        FilterChip(
                            selected = selectedGame == g,
                            onClick = { selectedGame = g },
                            label = { Text(if (g == "FREE_FIRE") "Free Fire" else "BGMI") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Text("Mode", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1v1", "DUO", "SQUAD").forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.25f),
                                selectedLabelColor = CyberCyan
                            )
                        )
                    }
                }

                Text("Arena Map", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    currentMaps.forEach { map ->
                        FilterChip(
                            selected = selectedMap == map,
                            onClick = { selectedMap = map },
                            label = { Text(map, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = weaponsRule,
                    onValueChange = { weaponsRule = it },
                    label = { Text("Weapons & Loadout Rule") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Rounds:", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    listOf(1, 3, 5, 7, 13).forEach { r ->
                        FilterChip(
                            selected = rounds == r,
                            onClick = { rounds = r },
                            label = { Text("${r}R") }
                        )
                    }
                }

                OutlinedTextField(
                    value = scheduledTime,
                    onValueChange = { scheduledTime = it },
                    label = { Text("Scheduled Match Time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Stake (Arena Points Only — No Real Money)",
                            style = MaterialTheme.typography.labelLarge,
                            color = TrophyGold
                        )
                        Text(
                            text = "${stakeSlider.roundToInt()} PTS",
                            style = MaterialTheme.typography.titleSmall,
                            color = TrophyGold,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = stakeSlider,
                        onValueChange = { stakeSlider = it },
                        valueRange = 50f..500f,
                        steps = 8
                    )
                }

                OutlinedTextField(
                    value = customNote,
                    onValueChange = { customNote = it },
                    label = { Text("Custom Room Rules / Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitChallenge(
                        selectedGame,
                        selectedMode,
                        selectedMap,
                        weaponsRule,
                        rounds,
                        customNote,
                        scheduledTime,
                        stakeSlider.roundToInt()
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                modifier = Modifier.testTag("confirm_send_challenge_button")
            ) {
                Text("Send Challenge (${stakeSlider.roundToInt()} Pts)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CounterOfferDialog(
    challenge: Challenge,
    onDismiss: () -> Unit,
    onSubmitCounter: (newTime: String, newMap: String, newWeapons: String, newStake: Int) -> Unit
) {
    var timeLabel by remember { mutableStateOf(challenge.scheduledTimeLabel) }
    var mapName by remember { mutableStateOf(challenge.mapName) }
    var weaponsRule by remember { mutableStateOf(challenge.weaponsRule) }
    var stake by remember { mutableFloatStateOf(challenge.stakePoints.toFloat().coerceIn(50f, 500f)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCardSurface,
        title = {
            Text("Propose Counter-Offer", style = MaterialTheme.typography.titleLarge, color = CyberCyan)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = timeLabel,
                    onValueChange = { timeLabel = it },
                    label = { Text("Proposed Match Time") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mapName,
                    onValueChange = { mapName = it },
                    label = { Text("Proposed Map") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = weaponsRule,
                    onValueChange = { weaponsRule = it },
                    label = { Text("Proposed Weapons Rule") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Proposed Stake: ${stake.roundToInt()} Arena Points", color = TrophyGold)
                Slider(
                    value = stake,
                    onValueChange = { stake = it },
                    valueRange = 50f..500f,
                    steps = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitCounter(timeLabel, mapName, weaponsRule, stake.roundToInt())
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Text("Send Counter-Offer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ScoreSubmissionDialog(
    challenge: Challenge,
    onDismiss: () -> Unit,
    onSubmitScore: (myScore: Int, theirScore: Int, proofNote: String, simulateMatchIfRival: Boolean) -> Unit
) {
    var myScoreStr by remember { mutableStateOf("2") }
    var theirScoreStr by remember { mutableStateOf("1") }
    var proofNote by remember { mutableStateOf("Final scoreboard screenshot uploaded • Room ID #88412 verified") }
    var simulateMatchingRival by remember { mutableStateOf(true) }
    val isRival = challenge.opponentId.startsWith("arenax_rival_")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCardSurface,
        title = {
            Text("Submit Match Result & Proof", style = MaterialTheme.typography.titleLarge, color = VictoryEmerald)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${challenge.game} ${challenge.mode} • @${challenge.challengerUsername} vs @${challenge.opponentUsername}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = myScoreStr,
                        onValueChange = { myScoreStr = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Your Score") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = theirScoreStr,
                        onValueChange = { theirScoreStr = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("Opponent Score") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = proofNote,
                    onValueChange = { proofNote = it },
                    label = { Text("Result Screenshot / Room Log Evidence") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isRival) {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (simulateMatchingRival) "Rival Confirms Same Score (Auto-Complete)" else "Simulate Score Conflict (Test Dispute Queue)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (simulateMatchingRival) VictoryEmerald else TrophyGold
                                )
                                Text(
                                    text = "Toggle off to test disputed score moderation queue",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondarySlate
                                )
                            }
                            Switch(
                                checked = simulateMatchingRival,
                                onCheckedChange = { simulateMatchingRival = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val myS = myScoreStr.toIntOrNull() ?: 2
                    val theirS = theirScoreStr.toIntOrNull() ?: 1
                    onSubmitScore(myS, theirS, proofNote, simulateMatchingRival)
                },
                colors = ButtonDefaults.buttonColors(containerColor = VictoryEmerald, contentColor = ArenaObsidian),
                modifier = Modifier.testTag("confirm_submit_score_button")
            ) {
                Text("Submit Score & Verify", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onCreatePhotoPost: (caption: String, gameTag: String, hashtags: String, presetMediaKey: String) -> Unit,
    onCreateYouTubePost: (url: String, videoId: String, title: String, caption: String, gameTag: String, hashtags: String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Photo Highlight, 1 = YouTube Video
    var gameTag by remember { mutableStateOf("BGMI") }
    var caption by remember { mutableStateOf("") }
    var hashtags by remember { mutableStateOf("#ArenaX #BGMI #Clutch") }
    var presetMedia by remember { mutableStateOf("preset_clutch_highlight") }

    var youtubeUrl by remember { mutableStateOf("https://youtu.be/dQw4w9WgXcQ") }
    var youtubeTitle by remember { mutableStateOf("Conqueror 1v4 Final Zone Highlight") }
    val parsedVideoId = remember(youtubeUrl) { YouTubeParser.extractVideoId(youtubeUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCardSurface,
        title = {
            Text("Create ArenaX Post", style = MaterialTheme.typography.titleLarge, color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("📸 Photo Post") }
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("▶️ YouTube Video") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BGMI", "FREE_FIRE").forEach { g ->
                        FilterChip(
                            selected = gameTag == g,
                            onClick = { gameTag = g },
                            label = { Text(if (g == "FREE_FIRE") "Free Fire" else "BGMI") }
                        )
                    }
                }

                if (selectedTab == 0) {
                    Text(
                        text = "Select Highlight Card Theme (Compressed & Optimized)",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberCyan
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "preset_clutch_highlight" to "🔥 1v4 Squad Wipe Scoreboard",
                            "preset_tdm_stats" to "🎯 24-3 Warehouse TDM MVP Card",
                            "preset_conqueror_tier" to "🏆 Season Conqueror Tier Promotion"
                        ).forEach { (key, label) ->
                            Surface(
                                color = if (presetMedia == key) NeonPurple.copy(alpha = 0.25f) else ArenaElevatedSurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (presetMedia == key) NeonPurple else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { presetMedia = key }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (presetMedia == key) CyberCyan else TextSecondarySlate,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = youtubeUrl,
                        onValueChange = { youtubeUrl = it },
                        label = { Text("Paste YouTube / youtu.be / Shorts Link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (parsedVideoId != null) {
                        Surface(
                            color = VictoryEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = VictoryEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Valid YouTube Video ID: $parsedVideoId",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = VictoryEmerald
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Enter a valid YouTube URL (watch?v=, youtu.be/, or shorts/)",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonDanger
                        )
                    }
                    OutlinedTextField(
                        value = youtubeTitle,
                        onValueChange = { youtubeTitle = it },
                        label = { Text("Video Highlight Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    placeholder = { Text("Drop your clutch story or challenge callout...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = hashtags,
                    onValueChange = { hashtags = it },
                    label = { Text("Hashtags") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onCreatePhotoPost(
                            caption.ifBlank { "Locked in for Season 1 ArenaX!" },
                            gameTag,
                            hashtags,
                            presetMedia
                        )
                        onDismiss()
                    } else if (parsedVideoId != null) {
                        onCreateYouTubePost(
                            youtubeUrl,
                            parsedVideoId,
                            youtubeTitle,
                            caption.ifBlank { youtubeTitle },
                            gameTag,
                            hashtags
                        )
                        onDismiss()
                    }
                },
                enabled = selectedTab == 0 || parsedVideoId != null,
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                modifier = Modifier.testTag("publish_post_button")
            ) {
                Text("Publish to Feed")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReportModalDialog(
    targetType: String,
    targetId: String,
    targetLabel: String,
    onDismiss: () -> Unit,
    onSubmitReport: (reason: String) -> Unit
) {
    var selectedReason by remember { mutableStateOf("Fake screenshot / UID spoofing") }
    var customDetails by remember { mutableStateOf("") }
    val reasons = listOf(
        "Fake screenshot / UID spoofing",
        "Cheating / Wallhack / Aimbot",
        "Abusive chat or harassment",
        "Fake match score submission"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ArenaCardSurface,
        title = {
            Text("Report $targetType ($targetLabel)", style = MaterialTheme.typography.titleLarge, color = CrimsonDanger)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { r ->
                    FilterChip(
                        selected = selectedReason == r,
                        onClick = { selectedReason = r },
                        label = { Text(r) }
                    )
                }
                OutlinedTextField(
                    value = customDetails,
                    onValueChange = { customDetails = it },
                    label = { Text("Additional Evidence / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitReport("$selectedReason. $customDetails".trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
