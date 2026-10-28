package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ArenaMission
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
import com.example.data.repository.ArenaRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "ArenaViewModel"
private const val STOP_TIMEOUT_MS = 5000L

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface VerificationScanState {
    data object Idle : VerificationScanState
    data object Scanning : VerificationScanState
    data class Extracted(
        val bitmap: Bitmap,
        val result: ExtractedPlayerVerification
    ) : VerificationScanState
    data class Confirmed(val status: String) : VerificationScanState
    data class Error(val message: String) : VerificationScanState
}

class ArenaViewModel(
    private val repository: ArenaRepository,
    val currentUserId: String,
    val currentUserEmail: String,
    val currentUserDisplayName: String
) : ViewModel() {

    // Structured JSON Schema for Gemini Vision Screenshot Verification
    private val verificationSchema = Schema.obj(
        properties = mapOf(
            "game" to Schema.enumeration(
                values = listOf("BGMI", "FREE_FIRE"),
                description = "Detected mobile game from screenshot"
            ),
            "inGameName" to Schema.string(description = "Extracted player In-Game Name (IGN)"),
            "gameUid" to Schema.string(description = "Extracted player numeric or alphanumeric UID"),
            "level" to Schema.integer(description = "Extracted player level (1-100)"),
            "rankTier" to Schema.string(description = "Extracted rank or tier e.g. Conqueror, Ace Dominator, Grandmaster, Heroic"),
            "codeVisible" to Schema.boolean(description = "True if the expected ArenaX verification code (e.g. AX-1234) is visible in the screenshot bio/signature/name"),
            "confidence" to Schema.integer(description = "Confidence score from 0 to 100"),
            "reasoning" to Schema.string(description = "Brief explanation of extracted fields and verification code visibility")
        )
    )

    // Lazy initialization of Gemini 3.8 Flash multimodal model via Firebase AI Logic
    private val visionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = verificationSchema
            }
        )
    }

    // Two-Tier Kotlin Flow Error Handling Architecture for all Firestore streams
    val myProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing user profile", e)
            emit(UiState.Error(e.message ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val publicProfilesState: StateFlow<UiState<List<UserProfile>>> = repository.observePublicProfiles()
        .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing public profiles", e)
            emit(UiState.Error(e.message ?: "Failed to load players"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myGameProfilesState: StateFlow<UiState<List<GameProfile>>> = repository.observeUserGameProfiles(currentUserId)
        .map<List<GameProfile>, UiState<List<GameProfile>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing game profiles", e)
            emit(UiState.Error(e.message ?: "Failed to load linked games"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myFollowsState: StateFlow<UiState<List<Follow>>> = repository.observeMyFollows(currentUserId)
        .map<List<Follow>, UiState<List<Follow>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing follows", e)
            emit(UiState.Error(e.message ?: "Failed to load follows"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val publicPostsState: StateFlow<UiState<List<Post>>> = repository.observePublicPosts()
        .map<List<Post>, UiState<List<Post>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing posts", e)
            emit(UiState.Error(e.message ?: "Failed to load feed"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myChallengesState: StateFlow<UiState<List<Challenge>>> = repository.observeMyChallenges(currentUserId)
        .map<List<Challenge>, UiState<List<Challenge>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing my challenges", e)
            emit(UiState.Error(e.message ?: "Failed to load challenges"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val publicChallengesState: StateFlow<UiState<List<Challenge>>> = repository.observePublicChallenges()
        .map<List<Challenge>, UiState<List<Challenge>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing public challenges", e)
            emit(UiState.Error(e.message ?: "Failed to load public arena"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myChatsState: StateFlow<UiState<List<ChatThread>>> = repository.observeMyChats(currentUserId)
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing chats", e)
            emit(UiState.Error(e.message ?: "Failed to load chats"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<ArenaNotification>>> = repository.observeNotifications(currentUserId)
        .map<List<ArenaNotification>, UiState<List<ArenaNotification>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing notifications", e)
            emit(UiState.Error(e.message ?: "Failed to load notifications"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val blockedUsersState: StateFlow<UiState<List<UserBlock>>> = repository.observeBlockedUsers(currentUserId)
        .map<List<UserBlock>, UiState<List<UserBlock>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing blocked users", e)
            emit(UiState.Error(e.message ?: "Failed to load block list"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val myReportsState: StateFlow<UiState<List<ModerationReport>>> = repository.observeMyReports(currentUserId)
        .map<List<ModerationReport>, UiState<List<ModerationReport>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing reports", e)
            emit(UiState.Error(e.message ?: "Failed to load moderation queue"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    // Active Chat Room Selection & Messages Stream
    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeChatMessagesState: StateFlow<UiState<List<ChatMessage>>> = _selectedChatId
        .flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) {
                emptyFlow()
            } else {
                repository.observeMessages(chatId, currentUserId)
            }
        }
        .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing chat messages", e)
            emit(UiState.Error(e.message ?: "Failed to load messages"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Success(emptyList()))

    // Active Post Comments Selection & Stream
    private val _selectedPostIdForComments = MutableStateFlow<String?>(null)
    val selectedPostIdForComments: StateFlow<String?> = _selectedPostIdForComments.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activePostCommentsState: StateFlow<UiState<List<PostComment>>> = _selectedPostIdForComments
        .flatMapLatest { postId ->
            if (postId.isNullOrBlank()) {
                emptyFlow()
            } else {
                repository.observeComments(postId)
            }
        }
        .map<List<PostComment>, UiState<List<PostComment>>> { UiState.Success(it) }
        .catch { e ->
            Log.w(TAG, "Error observing comments", e)
            emit(UiState.Error(e.message ?: "Failed to load comments"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Success(emptyList()))

    // Verification Scan State
    private val _verificationScanState = MutableStateFlow<VerificationScanState>(VerificationScanState.Idle)
    val verificationScanState: StateFlow<VerificationScanState> = _verificationScanState.asStateFlow()

    // Status Banner / Toast Message
    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Selected Profile to inspect
    private val _inspectedUserProfile = MutableStateFlow<UserProfile?>(null)
    val inspectedUserProfile: StateFlow<UserProfile?> = _inspectedUserProfile.asStateFlow()

    // Pre-filled Challenge Opponent Sheet State
    private val _challengeSheetOpponent = MutableStateFlow<UserProfile?>(null)
    val challengeSheetOpponent: StateFlow<UserProfile?> = _challengeSheetOpponent.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getOrInitializeUserProfile(currentUserId, currentUserDisplayName)
            repository.seedStarterArenaEcosystemIfNeeded()
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
    }

    fun selectUserProfileToInspect(profile: UserProfile?) {
        _inspectedUserProfile.value = profile
    }

    fun openChallengeSheet(opponent: UserProfile) {
        _challengeSheetOpponent.value = opponent
    }

    fun closeChallengeSheet() {
        _challengeSheetOpponent.value = null
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun selectPostForComments(postId: String?) {
        _selectedPostIdForComments.value = postId
    }

    // --- Profile & Onboarding Actions ---

    fun saveProfileDetails(
        username: String,
        displayName: String,
        bio: String,
        primaryGame: String,
        language: String,
        playTime: String,
        isPrivate: Boolean
    ) {
        viewModelScope.launch {
            val res = repository.updateOnboardingProfile(
                username = username,
                displayName = displayName,
                bio = bio,
                primaryGame = primaryGame,
                language = language,
                playTime = playTime,
                isPrivate = isPrivate
            )
            res.onSuccess {
                _statusBanner.value = "Profile updated (@$username)"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to update profile"
            }
        }
    }

    // --- Gemini Vision Screenshot Extraction ---

    fun resetVerificationScan() {
        _verificationScanState.value = VerificationScanState.Idle
    }

    /**
     * Renders a crisp synthetic in-game profile screenshot Bitmap containing the user's
     * anti-fraud verificationCode so the user can test real Gemini Vision multimodal OCR
     * on an emulator even without a local screenshot file.
     */
    fun generateSampleScreenshotAndVerifyWithGemini(
        selectedGame: String,
        sampleIgn: String,
        sampleUid: String,
        sampleRank: String,
        sampleLevel: Int,
        verificationCode: String
    ) {
        val bitmap = renderInGameScreenshotBitmap(
            game = selectedGame,
            ign = sampleIgn,
            uid = sampleUid,
            rank = sampleRank,
            level = sampleLevel,
            code = verificationCode
        )
        analyzeScreenshotWithGeminiVision(
            bitmap = bitmap,
            selectedGame = selectedGame,
            expectedCode = verificationCode
        )
    }

    private fun renderInGameScreenshotBitmap(
        game: String,
        ign: String,
        uid: String,
        rank: String,
        level: Int,
        code: String
    ): Bitmap {
        val width = 960
        val height = 540
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(Color.parseColor("#0D0B18"), Color.parseColor("#1E153A"), Color.parseColor("#092230")),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#241E38")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(48f, 48f, 912f, 492f), 28f, 28f, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F0FF")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(RectF(48f, 48f, 912f, 492f), 28f, 28f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00F0FF")
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 30f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBF24")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText("$game OFFICIAL CAREER PROFILE", 88f, 118f, titlePaint)
        canvas.drawText("IGN: $ign", 88f, 195f, whitePaint)
        canvas.drawText("UID: $uid", 88f, 255f, labelPaint)
        canvas.drawText("LEVEL: $level", 88f, 315f, labelPaint)
        canvas.drawText("CURRENT TIER / RANK: $rank", 88f, 375f, badgePaint)
        canvas.drawText("PLAYER BIO / SIGNATURE: ArenaX Verified [$code]", 88f, 442f, titlePaint)

        return bmp
    }

    fun analyzeScreenshotWithGeminiVision(
        bitmap: Bitmap,
        selectedGame: String,
        expectedCode: String
    ) {
        viewModelScope.launch {
            _verificationScanState.value = VerificationScanState.Scanning
            try {
                val prompt = """
                    You are an esports anti-fraud verification AI for ArenaX.
                    Inspect this in-game player profile screenshot for $selectedGame (BGMI or FREE_FIRE).
                    Extract:
                    1. game: "BGMI" or "FREE_FIRE"
                    2. inGameName: The player's in-game name (IGN)
                    3. gameUid: The player's numeric/alphanumeric UID
                    4. level: The player's integer account level
                    5. rankTier: The player's current rank/tier (e.g., Conqueror, Ace Dominator, Grandmaster, Heroic, Diamond)
                    6. codeVisible: Check strictly whether the required verification code "$expectedCode" is visible anywhere in the screenshot (bio, signature, or name).
                    7. confidence: Your confidence score (0-100) in the extraction accuracy.
                    8. reasoning: Short summary of what was detected.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    visionModel.generateContent(
                        content {
                            image(bitmap)
                            text(prompt)
                        }
                    )
                }

                val jsonString = response.text
                if (jsonString.isNullOrBlank()) {
                    _verificationScanState.value = VerificationScanState.Error("Gemini Vision returned an empty response.")
                    return@launch
                }

                val json = JSONObject(jsonString)
                val extracted = ExtractedPlayerVerification(
                    game = json.optString("game", selectedGame),
                    inGameName = json.optString("inGameName", ""),
                    gameUid = json.optString("gameUid", ""),
                    level = json.optInt("level", 1),
                    rankTier = json.optString("rankTier", "Unranked"),
                    codeVisible = json.optBoolean("codeVisible", false),
                    confidence = json.optInt("confidence", 0),
                    reasoning = json.optString("reasoning", "")
                )

                _verificationScanState.value = VerificationScanState.Extracted(
                    bitmap = bitmap,
                    result = extracted
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini Vision verification timed out", e)
                _verificationScanState.value = VerificationScanState.Error(
                    "Screenshot verification timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision verification failed", e)
                _verificationScanState.value = VerificationScanState.Error(
                    e.localizedMessage ?: "Failed to verify screenshot with Gemini Vision."
                )
            }
        }
    }

    fun confirmExtractedVerification(extracted: ExtractedPlayerVerification) {
        val currentProfile = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            val res = repository.submitExtractedVerification(
                currentUserProfile = currentProfile,
                extracted = extracted
            )
            res.onSuccess { status ->
                _verificationScanState.value = VerificationScanState.Confirmed(status)
                _statusBanner.value = if (status == "VERIFIED") {
                    "Verified Player Badge Unlocked! You can now send Arena Challenges."
                } else {
                    "Submitted to Admin Review Queue (Code mismatch or confidence < 75%)."
                }
            }.onFailure { err ->
                _verificationScanState.value = VerificationScanState.Error(
                    err.localizedMessage ?: "Verification submission failed."
                )
            }
        }
    }

    // --- Follow / Unfollow ---

    fun toggleFollow(targetUser: UserProfile) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val follows = (myFollowsState.value as? UiState.Success)?.data.orEmpty()
        val isFollowing = follows.any { it.followingId == targetUser.userId }
        viewModelScope.launch {
            repository.toggleFollow(me, targetUser, isFollowing)
                .onSuccess {
                    _statusBanner.value = if (isFollowing) {
                        "Unfollowed @${targetUser.username}"
                    } else {
                        "Following @${targetUser.username}!"
                    }
                }
                .onFailure { e ->
                    _statusBanner.value = e.localizedMessage ?: "Follow action failed"
                }
        }
    }

    // --- Feed Posts & YouTube Clips ---

    fun createPhotoPost(caption: String, gameTag: String, hashtagsRaw: String, presetMediaKey: String) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val tags = hashtagsRaw.split(",", " ").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            repository.createPost(
                author = me,
                postType = "PHOTO",
                caption = caption,
                gameTag = gameTag,
                hashtags = tags,
                mediaUrl = presetMediaKey.ifBlank { "preset_clutch_highlight" }
            ).onSuccess {
                _statusBanner.value = "Photo highlight posted to ArenaX Feed!"
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to create post"
            }
        }
    }

    fun createYouTubeVideoPost(
        youtubeUrl: String,
        youtubeVideoId: String,
        youtubeTitle: String,
        caption: String,
        gameTag: String,
        hashtagsRaw: String
    ) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val tags = hashtagsRaw.split(",", " ").map { it.trim() }.filter { it.isNotBlank() }
        viewModelScope.launch {
            repository.createPost(
                author = me,
                postType = "YOUTUBE",
                caption = caption,
                gameTag = gameTag,
                hashtags = tags,
                mediaUrl = youtubeUrl,
                youtubeVideoId = youtubeVideoId,
                youtubeTitle = youtubeTitle.ifBlank { "$gameTag Gameplay Highlight" }
            ).onSuccess {
                _statusBanner.value = "YouTube Video embedded in ArenaX Feed!"
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to post YouTube video"
            }
        }
    }

    fun toggleLikePost(post: Post) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.toggleLikePost(post, me.username)
        }
    }

    fun toggleSavePost(post: Post) {
        viewModelScope.launch {
            repository.toggleSavePost(post)
                .onSuccess { _statusBanner.value = "Saved post updated" }
        }
    }

    fun deleteOwnPost(post: Post) {
        viewModelScope.launch {
            repository.deletePost(post.id)
                .onSuccess { _statusBanner.value = "Post deleted" }
        }
    }

    fun addCommentToPost(post: Post, text: String) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.addComment(post, me.username, text)
                .onFailure { e -> _statusBanner.value = e.localizedMessage ?: "Comment failed" }
        }
    }

    // --- Challenge System ---

    fun sendChallenge(
        opponent: UserProfile,
        game: String,
        mode: String,
        mapName: String,
        weaponsRule: String,
        rounds: Int,
        customNote: String,
        scheduledTimeLabel: String,
        stakePoints: Int,
        alsoPostToChat: ChatThread? = null
    ) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val myChallenges = (myChallengesState.value as? UiState.Success)?.data.orEmpty()
        val blockedIds = (blockedUsersState.value as? UiState.Success)?.data.orEmpty().map { it.blockedId }.toSet()

        viewModelScope.launch {
            repository.createChallenge(
                challenger = me,
                opponent = opponent,
                game = game,
                mode = mode,
                mapName = mapName,
                weaponsRule = weaponsRule,
                rounds = rounds,
                customNote = customNote,
                scheduledTimeLabel = scheduledTimeLabel,
                stakePoints = stakePoints,
                existingMyChallenges = myChallenges,
                blockedUserIds = blockedIds
            ).onSuccess { challengeId ->
                _challengeSheetOpponent.value = null
                _statusBanner.value = "Challenge sent to @${opponent.username} for $stakePoints pts!"
                if (alsoPostToChat != null) {
                    repository.sendChatMessage(
                        chat = alsoPostToChat,
                        senderUsername = me.username,
                        text = "⚔️ Challenged @${opponent.username} to $game $mode ($mapName • $weaponsRule) for $stakePoints Arena Points!",
                        messageType = "CHALLENGE_INVITE",
                        challengeId = challengeId,
                        challengeSummary = "$game $mode • $mapName • $weaponsRule • $stakePoints pts • $scheduledTimeLabel"
                    )
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not create challenge"
            }
        }
    }

    fun updateChallengeStatus(
        challenge: Challenge,
        newStatus: String,
        counterTimeLabel: String? = null,
        counterMapName: String? = null,
        counterWeaponsRule: String? = null,
        counterStakePoints: Int? = null
    ) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.respondToChallenge(
                challenge = challenge,
                actorUsername = me.username,
                newStatus = newStatus,
                counterTimeLabel = counterTimeLabel,
                counterMapName = counterMapName,
                counterWeaponsRule = counterWeaponsRule,
                counterStakePoints = counterStakePoints
            ).onSuccess {
                _statusBanner.value = "Challenge status -> ${newStatus.uppercase()}"
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to update challenge"
            }
        }
    }

    fun submitChallengeScore(
        challenge: Challenge,
        myScore: Int,
        theirScore: Int,
        proofNote: String,
        simulateMatchingOpponentIfRival: Boolean
    ) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.submitChallengeResult(
                challenge = challenge,
                actorProfile = me,
                myScore = myScore,
                theirScore = theirScore,
                proofNote = proofNote,
                simulateMatchingOpponentIfRival = simulateMatchingOpponentIfRival
            ).onSuccess { finalStatus ->
                _statusBanner.value = when (finalStatus) {
                    "completed" -> "Match Confirmed! Elo Rating, Arena Points & Feed Card updated!"
                    "disputed" -> "Score mismatch! Marked DISPUTED and sent to Admin Queue."
                    else -> "Score submitted! Waiting for opponent confirmation."
                }
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to submit score"
            }
        }
    }

    // --- Chat System ---

    fun startOrOpenDirectChat(otherUser: UserProfile, onReady: () -> Unit = {}) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.getOrCreateDirectChat(me, otherUser)
                .onSuccess { chatId ->
                    _selectedChatId.value = chatId
                    onReady()
                }
                .onFailure { e ->
                    _statusBanner.value = e.localizedMessage ?: "Could not open chat"
                }
        }
    }

    fun createSquadChat(squadTitle: String, selectedMembers: List<UserProfile>) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.createSquadChat(me, squadTitle, selectedMembers)
                .onSuccess { chatId ->
                    _selectedChatId.value = chatId
                    _statusBanner.value = "Squad '$squadTitle' created!"
                }
                .onFailure { e ->
                    _statusBanner.value = e.localizedMessage ?: "Could not create squad chat"
                }
        }
    }

    fun sendMessageInActiveChat(text: String) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        val chatId = _selectedChatId.value ?: return
        val chat = (myChatsState.value as? UiState.Success)?.data?.firstOrNull { it.id == chatId } ?: return
        viewModelScope.launch {
            repository.sendChatMessage(
                chat = chat,
                senderUsername = me.username,
                text = text
            ).onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Message failed"
            }
        }
    }

    // --- Notifications, Blocks & Reports ---

    fun markNotificationAsRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }

    fun blockOrUnblockUser(targetUserId: String, targetUsername: String) {
        val blocked = (blockedUsersState.value as? UiState.Success)?.data.orEmpty()
        val isBlocked = blocked.any { it.blockedId == targetUserId }
        viewModelScope.launch {
            if (isBlocked) {
                repository.unblockUser(targetUserId)
                    .onSuccess { _statusBanner.value = "Unblocked @$targetUsername" }
            } else {
                repository.blockUser(targetUserId, targetUsername)
                    .onSuccess { _statusBanner.value = "Blocked @$targetUsername" }
            }
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String) {
        val me = (myProfileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            repository.reportTarget(
                reporterUsername = me.username,
                targetType = targetType,
                targetId = targetId,
                reason = reason
            ).onSuccess {
                _statusBanner.value = "Report submitted to ArenaX Moderation Queue."
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Could not submit report"
            }
        }
    }

    // Daily & Weekly Missions computed from live user activity
    fun getMissions(profile: UserProfile?, challenges: List<Challenge>, posts: List<Post>): List<ArenaMission> {
        val verifiedCount = if (profile?.isVerified == true) 1 else 0
        val completedChallenges = challenges.count { it.status == "completed" }
        val myPostsCount = posts.count { it.authorId == currentUserId }
        val winStreak = (profile?.winStreak ?: 0L).toInt()

        return listOf(
            ArenaMission(
                id = "m_verify",
                title = "Verify Game Identity",
                subtitle = "Scan your BGMI or Free Fire profile with Gemini Vision",
                rewardPoints = 250,
                progress = verifiedCount,
                target = 1,
                isWeekly = false
            ),
            ArenaMission(
                id = "m_daily_duel",
                title = "Daily Arena Duelist",
                subtitle = "Complete 1 confirmed 1v1 or Squad challenge",
                rewardPoints = 150,
                progress = completedChallenges.coerceAtMost(1),
                target = 1,
                isWeekly = false
            ),
            ArenaMission(
                id = "m_post_clip",
                title = "Highlight Creator",
                subtitle = "Share a Photo or YouTube gameplay highlight to the feed",
                rewardPoints = 100,
                progress = myPostsCount.coerceAtMost(1),
                target = 1,
                isWeekly = false
            ),
            ArenaMission(
                id = "m_weekly_streak",
                title = "Weekly Win Streak (3x)",
                subtitle = "Maintain a 3-match win streak in ranked Arena challenges",
                rewardPoints = 500,
                progress = winStreak.coerceAtMost(3),
                target = 3,
                isWeekly = true
            )
        )
    }
}
