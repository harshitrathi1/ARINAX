package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ArenaMission
import com.example.data.model.Challenge
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.Follow
import com.example.data.model.UserProfile
import com.example.ui.components.CounterOfferDialog
import com.example.ui.components.GameBadgeTag
import com.example.ui.components.GamerAvatar
import com.example.ui.components.RankBadgePill
import com.example.ui.components.ScoreSubmissionDialog
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
fun ArenaScreen(
    myProfile: UserProfile?,
    myChallenges: List<Challenge>,
    publicChallenges: List<Challenge>,
    publicProfiles: List<UserProfile>,
    myFollows: List<Follow>,
    missions: List<ArenaMission>,
    onChallengeRival: (UserProfile) -> Unit,
    onUpdateChallengeStatus: (
        challenge: Challenge,
        newStatus: String,
        counterTime: String?,
        counterMap: String?,
        counterWeapons: String?,
        counterStake: Int?
    ) -> Unit,
    onSubmitChallengeScore: (
        challenge: Challenge,
        myScore: Int,
        theirScore: Int,
        proofNote: String,
        simulateMatchIfRival: Boolean
    ) -> Unit,
    onInspectProfile: (UserProfile) -> Unit
) {
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0=Active, 1=Leaderboard, 2=History, 3=Missions
    var leaderboardFilter by remember { mutableStateOf("GLOBAL") } // GLOBAL, BGMI, FREE_FIRE, FRIENDS
    var counterOfferTarget by remember { mutableStateOf<Challenge?>(null) }
    var scoreSubmitTarget by remember { mutableStateOf<Challenge?>(null) }

    val activeChallenges = remember(myChallenges) {
        myChallenges.filter {
            it.status in listOf("sent", "countered", "accepted", "scheduled", "in_progress", "result_submitted", "disputed")
        }
    }
    val completedOrPastChallenges = remember(myChallenges, publicChallenges) {
        (myChallenges + publicChallenges)
            .distinctBy { it.id }
            .filter { it.status in listOf("completed", "declined", "expired", "cancelled", "disputed") }
    }
    val followingIds = remember(myFollows) { myFollows.map { it.followingId }.toSet() }

    val rankedPlayers = remember(publicProfiles, leaderboardFilter, followingIds, myProfile) {
        publicProfiles.filter { p ->
            when (leaderboardFilter) {
                "BGMI" -> p.primaryGame == "BGMI"
                "FREE_FIRE" -> p.primaryGame == "FREE_FIRE"
                "FRIENDS" -> p.userId == myProfile?.userId || followingIds.contains(p.userId)
                else -> true
            }
        }.sortedByDescending { it.eloRating }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sub-navigation pills
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    0 to "⚔️ Active (${activeChallenges.size})",
                    1 to "🏆 Leaderboard",
                    2 to "📜 History",
                    3 to "🎯 Missions"
                ).forEach { (idx, title) ->
                    FilterChip(
                        selected = selectedSubTab == idx,
                        onClick = { selectedSubTab = idx },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        when (selectedSubTab) {
            0 -> {
                if (activeChallenges.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(42.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No Active Challenges Right Now",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Pick a verified rival below to launch an instant 1v1 or Squad challenge!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondarySlate
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                val topRival = publicProfiles.firstOrNull { it.userId != myProfile?.userId }
                                if (topRival != null) {
                                    Button(
                                        onClick = { onChallengeRival(topRival) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                                        modifier = Modifier.testTag("arena_quick_challenge_button")
                                    ) {
                                        Text("Challenge @${topRival.username} (${topRival.primaryGame})")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(activeChallenges, key = { it.id }) { challenge ->
                        ChallengeCardItem(
                            challenge = challenge,
                            currentUserId = myProfile?.userId ?: "",
                            onAccept = {
                                onUpdateChallengeStatus(challenge, "accepted", null, null, null, null)
                            },
                            onDecline = {
                                onUpdateChallengeStatus(challenge, "declined", null, null, null, null)
                            },
                            onCounter = { counterOfferTarget = challenge },
                            onStartMatch = {
                                onUpdateChallengeStatus(challenge, "in_progress", null, null, null, null)
                            },
                            onOpenSubmitScore = { scoreSubmitTarget = challenge },
                            onCancel = {
                                onUpdateChallengeStatus(challenge, "cancelled", null, null, null, null)
                            }
                        )
                    }
                }
            }

            1 -> {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "GLOBAL" to "Global S1",
                            "BGMI" to "BGMI",
                            "FREE_FIRE" to "Free Fire",
                            "FRIENDS" to "Friends"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = leaderboardFilter == key,
                                onClick = { leaderboardFilter = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                itemsIndexed(rankedPlayers, key = { _, p -> p.userId }) { index, player ->
                    val rankPos = index + 1
                    val medalColor = when (rankPos) {
                        1 -> TrophyGold
                        2 -> Color(0xFFE2E8F0)
                        3 -> Color(0xFFD97706)
                        else -> CyberCyan
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (rankPos <= 3) medalColor.copy(alpha = 0.6f) else NeonPurple.copy(alpha = 0.25f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onInspectProfile(player) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#$rankPos",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = medalColor,
                                    modifier = Modifier.width(38.dp)
                                )
                                GamerAvatar(username = player.username, isVerified = player.isVerified, size = 44.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "@${player.username}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        GameBadgeTag(game = player.primaryGame)
                                        RankBadgePill(rank = player.primaryRank)
                                    }
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
                                    text = "${player.wins}W • ${player.winStreak}🔥 Streak",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VictoryEmerald
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                if (completedOrPastChallenges.isEmpty()) {
                    item {
                        Text(
                            text = "No completed or past challenges yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondarySlate
                        )
                    }
                } else {
                    items(completedOrPastChallenges, key = { it.id }) { challenge ->
                        ChallengeCardItem(
                            challenge = challenge,
                            currentUserId = myProfile?.userId ?: "",
                            onAccept = {},
                            onDecline = {},
                            onCounter = {},
                            onStartMatch = {},
                            onOpenSubmitScore = {},
                            onCancel = {}
                        )
                    }
                }
            }

            3 -> {
                items(missions, key = { it.id }) { mission ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (mission.isWeekly) "WEEKLY • ${mission.title}" else "DAILY • ${mission.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = mission.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondarySlate
                                    )
                                }
                                Surface(
                                    color = TrophyGold.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "+${mission.rewardPoints} PTS",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TrophyGold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = {
                                    (mission.progress.toFloat() / mission.target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
                                },
                                color = if (mission.progress >= mission.target) VictoryEmerald else CyberCyan,
                                trackColor = ArenaElevatedSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                            Text(
                                text = "${mission.progress} / ${mission.target} Completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mission.progress >= mission.target) VictoryEmerald else TextSecondarySlate
                            )
                        }
                    }
                }
            }
        }
    }

    if (counterOfferTarget != null) {
        CounterOfferDialog(
            challenge = counterOfferTarget!!,
            onDismiss = { counterOfferTarget = null },
            onSubmitCounter = { newTime, newMap, newWeapons, newStake ->
                onUpdateChallengeStatus(
                    counterOfferTarget!!,
                    "countered",
                    newTime,
                    newMap,
                    newWeapons,
                    newStake
                )
                counterOfferTarget = null
            }
        )
    }

    if (scoreSubmitTarget != null) {
        ScoreSubmissionDialog(
            challenge = scoreSubmitTarget!!,
            onDismiss = { scoreSubmitTarget = null },
            onSubmitScore = { myScore, theirScore, proof, simulateMatch ->
                onSubmitChallengeScore(scoreSubmitTarget!!, myScore, theirScore, proof, simulateMatch)
                scoreSubmitTarget = null
            }
        )
    }
}

@Composable
fun ChallengeCardItem(
    challenge: Challenge,
    currentUserId: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCounter: () -> Unit,
    onStartMatch: () -> Unit,
    onOpenSubmitScore: () -> Unit,
    onCancel: () -> Unit
) {
    val isRivalBot = challenge.opponentId.startsWith("arenax_rival_")
    val statusColor = when (challenge.status) {
        "completed", "confirmed" -> VictoryEmerald
        "disputed", "declined", "cancelled", "expired" -> CrimsonDanger
        "in_progress", "accepted" -> CyberCyan
        else -> TrophyGold
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GameBadgeTag(game = challenge.game)
                    Surface(
                        color = NeonPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${challenge.mode} • ${challenge.rounds}R",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = challenge.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Players Vs Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "@${challenge.challengerUsername}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = challenge.challengerIgn,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondarySlate
                    )
                }
                Text(
                    text = if (challenge.finalScoreLabel.isNotBlank()) challenge.finalScoreLabel else "VS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TrophyGold
                )
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "@${challenge.opponentUsername}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = challenge.opponentIgn,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondarySlate
                    )
                }
            }

            // Rules & Stake Summary
            Surface(
                color = ArenaElevatedSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🗺️ ${challenge.mapName} • 🔫 ${challenge.weaponsRule}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text = "⏰ ${challenge.scheduledTimeLabel} • 💰 Stake: ${challenge.stakePoints} Arena Points",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberCyan
                    )
                    if (challenge.customNote.isNotBlank()) {
                        Text(
                            text = "Note: ${challenge.customNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondarySlate
                        )
                    }
                }
            }

            // Status History Trail
            if (challenge.statusHistory.isNotEmpty()) {
                Text(
                    text = "Timeline: " + challenge.statusHistory.joinToString(" → "),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondarySlate
                )
            }

            // Action Buttons depending on current status
            when (challenge.status) {
                "sent", "countered" -> {
                    val canAcceptDirectly = challenge.opponentId == currentUserId || isRivalBot || challenge.status == "countered"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (canAcceptDirectly) {
                            Button(
                                onClick = onAccept,
                                colors = ButtonDefaults.buttonColors(containerColor = VictoryEmerald, contentColor = ArenaObsidian),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("accept_challenge_${challenge.id}")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isRivalBot && challenge.challengerId == currentUserId) "Accept (Rival Ready)" else "Accept")
                            }
                            OutlinedButton(
                                onClick = onCounter,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Counter")
                            }
                            OutlinedButton(
                                onClick = onDecline,
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text("Decline", color = CrimsonDanger)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Sent Challenge", color = CrimsonDanger)
                            }
                        }
                    }
                }

                "accepted", "scheduled" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onStartMatch,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ArenaObsidian),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Room Match", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onOpenSubmitScore,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("submit_score_button_${challenge.id}")
                        ) {
                            Text("Submit Score")
                        }
                    }
                }

                "in_progress", "result_submitted" -> {
                    Button(
                        onClick = onOpenSubmitScore,
                        colors = ButtonDefaults.buttonColors(containerColor = VictoryEmerald, contentColor = ArenaObsidian),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("submit_score_button_${challenge.id}")
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Submit Final Score & Screenshot Proof", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    myProfile: UserProfile?,
    chats: List<ChatThread>,
    activeChatId: String?,
    messages: List<ChatMessage>,
    publicProfiles: List<UserProfile>,
    myChallenges: List<Challenge>,
    onSelectChat: (String?) -> Unit,
    onStartDirectChat: (UserProfile) -> Unit,
    onCreateSquadChat: (String, List<UserProfile>) -> Unit,
    onSendMessage: (String) -> Unit,
    onOpenSwordChallenge: (UserProfile, ChatThread) -> Unit,
    onAcceptChallengeFromChat: (Challenge) -> Unit,
    onDeclineChallengeFromChat: (Challenge) -> Unit
) {
    var messageInput by remember { mutableStateOf("") }
    var showCreateSquadDialog by remember { mutableStateOf(false) }
    val activeChat = remember(chats, activeChatId) {
        chats.firstOrNull { it.id == activeChatId }
    }

    if (activeChat == null) {
        // Chat Threads & Squads List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "1:1 COMMS & SQUAD CHATS",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberCyan
                    )
                    Button(
                        onClick = { showCreateSquadDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ New Squad")
                    }
                }
            }

            if (chats.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Start a 1:1 Direct Chat or Challenge Room",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White
                            )
                            publicProfiles.filter { it.userId != myProfile?.userId }.take(3).forEach { rival ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        GamerAvatar(username = rival.username, isVerified = rival.isVerified, size = 36.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text("@${rival.username}", color = Color.White, style = MaterialTheme.typography.titleSmall)
                                            Text("${rival.primaryGame} • ${rival.primaryRank}", color = TextSecondarySlate, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    OutlinedButton(onClick = { onStartDirectChat(rival) }) {
                                        Text("Message")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(chats, key = { it.id }) { thread ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ArenaCardSurface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, NeonPurple.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .clickable { onSelectChat(thread.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                GamerAvatar(
                                    username = thread.title.removePrefix("@"),
                                    isVerified = true,
                                    size = 44.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = thread.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        if (thread.isSquad) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = CyberCyan.copy(alpha = 0.16f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "SQUAD (${thread.memberUids.size})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CyberCyan,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = thread.lastMessageText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondarySlate,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(Icons.Default.Bolt, contentDescription = "Open Chat", tint = CyberCyan)
                        }
                    }
                }
            }
        }
    } else {
        // Active Chat Conversation View with Sword Challenge Button
        val otherUid = activeChat.memberUids.firstOrNull { it != myProfile?.userId }
        val otherProfile = publicProfiles.firstOrNull { it.userId == otherUid }
            ?: publicProfiles.firstOrNull { it.userId != myProfile?.userId }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Chat Header with Sword Button
            Surface(
                color = ArenaCardSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onSelectChat(null) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Text(
                                text = activeChat.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Real-time Firestore Sync • Read Receipts Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = VictoryEmerald
                            )
                        }
                    }

                    if (otherProfile != null) {
                        Button(
                            onClick = { onOpenSwordChallenge(otherProfile, activeChat) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("chat_sword_challenge_button")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = "Sword Challenge", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⚔️ Challenge", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Messages Stream
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderId == myProfile?.userId
                    val linkedChallenge = myChallenges.firstOrNull { it.id == msg.challengeId }

                    if (msg.messageType == "CHALLENGE_INVITE" || msg.messageType == "CHALLENGE_RESULT") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ArenaElevatedSurface),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, TrophyGold.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = TrophyGold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ARENA CHALLENGE CARD",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TrophyGold
                                    )
                                }
                                Text(text = msg.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                                if (msg.challengeSummary.isNotBlank()) {
                                    Text(
                                        text = msg.challengeSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan
                                    )
                                }
                                if (linkedChallenge != null && linkedChallenge.status == "sent") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { onAcceptChallengeFromChat(linkedChallenge) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = VictoryEmerald,
                                                contentColor = ArenaObsidian
                                            )
                                        ) {
                                            Text("Accept Challenge", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(onClick = { onDeclineChallengeFromChat(linkedChallenge) }) {
                                            Text("Decline", color = CrimsonDanger)
                                        }
                                    }
                                } else if (linkedChallenge != null) {
                                    Text(
                                        text = "Status: ${linkedChallenge.status.uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = VictoryEmerald
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Surface(
                                color = if (isMine) NeonPurple else ArenaCardSurface,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "@${msg.senderUsername}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMine) CyberCyan else TrophyGold
                                    )
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = ArenaCardSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 84.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        placeholder = { Text("Send message or room ID...") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (messageInput.isNotBlank()) {
                                onSendMessage(messageInput)
                                messageInput = ""
                            }
                        },
                        modifier = Modifier.testTag("chat_send_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = CyberCyan)
                    }
                }
            }
        }
    }

    if (showCreateSquadDialog) {
        var squadName by remember { mutableStateOf("Conqueror Drop Squad") }
        val selectedMembers = remember { mutableStateListOf<UserProfile>() }
        AlertDialog(
            onDismissRequest = { showCreateSquadDialog = false },
            containerColor = ArenaCardSurface,
            title = { Text("Create Squad Group Chat", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = squadName,
                        onValueChange = { squadName = it },
                        label = { Text("Squad Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Select Squad Members:", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                    publicProfiles.filter { it.userId != myProfile?.userId }.forEach { p ->
                        val checked = selectedMembers.any { it.userId == p.userId }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedMembers.removeAll { it.userId == p.userId }
                                    else selectedMembers.add(p)
                                }
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (checked) selectedMembers.removeAll { it.userId == p.userId }
                                    else selectedMembers.add(p)
                                }
                            )
                            Text("@${p.username} (${p.primaryGame})", color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateSquadChat(squadName, selectedMembers.toList())
                        showCreateSquadDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Squad")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateSquadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
