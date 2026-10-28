package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.configureAppCheck
import com.example.data.model.ChatThread
import com.example.data.repository.ArenaRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutArenaUser
import com.example.ui.components.ChallengeCreationDialog
import com.example.ui.components.CreatePostDialog
import com.example.ui.screens.ArenaScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.HomeFeedScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.ArenaCardSurface
import com.example.ui.theme.ArenaObsidian
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.TextSecondarySlate
import com.example.ui.theme.TrophyGold
import com.example.ui.viewmodel.ArenaViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration before UI, ViewModels, or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        ArenaMainShell(
            currentUserId = user.uid,
            currentUserEmail = user.email.orEmpty(),
            currentUserDisplayName = user.displayName ?: "ArenaX Fighter"
        )
    }
}

@Composable
fun ArenaMainShell(
    currentUserId: String,
    currentUserEmail: String,
    currentUserDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: ArenaViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ArenaViewModel(
                    repository = ArenaRepository(db),
                    currentUserId = currentUserId,
                    currentUserEmail = currentUserEmail,
                    currentUserDisplayName = currentUserDisplayName
                )
            }
        }
    )

    val myProfileState by viewModel.myProfileState.collectAsStateWithLifecycle()
    val publicProfilesState by viewModel.publicProfilesState.collectAsStateWithLifecycle()
    val myGameProfilesState by viewModel.myGameProfilesState.collectAsStateWithLifecycle()
    val myFollowsState by viewModel.myFollowsState.collectAsStateWithLifecycle()
    val publicPostsState by viewModel.publicPostsState.collectAsStateWithLifecycle()
    val myChallengesState by viewModel.myChallengesState.collectAsStateWithLifecycle()
    val publicChallengesState by viewModel.publicChallengesState.collectAsStateWithLifecycle()
    val myChatsState by viewModel.myChatsState.collectAsStateWithLifecycle()
    val activeChatId by viewModel.selectedChatId.collectAsStateWithLifecycle()
    val activeChatMessagesState by viewModel.activeChatMessagesState.collectAsStateWithLifecycle()
    val activePostCommentsState by viewModel.activePostCommentsState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val blockedUsersState by viewModel.blockedUsersState.collectAsStateWithLifecycle()
    val myReportsState by viewModel.myReportsState.collectAsStateWithLifecycle()
    val verificationScanState by viewModel.verificationScanState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val inspectedProfile by viewModel.inspectedUserProfile.collectAsStateWithLifecycle()
    val challengeSheetOpponent by viewModel.challengeSheetOpponent.collectAsStateWithLifecycle()

    val myProfile = (myProfileState as? UiState.Success)?.data
    val publicProfiles = (publicProfilesState as? UiState.Success)?.data.orEmpty()
    val myGameProfiles = (myGameProfilesState as? UiState.Success)?.data.orEmpty()
    val myFollows = (myFollowsState as? UiState.Success)?.data.orEmpty()
    val posts = (publicPostsState as? UiState.Success)?.data.orEmpty()
    val myChallenges = (myChallengesState as? UiState.Success)?.data.orEmpty()
    val publicChallenges = (publicChallengesState as? UiState.Success)?.data.orEmpty()
    val myChats = (myChatsState as? UiState.Success)?.data.orEmpty()
    val activeMessages = (activeChatMessagesState as? UiState.Success)?.data.orEmpty()
    val activeComments = (activePostCommentsState as? UiState.Success)?.data.orEmpty()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val blockedUsers = (blockedUsersState as? UiState.Success)?.data.orEmpty()
    val blockedIds = remember(blockedUsers) { blockedUsers.map { it.blockedId }.toSet() }
    val myReports = (myReportsState as? UiState.Success)?.data.orEmpty()

    // 0 = Home, 1 = Explore, 2 = Arena, 3 = Chat, 4 = Profile
    var currentTab by remember { mutableIntStateOf(0) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var challengeLinkedChat by remember { mutableStateOf<ChatThread?>(null) }

    if (currentTab != 0) {
        BackHandler {
            if (currentTab == 3 && activeChatId != null) {
                viewModel.selectChat(null)
            } else {
                viewModel.selectUserProfileToInspect(null)
                currentTab = 0
            }
        }
    }

    val unreadNotificationsCount = remember(notifications) { notifications.count { !it.isRead } }
    val activeChallengesCount = remember(myChallenges) {
        myChallenges.count { it.status in listOf("sent", "countered", "accepted", "in_progress") }
    }

    Scaffold(
        containerColor = ArenaObsidian,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = ArenaCardSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ARENAX",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = TrophyGold.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .border(1.dp, TrophyGold.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .clickable { currentTab = 2 }
                            ) {
                                Text(
                                    text = "⚡ ${myProfile?.eloRating ?: 1200} ELO • ${myProfile?.arenaPoints ?: 500} PTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TrophyGold,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    viewModel.selectUserProfileToInspect(null)
                                    currentTab = 4
                                }
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotificationsCount > 0) {
                                            Badge(containerColor = NeonPurple) {
                                                Text("$unreadNotificationsCount")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = statusBanner != null) {
                        if (statusBanner != null) {
                            Surface(
                                color = NeonPurple.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.clearStatusBanner() }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = statusBanner!!,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = CyberCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "DISMISS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == 0 || currentTab == 1) {
                FloatingActionButton(
                    onClick = { showCreatePostDialog = true },
                    containerColor = NeonPurple,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.testTag("create_post_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Post")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ArenaCardSurface,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                val navItems = listOf(
                    Triple(0, "Home", Icons.Default.Home),
                    Triple(1, "Explore", Icons.Default.Explore),
                    Triple(2, "Arena", Icons.Default.Bolt),
                    Triple(3, "Chat", Icons.AutoMirrored.Filled.Chat),
                    Triple(4, "Profile", Icons.Default.Person)
                )
                navItems.forEach { (index, label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = {
                            if (index != 4) {
                                viewModel.selectUserProfileToInspect(null)
                            }
                            currentTab = index
                        },
                        icon = {
                            if (index == 2 && activeChallengesCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = CyberCyan) {
                                            Text("$activeChallengesCount", color = ArenaObsidian)
                                        }
                                    }
                                ) {
                                    Icon(icon, contentDescription = label)
                                }
                            } else {
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberCyan,
                            selectedTextColor = CyberCyan,
                            indicatorColor = NeonPurple.copy(alpha = 0.25f),
                            unselectedIconColor = TextSecondarySlate,
                            unselectedTextColor = TextSecondarySlate
                        ),
                        modifier = Modifier.testTag("nav_tab_${label.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ArenaObsidian)
        ) {
            when (currentTab) {
                0 -> HomeFeedScreen(
                    myProfile = myProfile,
                    publicProfiles = publicProfiles,
                    myFollows = myFollows,
                    posts = posts,
                    blockedIds = blockedIds,
                    activePostComments = activeComments,
                    onNavigateToVerify = {
                        viewModel.selectUserProfileToInspect(null)
                        currentTab = 4
                    },
                    onInspectProfile = { rival ->
                        viewModel.selectUserProfileToInspect(rival)
                        currentTab = 4
                    },
                    onToggleFollow = { rival -> viewModel.toggleFollow(rival) },
                    onChallengeUser = { rival ->
                        challengeLinkedChat = null
                        viewModel.openChallengeSheet(rival)
                    },
                    onLikePost = { post -> viewModel.toggleLikePost(post) },
                    onSavePost = { post -> viewModel.toggleSavePost(post) },
                    onDeletePost = { post -> viewModel.deleteOwnPost(post) },
                    onOpenComments = { post -> viewModel.selectPostForComments(post.id) },
                    onCloseComments = { viewModel.selectPostForComments(null) },
                    onAddComment = { post, text -> viewModel.addCommentToPost(post, text) },
                    onReportTarget = { type, id, reason -> viewModel.submitReport(type, id, reason) }
                )

                1 -> ExploreScreen(
                    myProfile = myProfile,
                    publicProfiles = publicProfiles,
                    myFollows = myFollows,
                    blockedIds = blockedIds,
                    onInspectProfile = { player ->
                        viewModel.selectUserProfileToInspect(player)
                        currentTab = 4
                    },
                    onToggleFollow = { player -> viewModel.toggleFollow(player) },
                    onChallengeUser = { player ->
                        challengeLinkedChat = null
                        viewModel.openChallengeSheet(player)
                    },
                    onMessageUser = { player ->
                        viewModel.startOrOpenDirectChat(player) {
                            currentTab = 3
                        }
                    }
                )

                2 -> ArenaScreen(
                    myProfile = myProfile,
                    myChallenges = myChallenges,
                    publicChallenges = publicChallenges,
                    publicProfiles = publicProfiles,
                    myFollows = myFollows,
                    missions = viewModel.getMissions(myProfile, myChallenges, posts),
                    onChallengeRival = { rival ->
                        challengeLinkedChat = null
                        viewModel.openChallengeSheet(rival)
                    },
                    onUpdateChallengeStatus = { challenge, newStatus, cTime, cMap, cWeap, cStake ->
                        viewModel.updateChallengeStatus(challenge, newStatus, cTime, cMap, cWeap, cStake)
                    },
                    onSubmitChallengeScore = { challenge, myS, theirS, proof, simulateMatch ->
                        viewModel.submitChallengeScore(challenge, myS, theirS, proof, simulateMatch)
                    },
                    onInspectProfile = { player ->
                        viewModel.selectUserProfileToInspect(player)
                        currentTab = 4
                    }
                )

                3 -> ChatScreen(
                    myProfile = myProfile,
                    chats = myChats,
                    activeChatId = activeChatId,
                    messages = activeMessages,
                    publicProfiles = publicProfiles,
                    myChallenges = myChallenges,
                    onSelectChat = { chatId -> viewModel.selectChat(chatId) },
                    onStartDirectChat = { rival -> viewModel.startOrOpenDirectChat(rival) },
                    onCreateSquadChat = { title, members -> viewModel.createSquadChat(title, members) },
                    onSendMessage = { text -> viewModel.sendMessageInActiveChat(text) },
                    onOpenSwordChallenge = { opponent, chatThread ->
                        challengeLinkedChat = chatThread
                        viewModel.openChallengeSheet(opponent)
                    },
                    onAcceptChallengeFromChat = { challenge ->
                        viewModel.updateChallengeStatus(challenge, "accepted")
                    },
                    onDeclineChallengeFromChat = { challenge ->
                        viewModel.updateChallengeStatus(challenge, "declined")
                    }
                )

                4 -> ProfileScreen(
                    myProfile = myProfile,
                    inspectedProfile = inspectedProfile,
                    myGameProfiles = myGameProfiles,
                    myFollows = myFollows,
                    allPosts = posts,
                    notifications = notifications,
                    blockedUsers = blockedUsers,
                    myReports = myReports,
                    disputedChallenges = myChallenges.filter { it.status == "disputed" },
                    verificationScanState = verificationScanState,
                    onClearInspectedProfile = {
                        viewModel.selectUserProfileToInspect(null)
                        currentTab = 0
                    },
                    onToggleFollow = { player -> viewModel.toggleFollow(player) },
                    onMessageUser = { player ->
                        viewModel.startOrOpenDirectChat(player) {
                            currentTab = 3
                        }
                    },
                    onChallengeUser = { player ->
                        challengeLinkedChat = null
                        viewModel.openChallengeSheet(player)
                    },
                    onBlockOrUnblockUser = { uid, uname -> viewModel.blockOrUnblockUser(uid, uname) },
                    onReportTarget = { type, id, reason -> viewModel.submitReport(type, id, reason) },
                    onSaveProfileSettings = { uname, dname, bio, game, lang, time, priv ->
                        viewModel.saveProfileDetails(uname, dname, bio, game, lang, time, priv)
                    },
                    onGenerateAndScanSampleScreenshot = { game, ign, uid, rank, level, code ->
                        viewModel.generateSampleScreenshotAndVerifyWithGemini(game, ign, uid, rank, level, code)
                    },
                    onScanGalleryBitmap = { bmp, game, code ->
                        viewModel.analyzeScreenshotWithGeminiVision(bmp, game, code)
                    },
                    onConfirmExtractedVerification = { extracted ->
                        viewModel.confirmExtractedVerification(extracted)
                    },
                    onResetVerificationScan = { viewModel.resetVerificationScan() },
                    onMarkNotificationRead = { id -> viewModel.markNotificationAsRead(id) },
                    onSignOut = {
                        signOutArenaUser(
                            context = context,
                            onSignOutComplete = {},
                            scope = scope
                        )
                    }
                )
            }
        }
    }

    if (challengeSheetOpponent != null) {
        ChallengeCreationDialog(
            opponent = challengeSheetOpponent!!,
            myProfile = myProfile,
            onDismiss = {
                challengeLinkedChat = null
                viewModel.closeChallengeSheet()
            },
            onSubmitChallenge = { game, mode, mapName, weaponsRule, rounds, customNote, scheduledTime, stakePoints ->
                viewModel.sendChallenge(
                    opponent = challengeSheetOpponent!!,
                    game = game,
                    mode = mode,
                    mapName = mapName,
                    weaponsRule = weaponsRule,
                    rounds = rounds,
                    customNote = customNote,
                    scheduledTimeLabel = scheduledTime,
                    stakePoints = stakePoints,
                    alsoPostToChat = challengeLinkedChat
                )
                challengeLinkedChat = null
            }
        )
    }

    if (showCreatePostDialog) {
        CreatePostDialog(
            onDismiss = { showCreatePostDialog = false },
            onCreatePhotoPost = { caption, gameTag, hashtags, presetMedia ->
                viewModel.createPhotoPost(caption, gameTag, hashtags, presetMedia)
            },
            onCreateYouTubePost = { url, videoId, title, caption, gameTag, hashtags ->
                viewModel.createYouTubeVideoPost(url, videoId, title, caption, gameTag, hashtags)
            }
        )
    }
}
