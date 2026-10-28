package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.ProfanityFilter
import com.example.core.handleFirestoreError
import com.example.data.model.ArenaNotification
import com.example.data.model.Challenge
import com.example.data.model.ChatMessage
import com.example.data.model.ChatThread
import com.example.data.model.ExtractedPlayerVerification
import com.example.data.model.Follow
import com.example.data.model.GameProfile
import com.example.data.model.ModerationReport
import com.example.data.model.Post
import com.example.data.model.PostComment
import com.example.data.model.UserBlock
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.math.pow
import kotlin.math.roundToLong

class ArenaRepository(private val db: FirebaseFirestore) {

    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- 1. User Profile & Onboarding ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.GET, path)
                    throw error
                }
        )
    }

    fun observePublicProfiles(): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.eloRating }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getOrInitializeUserProfile(
        uid: String,
        defaultName: String
    ): Result<UserProfile> = runCatching {
        val docRef = db.collection("users").document(uid)
        try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val existing = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (existing != null) return@runCatching existing
            }
            val cleanHandle = defaultName.lowercase()
                .replace(Regex("[^a-z0-9_]"), "")
                .take(16)
                .ifBlank { "gamer" } + "_" + uid.take(4).lowercase()
            val randomCode = "AX-${(1000..9999).random()}"
            val payload = mapOf(
                "userId" to uid,
                "username" to cleanHandle,
                "displayName" to defaultName.ifBlank { "Arena Player" }.take(40),
                "bio" to "Competitive mobile gamer on ArenaX",
                "avatarUrl" to "",
                "visibility" to "public",
                "primaryGame" to "BGMI",
                "primaryIgn" to "",
                "primaryGameUid" to "",
                "primaryRank" to "Unranked",
                "primaryLevel" to 1L,
                "isVerified" to false,
                "verificationStatus" to "UNVERIFIED",
                "verificationCode" to randomCode,
                "eloRating" to 1200L,
                "arenaPoints" to 500L,
                "wins" to 0L,
                "losses" to 0L,
                "winStreak" to 0L,
                "followersCount" to 0L,
                "followingCount" to 0L,
                "badges" to listOf("Season 1 Rookie"),
                "language" to "English / Hindi",
                "playTime" to "20:00 - 23:30 IST",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            val createdSnap = docRef.get().await()
            createdSnap.toObject(
                UserProfile::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: UserProfile(id = uid, userId = uid, username = cleanHandle, verificationCode = randomCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updateOnboardingProfile(
        username: String,
        displayName: String,
        bio: String,
        primaryGame: String,
        language: String,
        playTime: String,
        isPrivate: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val sanitizedUsername = username.trim().lowercase().replace(Regex("[^a-z0-9_\\-]"), "")
        require(sanitizedUsername.length in 3..30) { "Username must be 3-30 valid characters" }

        // Check duplicate username across public profiles
        val dupQuery = db.collection("users")
            .whereEqualTo("visibility", "public")
            .whereEqualTo("username", sanitizedUsername)
            .get()
            .await()
        val takenByOther = dupQuery.documents.any { it.id != uid }
        require(!takenByOther) { "Username @$sanitizedUsername is already taken" }

        val docRef = db.collection("users").document(uid)
        try {
            docRef.update(
                mapOf(
                    "username" to sanitizedUsername,
                    "displayName" to ProfanityFilter.sanitize(displayName.ifBlank { sanitizedUsername }).take(50),
                    "bio" to ProfanityFilter.sanitize(bio).take(300),
                    "primaryGame" to if (primaryGame == "FREE_FIRE") "FREE_FIRE" else "BGMI",
                    "language" to language.take(30),
                    "playTime" to playTime.take(40),
                    "visibility" to if (isPrivate) "private" else "public",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- 2. Verified Player Identity (Anti-Fraud + Duplicate UID Check) ---

    suspend fun submitExtractedVerification(
        currentUserProfile: UserProfile,
        extracted: ExtractedPlayerVerification,
        season: String = "S1-2026"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val cleanGameUid = extracted.gameUid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "")
        require(cleanGameUid.length in 3..40) { "Extracted UID '$cleanGameUid' is invalid" }

        val gameKey = if (extracted.game == "FREE_FIRE") "FREE_FIRE" else "BGMI"
        val profileDocId = "${gameKey}_$cleanGameUid"
        val gameProfileRef = db.collection("gameProfiles").document(profileDocId)

        try {
            // Anti-Fraud Check (a): One UID can be linked to only one account
            val existingSnap = try {
                gameProfileRef.get().await()
            } catch (_: Exception) {
                null
            }
            if (existingSnap != null && existingSnap.exists()) {
                val ownerUid = existingSnap.getString("userId") ?: ""
                if (ownerUid.isNotBlank() && ownerUid != uid) {
                    throw IllegalStateException("Anti-Fraud Alert: Game UID $cleanGameUid is already linked to another ArenaX player!")
                }
            }

            // Anti-Fraud Check (b) & (c): Code visibility + Confidence threshold (75%+)
            val isApprovedImmediately = extracted.codeVisible && extracted.confidence >= 75
            val status = if (isApprovedImmediately) "VERIFIED" else "PENDING_REVIEW"

            val gameProfilePayload = mapOf(
                "userId" to uid,
                "username" to currentUserProfile.username.ifBlank { "gamer_${uid.take(4)}" },
                "game" to gameKey,
                "inGameName" to extracted.inGameName.take(50).ifBlank { "UnknownIGN" },
                "gameUid" to cleanGameUid,
                "level" to extracted.level.coerceIn(1, 500).toLong(),
                "rankTier" to extracted.rankTier.take(40).ifBlank { "Gold" },
                "verificationCode" to currentUserProfile.verificationCode.ifBlank { "AX-4821" },
                "codeMatched" to extracted.codeVisible,
                "confidence" to extracted.confidence.coerceIn(0, 100).toLong(),
                "status" to status,
                "season" to season,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            gameProfileRef.set(gameProfilePayload).await()

            // Update UserProfile with verified details and badge
            val updatedBadges = currentUserProfile.badges.toMutableList()
            if (isApprovedImmediately && !updatedBadges.contains("Verified Player")) {
                updatedBadges.add(0, "Verified Player")
            }
            val userRef = db.collection("users").document(uid)
            userRef.update(
                mapOf(
                    "primaryGame" to gameKey,
                    "primaryIgn" to extracted.inGameName.take(50).ifBlank { "UnknownIGN" },
                    "primaryGameUid" to cleanGameUid,
                    "primaryRank" to extracted.rankTier.take(40).ifBlank { "Gold" },
                    "primaryLevel" to extracted.level.coerceIn(1, 500).toLong(),
                    "isVerified" to isApprovedImmediately,
                    "verificationStatus" to status,
                    "badges" to updatedBadges.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            status
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, gameProfileRef.path)
            throw e
        }
    }

    fun observeUserGameProfiles(userId: String): Flow<List<GameProfile>> = flow {
        val path = "gameProfiles"
        emitAll(
            db.collection("gameProfiles")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GameProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    // --- 3. Social Follow / Unfollow ---

    fun observeMyFollows(userId: String): Flow<List<Follow>> = flow {
        val path = "follows"
        emitAll(
            db.collection("follows")
                .whereEqualTo("followerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Follow::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun toggleFollow(
        currentUser: UserProfile,
        targetUser: UserProfile,
        currentlyFollowing: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid != targetUser.userId) { "Cannot follow yourself" }
        val followId = "${uid}_${targetUser.userId}"
        val followRef = db.collection("follows").document(followId)
        val myRef = db.collection("users").document(uid)
        val targetRef = db.collection("users").document(targetUser.userId)

        try {
            if (currentlyFollowing) {
                followRef.delete().await()
                myRef.update(
                    mapOf(
                        "followingCount" to (currentUser.followingCount - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                targetRef.update(
                    mapOf(
                        "followersCount" to (targetUser.followersCount - 1L).coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                followRef.set(
                    mapOf(
                        "followerId" to uid,
                        "followerUsername" to currentUser.username,
                        "followingId" to targetUser.userId,
                        "followingUsername" to targetUser.username,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                myRef.update(
                    mapOf(
                        "followingCount" to (currentUser.followingCount + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                targetRef.update(
                    mapOf(
                        "followersCount" to (targetUser.followersCount + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                sendNotification(
                    recipientId = targetUser.userId,
                    actorUsername = currentUser.username,
                    type = "FOLLOW",
                    title = "New Follower",
                    body = "@${currentUser.username} started following you on ArenaX!",
                    referenceId = uid
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followRef.path)
            throw e
        }
    }

    // --- 4. Feed, Photo Posts, YouTube Video Posts & Challenge Result Cards ---

    fun observePublicPosts(): Flow<List<Post>> = flow {
        val path = "posts"
        emitAll(
            db.collection("posts")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Post::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createPost(
        author: UserProfile,
        postType: String,
        caption: String,
        gameTag: String,
        hashtags: List<String>,
        mediaUrl: String = "",
        youtubeVideoId: String = "",
        youtubeTitle: String = "",
        challengeId: String = ""
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val postId = "post_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val postRef = db.collection("posts").document(postId)
        val sanitizedCaption = ProfanityFilter.sanitize(caption).take(1000).ifBlank { "GG!" }
        val cleanTags = hashtags.map { it.trim().removePrefix("#").take(30) }
            .filter { it.isNotBlank() }
            .take(10)

        try {
            val payload = mapOf(
                "authorId" to uid,
                "authorUsername" to author.username.ifBlank { "gamer" },
                "authorAvatarUrl" to author.avatarUrl,
                "authorVerified" to author.isVerified,
                "authorRank" to author.primaryRank.ifBlank { "Unranked" },
                "postType" to postType,
                "caption" to sanitizedCaption,
                "mediaUrl" to mediaUrl.take(500),
                "youtubeVideoId" to youtubeVideoId.take(30),
                "youtubeTitle" to youtubeTitle.take(200),
                "gameTag" to if (gameTag in listOf("BGMI", "FREE_FIRE", "ALL")) gameTag else "BGMI",
                "hashtags" to cleanTags,
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "likedByUids" to emptyList<String>(),
                "savedByUids" to emptyList<String>(),
                "visibility" to "public",
                "challengeId" to challengeId.take(128),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            postRef.set(payload).await()
            postId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            throw e
        }
    }

    suspend fun toggleLikePost(post: Post, actorUsername: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val postRef = db.collection("posts").document(post.id)
        val alreadyLiked = post.likedByUids.contains(uid)
        val updatedLikedBy = if (alreadyLiked) {
            post.likedByUids - uid
        } else {
            (post.likedByUids + uid).take(50)
        }
        val newLikesCount = if (alreadyLiked) {
            (post.likesCount - 1L).coerceAtLeast(0L)
        } else {
            post.likesCount + 1L
        }
        try {
            postRef.update(
                mapOf(
                    "likedByUids" to updatedLikedBy,
                    "likesCount" to newLikesCount,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (!alreadyLiked && post.authorId != uid) {
                sendNotification(
                    recipientId = post.authorId,
                    actorUsername = actorUsername,
                    type = "LIKE",
                    title = "Post Liked",
                    body = "@$actorUsername liked your ${post.gameTag} highlight!",
                    referenceId = post.id
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }
    }

    suspend fun toggleSavePost(post: Post): Result<Unit> = runCatching {
        val uid = requireUserId()
        val postRef = db.collection("posts").document(post.id)
        val alreadySaved = post.savedByUids.contains(uid)
        val updatedSavedBy = if (alreadySaved) {
            post.savedByUids - uid
        } else {
            (post.savedByUids + uid).take(50)
        }
        try {
            postRef.update(
                mapOf(
                    "savedByUids" to updatedSavedBy,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            throw e
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> = runCatching {
        requireUserId()
        val postRef = db.collection("posts").document(postId)
        try {
            postRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, postRef.path)
            throw e
        }
    }

    fun observeComments(postId: String): Flow<List<PostComment>> = flow {
        val path = "posts/$postId/comments"
        emitAll(
            db.collection("posts").document(postId).collection("comments")
                .whereEqualTo("postId", postId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PostComment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addComment(
        post: Post,
        authorUsername: String,
        text: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val commentId = "cmt_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val commentRef = db.collection("posts").document(post.id).collection("comments").document(commentId)
        val postRef = db.collection("posts").document(post.id)
        val cleanText = ProfanityFilter.sanitize(text).take(500)
        require(cleanText.isNotBlank()) { "Comment cannot be empty" }

        try {
            commentRef.set(
                mapOf(
                    "postId" to post.id,
                    "authorId" to uid,
                    "authorUsername" to authorUsername,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            postRef.update(
                mapOf(
                    "commentsCount" to (post.commentsCount + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (post.authorId != uid) {
                sendNotification(
                    recipientId = post.authorId,
                    actorUsername = authorUsername,
                    type = "COMMENT",
                    title = "New Comment",
                    body = "@$authorUsername commented: \"${cleanText.take(60)}\"",
                    referenceId = post.id
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            throw e
        }
    }

    // --- 5. Challenge System (State Machine, Counter-Offer, Score Match, Elo Settlement) ---

    fun observeMyChallenges(userId: String): Flow<List<Challenge>> = flow {
        val path = "challenges"
        emitAll(
            db.collection("challenges")
                .whereArrayContains("participantUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Challenge::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observePublicChallenges(): Flow<List<Challenge>> = flow {
        val path = "challenges"
        emitAll(
            db.collection("challenges")
                .whereEqualTo("visibility", "public")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Challenge::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createChallenge(
        challenger: UserProfile,
        opponent: UserProfile,
        game: String,
        mode: String,
        mapName: String,
        weaponsRule: String,
        rounds: Int,
        customNote: String,
        scheduledTimeLabel: String,
        stakePoints: Int,
        existingMyChallenges: List<Challenge>,
        blockedUserIds: Set<String>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        require(challenger.isVerified) {
            "Only Verified Players can send challenges! Verify your BGMI/Free Fire screenshot first."
        }
        require(uid != opponent.userId) { "You cannot challenge yourself." }
        require(!blockedUserIds.contains(opponent.userId)) {
            "Cannot challenge a user on your block list."
        }
        require(challenger.arenaPoints >= stakePoints) {
            "Insufficient Arena Points (${challenger.arenaPoints} available, $stakePoints required)."
        }

        // Anti-Abuse: Daily challenge limit (max 10 active/sent challenges) & cooldown on same opponent
        val activeWithSameOpponent = existingMyChallenges.count {
            it.opponentId == opponent.userId && it.status in listOf("sent", "countered", "accepted", "scheduled", "in_progress")
        }
        require(activeWithSameOpponent == 0) {
            "Cooldown active: You already have an active challenge with @${opponent.username}."
        }
        val activeTotal = existingMyChallenges.count {
            it.challengerId == uid && it.status in listOf("sent", "countered", "accepted", "scheduled", "in_progress")
        }
        require(activeTotal < 10) {
            "Daily active challenge limit (10) reached. Finish existing matches first."
        }

        val challengeId = "chal_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val challengeRef = db.collection("challenges").document(challengeId)
        val validGame = if (game == "FREE_FIRE") "FREE_FIRE" else "BGMI"
        val validMode = if (mode in listOf("1v1", "DUO", "SQUAD")) mode else "1v1"

        try {
            val payload = mapOf(
                "challengerId" to uid,
                "challengerUsername" to challenger.username,
                "challengerIgn" to challenger.primaryIgn.ifBlank { challenger.username },
                "challengerRank" to challenger.primaryRank.ifBlank { "Verified" },
                "opponentId" to opponent.userId,
                "opponentUsername" to opponent.username,
                "opponentIgn" to opponent.primaryIgn.ifBlank { opponent.username },
                "opponentRank" to opponent.primaryRank.ifBlank { "Verified" },
                "participantUids" to listOf(uid, opponent.userId),
                "game" to validGame,
                "mode" to validMode,
                "mapName" to mapName.take(50).ifBlank { "Warehouse TDM" },
                "weaponsRule" to weaponsRule.take(100).ifBlank { "M416 Only" },
                "rounds" to rounds.coerceIn(1, 30).toLong(),
                "customNote" to ProfanityFilter.sanitize(customNote).take(300),
                "scheduledTimeLabel" to scheduledTimeLabel.take(80).ifBlank { "Tonight 21:00 IST" },
                "stakePoints" to stakePoints.coerceIn(10, 5000).toLong(),
                "status" to "sent",
                "challengerSubmittedScore" to "",
                "challengerProofNote" to "",
                "opponentSubmittedScore" to "",
                "opponentProofNote" to "",
                "winnerId" to "",
                "winnerUsername" to "",
                "finalScoreLabel" to "",
                "statusHistory" to listOf("sent by @${challenger.username} (${stakePoints} pts)"),
                "visibility" to "public",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            challengeRef.set(payload).await()

            sendNotification(
                recipientId = opponent.userId,
                actorUsername = challenger.username,
                type = "CHALLENGE",
                title = "New $validGame $validMode Challenge!",
                body = "@${challenger.username} challenged you on $mapName for $stakePoints Arena Points!",
                referenceId = challengeId
            )

            challengeId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, challengeRef.path)
            throw e
        }
    }

    suspend fun respondToChallenge(
        challenge: Challenge,
        actorUsername: String,
        newStatus: String,
        counterTimeLabel: String? = null,
        counterMapName: String? = null,
        counterWeaponsRule: String? = null,
        counterStakePoints: Int? = null
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val challengeRef = db.collection("challenges").document(challenge.id)
        val historyEntry = when (newStatus) {
            "accepted" -> "accepted by @$actorUsername"
            "declined" -> "declined by @$actorUsername"
            "countered" -> "countered by @$actorUsername (${counterTimeLabel ?: challenge.scheduledTimeLabel})"
            "in_progress" -> "match started by @$actorUsername"
            "cancelled" -> "cancelled by @$actorUsername"
            else -> "$newStatus by @$actorUsername"
        }.take(120)

        val updatedHistory = (challenge.statusHistory + historyEntry).takeLast(20)
        val updates = mutableMapOf<String, Any>(
            "status" to newStatus,
            "statusHistory" to updatedHistory,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (counterTimeLabel != null) updates["scheduledTimeLabel"] = counterTimeLabel.take(80)
        if (counterMapName != null) updates["mapName"] = counterMapName.take(50)
        if (counterWeaponsRule != null) updates["weaponsRule"] = counterWeaponsRule.take(100)
        if (counterStakePoints != null) updates["stakePoints"] = counterStakePoints.coerceIn(10, 5000).toLong()

        try {
            challengeRef.update(updates).await()
            val otherUid = if (challenge.challengerId == uid) challenge.opponentId else challenge.challengerId
            sendNotification(
                recipientId = otherUid,
                actorUsername = actorUsername,
                type = "CHALLENGE",
                title = "Challenge ${newStatus.uppercase()}",
                body = "@$actorUsername updated your ${challenge.game} ${challenge.mode} challenge to $newStatus.",
                referenceId = challenge.id
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, challengeRef.path)
            throw e
        }
    }

    /**
     * Submits match score ("challengerScore-opponentScore", e.g. "2-1") and proof screenshot note.
     * If both sides' scores match (or against an official ArenaX showcase rival), auto-confirms,
     * settles Elo ratings + Arena Points + streaks, and posts an automatic Challenge Result Card to the feed!
     * If scores conflict, marks the challenge `disputed` and adds it to the Admin Moderation Queue.
     */
    suspend fun submitChallengeResult(
        challenge: Challenge,
        actorProfile: UserProfile,
        myScore: Int,
        theirScore: Int,
        proofNote: String,
        simulateMatchingOpponentIfRival: Boolean = true
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val isChallenger = challenge.challengerId == uid
        val normalizedScore = if (isChallenger) {
            "${myScore.coerceIn(0, 99)}-${theirScore.coerceIn(0, 99)}"
        } else {
            "${theirScore.coerceIn(0, 99)}-${myScore.coerceIn(0, 99)}"
        }
        require(myScore != theirScore) { "Competitive ArenaX matches must have a winner (no ties)." }

        val challengeRef = db.collection("challenges").document(challenge.id)
        val isRivalBot = challenge.opponentId.startsWith("arenax_rival_")
        val otherScoreAlready = if (isChallenger) {
            challenge.opponentSubmittedScore
        } else {
            challenge.challengerSubmittedScore
        }

        val effectiveOtherScore = when {
            otherScoreAlready.isNotBlank() -> otherScoreAlready
            isRivalBot && simulateMatchingOpponentIfRival -> normalizedScore
            else -> ""
        }

        try {
            if (effectiveOtherScore.isBlank()) {
                // First player submitted; waiting on opponent
                val history = (challenge.statusHistory +
                    "result $normalizedScore submitted by @${actorProfile.username}").takeLast(20)
                val updates = mutableMapOf<String, Any>(
                    "status" to "result_submitted",
                    "statusHistory" to history,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isChallenger) {
                    updates["challengerSubmittedScore"] = normalizedScore
                    updates["challengerProofNote"] = proofNote.take(300)
                } else {
                    updates["opponentSubmittedScore"] = normalizedScore
                    updates["opponentProofNote"] = proofNote.take(300)
                }
                challengeRef.update(updates).await()
                return@runCatching "result_submitted"
            }

            if (effectiveOtherScore == normalizedScore) {
                // Both sides match -> Auto-confirm & Complete!
                val parts = normalizedScore.split("-")
                val cScore = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val oScore = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val challengerWon = cScore > oScore
                val winnerId = if (challengerWon) challenge.challengerId else challenge.opponentId
                val winnerUsername = if (challengerWon) challenge.challengerUsername else challenge.opponentUsername
                val loserUsername = if (challengerWon) challenge.opponentUsername else challenge.challengerUsername
                val finalScoreDisplay = if (challengerWon) "$cScore - $oScore" else "$oScore - $cScore"

                val history = (challenge.statusHistory +
                    listOf(
                        "confirmed score $normalizedScore",
                        "completed: @$winnerUsername won $finalScoreDisplay"
                    )).takeLast(20)

                challengeRef.update(
                    mapOf(
                        "challengerSubmittedScore" to normalizedScore,
                        "opponentSubmittedScore" to normalizedScore,
                        "challengerProofNote" to if (isChallenger) proofNote.take(300) else challenge.challengerProofNote,
                        "opponentProofNote" to if (!isChallenger) proofNote.take(300) else challenge.opponentProofNote.ifBlank { "Auto-verified proof" },
                        "status" to "completed",
                        "winnerId" to winnerId,
                        "winnerUsername" to winnerUsername,
                        "finalScoreLabel" to finalScoreDisplay,
                        "statusHistory" to history,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                // Settle Elo ratings, Arena Points, Win/Loss & Streaks on both profiles
                settleEloAndStats(
                    challengerId = challenge.challengerId,
                    opponentId = challenge.opponentId,
                    challengerWon = challengerWon,
                    stakePoints = challenge.stakePoints
                )

                // Automatically post a Challenge Result Card to the Feed!
                createPost(
                    author = actorProfile,
                    postType = "CHALLENGE_RESULT",
                    caption = "⚔️ ARENA RESULT: @$winnerUsername defeated @$loserUsername ($finalScoreDisplay) in ${challenge.game} ${challenge.mode} on ${challenge.mapName}! +${challenge.stakePoints} Arena Points & Elo boost!",
                    gameTag = challenge.game,
                    hashtags = listOf("ArenaX", challenge.game, "1v1Result", winnerUsername),
                    challengeId = challenge.id
                )

                return@runCatching "completed"
            } else {
                // Scores differ -> Mark DISPUTED and add to Admin Moderation Queue!
                val history = (challenge.statusHistory +
                    "disputed: ${challenge.challengerUsername}($normalizedScore) vs ($effectiveOtherScore)").takeLast(20)

                val updates = mutableMapOf<String, Any>(
                    "status" to "disputed",
                    "statusHistory" to history,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (isChallenger) {
                    updates["challengerSubmittedScore"] = normalizedScore
                    updates["challengerProofNote"] = proofNote.take(300)
                } else {
                    updates["opponentSubmittedScore"] = normalizedScore
                    updates["opponentProofNote"] = proofNote.take(300)
                }
                challengeRef.update(updates).await()

                reportTarget(
                    reporterUsername = actorProfile.username,
                    targetType = "DISPUTED_CHALLENGE",
                    targetId = challenge.id,
                    reason = "Score mismatch in ${challenge.game} ${challenge.mode}: $normalizedScore vs $effectiveOtherScore. Evidence: $proofNote"
                )
                return@runCatching "disputed"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, challengeRef.path)
            throw e
        }
    }

    private suspend fun settleEloAndStats(
        challengerId: String,
        opponentId: String,
        challengerWon: Boolean,
        stakePoints: Long
    ) {
        val cRef = db.collection("users").document(challengerId)
        val oRef = db.collection("users").document(opponentId)
        try {
            val cSnap = cRef.get().await()
            val oSnap = oRef.get().await()
            val cProfile = cSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val oProfile = oSnap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            if (cProfile != null && oProfile != null) {
                val expectedC = 1.0 / (1.0 + 10.0.pow((oProfile.eloRating - cProfile.eloRating) / 400.0))
                val kFactor = 32.0
                val actualC = if (challengerWon) 1.0 else 0.0
                val deltaC = (kFactor * (actualC - expectedC)).roundToLong()

                val newCElo = (cProfile.eloRating + deltaC).coerceIn(100L, 9999L)
                val newOElo = (oProfile.eloRating - deltaC).coerceIn(100L, 9999L)

                val cNewWins = if (challengerWon) cProfile.wins + 1L else cProfile.wins
                val cNewLosses = if (challengerWon) cProfile.losses else cProfile.losses + 1L
                val cNewStreak = if (challengerWon) cProfile.winStreak + 1L else 0L
                val cNewPoints = if (challengerWon) {
                    cProfile.arenaPoints + stakePoints
                } else {
                    (cProfile.arenaPoints - stakePoints).coerceAtLeast(0L)
                }

                val oNewWins = if (!challengerWon) oProfile.wins + 1L else oProfile.wins
                val oNewLosses = if (!challengerWon) oProfile.losses else oProfile.losses + 1L
                val oNewStreak = if (!challengerWon) oProfile.winStreak + 1L else 0L
                val oNewPoints = if (!challengerWon) {
                    oProfile.arenaPoints + stakePoints
                } else {
                    (oProfile.arenaPoints - stakePoints).coerceAtLeast(0L)
                }

                cRef.update(
                    mapOf(
                        "eloRating" to newCElo,
                        "arenaPoints" to cNewPoints,
                        "wins" to cNewWins,
                        "losses" to cNewLosses,
                        "winStreak" to cNewStreak,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                oRef.update(
                    mapOf(
                        "eloRating" to newOElo,
                        "arenaPoints" to oNewPoints,
                        "wins" to oNewWins,
                        "losses" to oNewLosses,
                        "winStreak" to oNewStreak,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$challengerId")
        }
    }

    // --- 6. Real-Time 1:1 & Squad Chat ---

    fun observeMyChats(userId: String): Flow<List<ChatThread>> = flow {
        val path = "chats"
        emitAll(
            db.collection("chats")
                .whereArrayContains("memberUids", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatThread::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun getOrCreateDirectChat(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val sortedUids = listOf(uid, otherUser.userId).sorted()
        val chatId = "dm_${sortedUids[0].take(20)}_${sortedUids[1].take(20)}"
        val chatRef = db.collection("chats").document(chatId)

        try {
            val existing = try {
                chatRef.get().await()
            } catch (_: Exception) {
                null
            }
            if (existing != null && existing.exists()) {
                return@runCatching chatId
            }
            chatRef.set(
                mapOf(
                    "title" to "@${otherUser.username}",
                    "isSquad" to false,
                    "memberUids" to listOf(uid, otherUser.userId),
                    "memberNames" to listOf(currentUser.username, otherUser.username),
                    "lastMessageText" to "Direct chat ready — tap Sword to Challenge!",
                    "lastMessageSenderId" to uid,
                    "typingUserId" to "",
                    "activeChallengeId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            chatId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, chatRef.path)
            throw e
        }
    }

    suspend fun createSquadChat(
        creator: UserProfile,
        squadTitle: String,
        invitedMembers: List<UserProfile>
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val chatId = "squad_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val chatRef = db.collection("chats").document(chatId)
        val memberUids = (listOf(uid) + invitedMembers.map { it.userId }).distinct().take(10)
        val memberNames = (listOf(creator.username) + invitedMembers.map { it.username }).distinct().take(10)

        try {
            chatRef.set(
                mapOf(
                    "title" to ProfanityFilter.sanitize(squadTitle).take(80).ifBlank { "Squad Comms" },
                    "isSquad" to true,
                    "memberUids" to memberUids,
                    "memberNames" to memberNames,
                    "lastMessageText" to "Squad created by @${creator.username}",
                    "lastMessageSenderId" to uid,
                    "typingUserId" to "",
                    "activeChallengeId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            chatId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, chatRef.path)
            throw e
        }
    }

    fun observeMessages(chatId: String, currentUserId: String): Flow<List<ChatMessage>> = flow {
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun sendChatMessage(
        chat: ChatThread,
        senderUsername: String,
        text: String,
        messageType: String = "TEXT",
        challengeId: String = "",
        challengeSummary: String = ""
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messageId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val msgRef = db.collection("chats").document(chat.id).collection("messages").document(messageId)
        val chatRef = db.collection("chats").document(chat.id)
        val cleanText = ProfanityFilter.sanitize(text).take(1000)
        require(cleanText.isNotBlank()) { "Message cannot be empty" }

        try {
            msgRef.set(
                mapOf(
                    "chatId" to chat.id,
                    "senderId" to uid,
                    "senderUsername" to senderUsername,
                    "memberUids" to chat.memberUids,
                    "messageType" to messageType,
                    "text" to cleanText,
                    "challengeId" to challengeId.take(128),
                    "challengeSummary" to challengeSummary.take(300),
                    "readByUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val chatUpdates = mutableMapOf<String, Any>(
                "lastMessageText" to cleanText.take(200),
                "lastMessageSenderId" to uid,
                "typingUserId" to "",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (challengeId.isNotBlank()) {
                chatUpdates["activeChallengeId"] = challengeId
            }
            chatRef.update(chatUpdates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            throw e
        }
    }

    // --- 7. Notifications, Reports & Blocks ---

    fun observeNotifications(userId: String): Flow<List<ArenaNotification>> = flow {
        val path = "notifications"
        emitAll(
            db.collection("notifications")
                .whereEqualTo("recipientId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ArenaNotification::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun sendNotification(
        recipientId: String,
        actorUsername: String,
        type: String,
        title: String,
        body: String,
        referenceId: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val notifId = "ntf_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val notifRef = db.collection("notifications").document(notifId)
        try {
            notifRef.set(
                mapOf(
                    "recipientId" to recipientId,
                    "actorId" to uid,
                    "actorUsername" to actorUsername.ifBlank { "gamer" },
                    "type" to type,
                    "title" to title.take(100),
                    "body" to body.take(300),
                    "referenceId" to referenceId.take(128),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, notifRef.path)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("notifications").document(notificationId)
        try {
            ref.update("isRead", true).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    fun observeBlockedUsers(userId: String): Flow<List<UserBlock>> = flow {
        val path = "blocks"
        emitAll(
            db.collection("blocks")
                .whereEqualTo("blockerId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserBlock::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun blockUser(blockedId: String, blockedUsername: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(uid != blockedId) { "Cannot block yourself" }
        val blockId = "${uid}_$blockedId"
        val ref = db.collection("blocks").document(blockId)
        try {
            ref.set(
                mapOf(
                    "blockerId" to uid,
                    "blockedId" to blockedId,
                    "blockedUsername" to blockedUsername.take(30).ifBlank { "gamer" },
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun unblockUser(blockedId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("blocks").document("${uid}_$blockedId")
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    suspend fun reportTarget(
        reporterUsername: String,
        targetType: String,
        targetId: String,
        reason: String
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val reportId = "rep_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("reports").document(reportId)
        try {
            ref.set(
                mapOf(
                    "reporterId" to uid,
                    "reporterUsername" to reporterUsername.take(30).ifBlank { "gamer" },
                    "targetType" to targetType,
                    "targetId" to targetId.take(128),
                    "reason" to ProfanityFilter.sanitize(reason).take(500).ifBlank { "Policy violation" },
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            reportId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    fun observeMyReports(userId: String): Flow<List<ModerationReport>> = flow {
        val path = "reports"
        emitAll(
            db.collection("reports")
                .whereEqualTo("reporterId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ModerationReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    // --- 8. Starter Pro Rivals & Showcase Feed Seeding (Real Firestore Documents) ---

    suspend fun seedStarterArenaEcosystemIfNeeded() {
        auth.currentUser?.uid ?: return
        try {
            val rival1Id = "arenax_rival_mortal_bgmi"
            val existing = db.collection("users").document(rival1Id).get().await()
            if (existing.exists()) return

            val rivals = listOf(
                mapOf(
                    "userId" to "arenax_rival_mortal_bgmi",
                    "username" to "soul_viper_ax",
                    "displayName" to "Viper IGL • BGMI",
                    "bio" to "BGMI Conqueror S1 • M416 + Bolt Action specialist • Open for 1v1 TDM Warehouse stakes!",
                    "avatarUrl" to "",
                    "visibility" to "public",
                    "primaryGame" to "BGMI",
                    "primaryIgn" to "SOULxViper",
                    "primaryGameUid" to "5589012341",
                    "primaryRank" to "Conqueror",
                    "primaryLevel" to 84L,
                    "isVerified" to true,
                    "verificationStatus" to "VERIFIED",
                    "verificationCode" to "AX-9012",
                    "eloRating" to 1640L,
                    "arenaPoints" to 2400L,
                    "wins" to 42L,
                    "losses" to 8L,
                    "winStreak" to 7L,
                    "followersCount" to 1280L,
                    "followingCount" to 64L,
                    "badges" to listOf("Verified Player", "BGMI Conqueror", "7-Win Streak"),
                    "language" to "Hindi / English",
                    "playTime" to "21:00 - 01:00 IST",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to "arenax_rival_pahadi_ff",
                    "username" to "ff_phantom_99",
                    "displayName" to "Phantom • Free Fire",
                    "bio" to "Free Fire Grandmaster • One-Tap Desert Eagle & MP40 Clash Squad king.",
                    "avatarUrl" to "",
                    "visibility" to "public",
                    "primaryGame" to "FREE_FIRE",
                    "primaryIgn" to "FF_Phantom99",
                    "primaryGameUid" to "294810552",
                    "primaryRank" to "Grandmaster",
                    "primaryLevel" to 79L,
                    "isVerified" to true,
                    "verificationStatus" to "VERIFIED",
                    "verificationCode" to "AX-7731",
                    "eloRating" to 1585L,
                    "arenaPoints" to 1950L,
                    "wins" to 36L,
                    "losses" to 11L,
                    "winStreak" to 4L,
                    "followersCount" to 940L,
                    "followingCount" to 52L,
                    "badges" to listOf("Verified Player", "FF Grandmaster"),
                    "language" to "Hindi / English",
                    "playTime" to "19:00 - 23:00 IST",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to "arenax_rival_jonathan_bgmi",
                    "username" to "jiggle_god_ax",
                    "displayName" to "JiggleGod • Assaulter",
                    "bio" to "2 Finger + Full Gyro 400 sens • Accepting all 1v1 M416 Warehouse challenges.",
                    "avatarUrl" to "",
                    "visibility" to "public",
                    "primaryGame" to "BGMI",
                    "primaryIgn" to "AXxJiggleGod",
                    "primaryGameUid" to "5192837465",
                    "primaryRank" to "Ace Dominator",
                    "primaryLevel" to 76L,
                    "isVerified" to true,
                    "verificationStatus" to "VERIFIED",
                    "verificationCode" to "AX-3318",
                    "eloRating" to 1510L,
                    "arenaPoints" to 1500L,
                    "wins" to 29L,
                    "losses" to 9L,
                    "winStreak" to 5L,
                    "followersCount" to 710L,
                    "followingCount" to 40L,
                    "badges" to listOf("Verified Player", "TDM Specialist"),
                    "language" to "English",
                    "playTime" to "20:00 - 00:00 IST",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                mapOf(
                    "userId" to "arenax_rival_raistar_ff",
                    "username" to "blazex_one_tap",
                    "displayName" to "BlazeX • Clash Squad",
                    "bio" to "Custom Room 1v1 Gloowall + Woodpecker • Fast movement & clean headshots.",
                    "avatarUrl" to "",
                    "visibility" to "public",
                    "primaryGame" to "FREE_FIRE",
                    "primaryIgn" to "BlazeX_FF",
                    "primaryGameUid" to "884120934",
                    "primaryRank" to "Heroic Elite",
                    "primaryLevel" to 71L,
                    "isVerified" to true,
                    "verificationStatus" to "VERIFIED",
                    "verificationCode" to "AX-6104",
                    "eloRating" to 1445L,
                    "arenaPoints" to 1200L,
                    "wins" to 21L,
                    "losses" to 10L,
                    "winStreak" to 2L,
                    "followersCount" to 530L,
                    "followingCount" to 38L,
                    "badges" to listOf("Verified Player", "Headshot Master"),
                    "language" to "Hindi / Tamil / English",
                    "playTime" to "18:00 - 22:00 IST",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            for (rival in rivals) {
                val id = rival["userId"] as String
                db.collection("users").document(id).set(rival).await()
            }

            // Seed starter Feed Posts (1 YouTube video highlight, 1 Challenge Result card, 1 Photo clutch post)
            val starterPosts = listOf(
                "post_seed_yt_1" to mapOf(
                    "authorId" to "arenax_rival_mortal_bgmi",
                    "authorUsername" to "soul_viper_ax",
                    "authorAvatarUrl" to "",
                    "authorVerified" to true,
                    "authorRank" to "Conqueror",
                    "postType" to "YOUTUBE",
                    "caption" to "1v4 Final Circle Erangel Clutch at 1640 Elo! Drop a challenge from my profile if you want a Warehouse TDM 1v1.",
                    "mediaUrl" to "",
                    "youtubeVideoId" to "dQw4w9WgXcQ",
                    "youtubeTitle" to "BGMI Conqueror 1v4 Final Zone Clutch & Gyro Handcam",
                    "gameTag" to "BGMI",
                    "hashtags" to listOf("BGMI", "Conqueror", "1v4Clutch", "ArenaX"),
                    "likesCount" to 48L,
                    "commentsCount" to 6L,
                    "likedByUids" to emptyList<String>(),
                    "savedByUids" to emptyList<String>(),
                    "visibility" to "public",
                    "challengeId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "post_seed_result_2" to mapOf(
                    "authorId" to "arenax_rival_pahadi_ff",
                    "authorUsername" to "ff_phantom_99",
                    "authorAvatarUrl" to "",
                    "authorVerified" to true,
                    "authorRank" to "Grandmaster",
                    "postType" to "CHALLENGE_RESULT",
                    "caption" to "⚔️ ARENA RESULT: @ff_phantom_99 beat @blazex_one_tap (7 - 4) in FREE_FIRE 1v1 Clash Squad Custom Room! +250 Arena Points & +18 Elo!",
                    "mediaUrl" to "",
                    "youtubeVideoId" to "",
                    "youtubeTitle" to "",
                    "gameTag" to "FREE_FIRE",
                    "hashtags" to listOf("FreeFire", "ClashSquad", "1v1Result", "ArenaX"),
                    "likesCount" to 34L,
                    "commentsCount" to 4L,
                    "likedByUids" to emptyList<String>(),
                    "savedByUids" to emptyList<String>(),
                    "visibility" to "public",
                    "challengeId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                "post_seed_photo_3" to mapOf(
                    "authorId" to "arenax_rival_jonathan_bgmi",
                    "authorUsername" to "jiggle_god_ax",
                    "authorAvatarUrl" to "",
                    "authorVerified" to true,
                    "authorRank" to "Ace Dominator",
                    "postType" to "PHOTO",
                    "caption" to "New Season 5-finger Claw + 400% Gyro sensitivity layout locked in. 24-3 K/D in Warehouse TDM today!",
                    "mediaUrl" to "preset_tdm_stats",
                    "youtubeVideoId" to "",
                    "youtubeTitle" to "",
                    "gameTag" to "BGMI",
                    "hashtags" to listOf("BGMI", "TDM", "Sensitivity", "Gyro"),
                    "likesCount" to 29L,
                    "commentsCount" to 3L,
                    "likedByUids" to emptyList<String>(),
                    "savedByUids" to emptyList<String>(),
                    "visibility" to "public",
                    "challengeId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            for ((id, postData) in starterPosts) {
                db.collection("posts").document(id).set(postData).await()
            }
        } catch (_: Exception) {
            // Ignore seed errors if offline or already seeded
        }
    }
}
