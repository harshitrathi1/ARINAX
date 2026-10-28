package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val visibility: String = "public",
    val primaryGame: String = "BGMI",
    val primaryIgn: String = "",
    val primaryGameUid: String = "",
    val primaryRank: String = "Unranked",
    val primaryLevel: Long = 1L,
    @get:PropertyName("isVerified") @set:PropertyName("isVerified")
    var isVerified: Boolean = false,
    val verificationStatus: String = "UNVERIFIED",
    val verificationCode: String = "AX-4821",
    val eloRating: Long = 1200L,
    val arenaPoints: Long = 500L,
    val wins: Long = 0L,
    val losses: Long = 0L,
    val winStreak: Long = 0L,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val badges: List<String> = emptyList(),
    val language: String = "English / Hindi",
    val playTime: String = "20:00 - 23:30 IST",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GameProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val game: String = "BGMI",
    val inGameName: String = "",
    val gameUid: String = "",
    val level: Long = 1L,
    val rankTier: String = "",
    val verificationCode: String = "",
    val codeMatched: Boolean = false,
    val confidence: Long = 0L,
    val status: String = "PENDING_REVIEW",
    val season: String = "S1-2026",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Follow(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val followerUsername: String = "",
    val followingId: String = "",
    val followingUsername: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Post(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorAvatarUrl: String = "",
    val authorVerified: Boolean = false,
    val authorRank: String = "",
    val postType: String = "PHOTO",
    val caption: String = "",
    val mediaUrl: String = "",
    val youtubeVideoId: String = "",
    val youtubeTitle: String = "",
    val gameTag: String = "BGMI",
    val hashtags: List<String> = emptyList(),
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val likedByUids: List<String> = emptyList(),
    val savedByUids: List<String> = emptyList(),
    val visibility: String = "public",
    val challengeId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PostComment(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Challenge(
    @DocumentId val id: String = "",
    val challengerId: String = "",
    val challengerUsername: String = "",
    val challengerIgn: String = "",
    val challengerRank: String = "",
    val opponentId: String = "",
    val opponentUsername: String = "",
    val opponentIgn: String = "",
    val opponentRank: String = "",
    val participantUids: List<String> = emptyList(),
    val game: String = "BGMI",
    val mode: String = "1v1",
    val mapName: String = "Warehouse TDM",
    val weaponsRule: String = "M416 Only",
    val rounds: Long = 1L,
    val customNote: String = "",
    val scheduledTimeLabel: String = "Tonight 21:00 IST",
    val stakePoints: Long = 100L,
    val status: String = "sent",
    val challengerSubmittedScore: String = "",
    val challengerProofNote: String = "",
    val opponentSubmittedScore: String = "",
    val opponentProofNote: String = "",
    val winnerId: String = "",
    val winnerUsername: String = "",
    val finalScoreLabel: String = "",
    val statusHistory: List<String> = emptyList(),
    val visibility: String = "public",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val title: String = "",
    @get:PropertyName("isSquad") @set:PropertyName("isSquad")
    var isSquad: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val memberNames: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val typingUserId: String = "",
    val activeChallengeId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = "TEXT",
    val text: String = "",
    val challengeId: String = "",
    val challengeSummary: String = "",
    val readByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ArenaNotification(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val actorUsername: String = "",
    val type: String = "SYSTEM",
    val title: String = "",
    val body: String = "",
    val referenceId: String = "",
    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ModerationReport(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val reporterUsername: String = "",
    val targetType: String = "USER",
    val targetId: String = "",
    val reason: String = "",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserBlock(
    @DocumentId val id: String = "",
    val blockerId: String = "",
    val blockedId: String = "",
    val blockedUsername: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

data class ExtractedPlayerVerification(
    val game: String,
    val inGameName: String,
    val gameUid: String,
    val level: Int,
    val rankTier: String,
    val codeVisible: Boolean,
    val confidence: Int,
    val reasoning: String
)

data class ArenaMission(
    val id: String,
    val title: String,
    val subtitle: String,
    val rewardPoints: Int,
    val progress: Int,
    val target: Int,
    val isWeekly: Boolean = false
)
