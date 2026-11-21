package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.UserEntity
import com.example.data.repository.SentinelEvent
import com.example.service.LocationTrackingService
import com.example.ui.components.AppRestrictionOverlay
import com.example.ui.components.FocusSenseTopBar
import com.example.ui.dialogs.CreateChildAccountDialog
import com.example.ui.dialogs.ParentPinDialog
import com.example.ui.screens.auth.RoleSelectionAuthScreen
import com.example.ui.screens.child.ChildDashboardScreen
import com.example.ui.screens.parent.ParentDashboardScreen
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Navy800
import com.example.ui.theme.PurpleAccent
import com.example.ui.viewmodel.FocusSenseViewModel
import com.example.ui.viewmodel.FocusSenseViewModelFactory
import com.example.ui.viewmodel.LiveScreenAndStatsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: FocusSenseViewModel by viewModels {
        FocusSenseViewModelFactory((application as FocusSenseApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val openAllowedChannels = intent?.getBooleanExtra("extra_open_allowed_channels", false) == true

        setContent {
            MyApplicationTheme {
                FocusSenseApp(
                    viewModel = viewModel,
                    initialOpenAllowedChannels = openAllowedChannels
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun FocusSenseApp(
    viewModel: FocusSenseViewModel,
    initialOpenAllowedChannels: Boolean = false
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isSessionReady by viewModel.isSessionReady.collectAsStateWithLifecycle()

    val appContext = LocalContext.current

    if (!isSessionReady) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Navy800),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(IndigoPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "FocusSense",
                        tint = Color.White,
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "FocusSense",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = EmeraldSafe, modifier = Modifier.size(28.dp))
            }
        }
        return
    }

    LaunchedEffect(currentUser?.userId, currentUser?.role) {
        if (currentUser?.role != "child" && LocationTrackingService.isTrackingActive.value) {
            try {
                appContext.startService(
                    Intent(appContext, LocationTrackingService::class.java).apply {
                        action = LocationTrackingService.ACTION_STOP
                    }
                )
            } catch (_: Exception) {}
        }
    }

    if (currentUser == null) {
        RoleSelectionAuthScreen(viewModel = viewModel)
        return
    }

    val activeUser = currentUser!!
    key(activeUser.userId) {
        val liveViewModel: LiveScreenAndStatsViewModel = viewModel(
            key = activeUser.userId,
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY]) as FocusSenseApplication
                    LiveScreenAndStatsViewModel(focusRepo = app.repository)
                }
            }
        )

        val selectedChildId by viewModel.selectedChildId.collectAsStateWithLifecycle()
        val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
        val childrenUsers by viewModel.childrenUsers.collectAsStateWithLifecycle()
        val parentUsers by viewModel.parentUsers.collectAsStateWithLifecycle()

        val activityLogs by viewModel.activityLogs.collectAsStateWithLifecycle()
        val flaggedAlerts by viewModel.flaggedAlerts.collectAsStateWithLifecycle()
        val scheduleRules by viewModel.scheduleRules.collectAsStateWithLifecycle()
        val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
        val latestLocation by viewModel.latestLocation.collectAsStateWithLifecycle()
        val locationHistory by viewModel.locationHistory.collectAsStateWithLifecycle()
        val allDevices by viewModel.allDevices.collectAsStateWithLifecycle()

        val activeRestrictionOverlay by viewModel.activeRestrictionOverlay.collectAsStateWithLifecycle()
        val pomodoroState by viewModel.pomodoroState.collectAsStateWithLifecycle()
        val allowedYouTubeChannels by viewModel.allowedYouTubeChannels.collectAsStateWithLifecycle()
        val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
        val isSearchingChannels by viewModel.isSearchingChannels.collectAsStateWithLifecycle()
        val activePairingCode by viewModel.activePairingCode.collectAsStateWithLifecycle()

        val childUsageStats by liveViewModel.childUsageStats.collectAsStateWithLifecycle()
        val myUsageStats by liveViewModel.myUsageStats.collectAsStateWithLifecycle()

        var childInitialTab by remember { mutableIntStateOf(0) }
        LaunchedEffect(initialOpenAllowedChannels) {
            if (initialOpenAllowedChannels) {
                childInitialTab = 0
            }
        }

        var showPinDialog by remember { mutableStateOf(false) }
        var showProfileSwitchDialog by remember { mutableStateOf(false) }
        var showCreateChildDialog by remember { mutableStateOf(false) }
        var pendingSwitchUser by remember { mutableStateOf<UserEntity?>(null) }

        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val context = LocalContext.current

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(activeUser.userId, activeUser.role) {
            if (activeUser.role == "parent" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, activeUser.userId, activeUser.role, selectedChildId) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && activeUser.role == "parent") {
                    viewModel.fetchRemoteChildData(selectedChildId)
                    if (selectedChildId.isNotBlank()) {
                        liveViewModel.refreshChildUsageStats(selectedChildId)
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(activeUser.userId, activeUser.role) {
            if (activeUser.role == "child") {
                val hasFineLocation = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasFineLocation && !LocationTrackingService.isTrackingActive.value) {
                    try {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, LocationTrackingService::class.java).apply {
                                action = LocationTrackingService.ACTION_START
                            }
                        )
                    } catch (_: Exception) {}
                }

                viewModel.refreshInstalledApps(context)
                viewModel.syncSchedules()
                viewModel.checkAndRespondToLocationPing(context)
                while (true) {
                    delay(30000)
                    viewModel.syncSchedules()
                    viewModel.checkAndRespondToLocationPing(context)
                    viewModel.syncData()
                }
            } else {
                if (LocationTrackingService.isTrackingActive.value) {
                    try {
                        context.startService(
                            Intent(context, LocationTrackingService::class.java).apply {
                                action = LocationTrackingService.ACTION_STOP
                            }
                        )
                    } catch (_: Exception) {}
                }

                viewModel.loadActivePairingCode()
                viewModel.fetchRemoteChildData(viewModel.selectedChildId.value)
                while (true) {
                    delay(12000)
                    viewModel.fetchRemoteChildData(viewModel.selectedChildId.value)
                    liveViewModel.refreshChildUsageStats(viewModel.selectedChildId.value)
                }
            }
        }

        LaunchedEffect(activeUser.userId, activeUser.role) {
            val app = context.applicationContext as FocusSenseApplication
            while (true) {
                liveViewModel.collectAndSyncDeviceUsageStats(
                    context = context,
                    user = activeUser,
                    installedApps = installedApps,
                    blockedPackages = app.repository.instantBlockedPackages.value
                )
                delay(45_000L)
            }
        }

        LaunchedEffect(selectedChildId) {
            if (activeUser.role == "parent" && selectedChildId.isNotBlank()) {
                viewModel.fetchRemoteChildData(selectedChildId)
                liveViewModel.refreshChildUsageStats(selectedChildId)
            }
        }

        LaunchedEffect(activeUser.userId, activeUser.role) {
            viewModel.eventStream.collect { event ->
                if (event is SentinelEvent.ContentFlagged && activeUser.role == "parent") {
                    val bodyText = event.log.extractedText
                        .ifBlank { event.log.aiAnalysisSummary }
                        .take(140)
                    sendParentSystemNotification(
                        context = context,
                        notificationId = (event.log.logId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1),
                        title = "Safety Alert: ${event.threatResult.threatCategory}",
                        message = "${event.log.appName}: $bodyText"
                    )
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            "Safety Alert: ${event.threatResult.threatCategory} in ${event.log.appName}"
                        )
                    }
                }
            }
        }

        val selectedChild = childrenUsers.find { it.userId == selectedChildId } ?: childrenUsers.firstOrNull()
        val effectiveChildId = selectedChild?.userId ?: selectedChildId

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                FocusSenseTopBar(
                    currentUser = currentUser,
                    children = childrenUsers,
                    selectedChildId = selectedChildId,
                    onSelectChild = { viewModel.selectChild(it) },
                    onSwitchRoleRequested = {
                        if (currentUser?.role == "child") {
                            showPinDialog = true
                        } else {
                            showProfileSwitchDialog = true
                        }
                    },
                    onCreateChildRequested = { showCreateChildDialog = true }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val app = context.applicationContext as FocusSenseApplication
                if (currentUser?.role == "parent") {
                    ParentDashboardScreen(
                        selectedChild = selectedChild,
                        activityLogs = activityLogs,
                        flaggedAlerts = flaggedAlerts,
                        scheduleRules = scheduleRules,
                        latestLocation = latestLocation,
                        locationHistory = locationHistory,
                        devices = allDevices,
                        onAcknowledgeAlert = { viewModel.acknowledgeAlert(it) },
                        onRemoveLog = { viewModel.removeLog(it) },
                        onAddRule = { name, category, start, end, days, apps ->
                            viewModel.addScheduleRule(name, category, start, end, days, apps)
                        },
                        onUpdateRule = { rule -> viewModel.updateScheduleRule(rule) },
                        onToggleRule = { ruleId, active -> viewModel.toggleRuleActive(ruleId, active) },
                        onDeleteRule = { viewModel.deleteScheduleRule(it) },
                        onRequestLocationPing = { viewModel.requestChildLocation(selectedChildId) },
                        activePairingCode = activePairingCode,
                        onGeneratePairingCode = { viewModel.generatePairingCode() },
                        installedApps = installedApps,
                        onToggleAppBlock = { pkg, isBlocked -> viewModel.toggleAppBlock(pkg, isBlocked) },
                        onRefreshInstalledApps = { viewModel.refreshInstalledApps(context) },
                        onSwitchAccountRequested = { showProfileSwitchDialog = true },
                        onUpdateParentPin = { newPin ->
                            viewModel.updateParentPin(newPin)
                            scope.launch { snackbarHostState.showSnackbar("Parent PIN updated") }
                        },
                        childUsageStats = childUsageStats,
                        myUsageStats = myUsageStats,
                        onRefreshUsageStats = {
                            liveViewModel.refreshChildUsageStats(effectiveChildId)
                            currentUser?.let { user ->
                                liveViewModel.collectAndSyncDeviceUsageStats(
                                    context = context,
                                    user = user,
                                    installedApps = installedApps,
                                    blockedPackages = app.repository.instantBlockedPackages.value
                                )
                            }
                        },
                        googleAccountEmail = currentUser?.email,
                        onSignOutAll = { viewModel.signOut() }
                    )
                } else {
                    ChildDashboardScreen(
                        currentChild = currentUser,
                        scheduleRules = scheduleRules,
                        pomodoroState = pomodoroState,
                        allowedYouTubeChannels = allowedYouTubeChannels,
                        searchResults = searchResults,
                        isSearchingChannels = isSearchingChannels,
                        onSearchChannels = { query -> viewModel.searchChannels(query) },
                        onClearSearch = { viewModel.clearSearchResults() },
                        onAddChannelFromSearch = { result -> viewModel.addAllowedChannel(result) },
                        initialTab = childInitialTab,
                        onToggleYouTubeChannelAllowed = { channelId, isAllowed ->
                            viewModel.toggleYouTubeChannelAllowed(channelId, isAllowed)
                        },
                        onRemoveYouTubeChannel = { channelId ->
                            viewModel.removeYouTubeChannel(channelId)
                        },
                        onStartPomodoro = { pkgs, label, mins ->
                            viewModel.startPomodoroFocus(pkgs, label, mins)
                        },
                        onPausePomodoro = { viewModel.pausePomodoroFocus() },
                        onResumePomodoro = { viewModel.resumePomodoroFocus() },
                        onStopPomodoro = { viewModel.stopPomodoroFocus() },
                        onRequestParentUnlock = { showPinDialog = true },
                        myUsageStats = myUsageStats,
                        onRefreshUsageStats = {
                            currentUser?.let { user ->
                                liveViewModel.collectAndSyncDeviceUsageStats(
                                    context = context,
                                    user = user,
                                    installedApps = installedApps,
                                    blockedPackages = app.repository.instantBlockedPackages.value
                                )
                            }
                        }
                    )
                }

                activeRestrictionOverlay?.let { blockInfo ->
                    AppRestrictionOverlay(
                        blockInfo = blockInfo,
                        onDismiss = { viewModel.dismissRestrictionOverlay() },
                        onParentOverrideRequested = {
                            viewModel.dismissRestrictionOverlay()
                            showPinDialog = true
                        },
                        onSeeAllowedChannels = {
                            viewModel.dismissRestrictionOverlay()
                            childInitialTab = 0
                        }
                    )
                }
            }
        }

        if (showCreateChildDialog) {
            val authError by viewModel.authError.collectAsStateWithLifecycle()
            val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
            CreateChildAccountDialog(
                onDismiss = {
                    viewModel.clearAuthError()
                    showCreateChildDialog = false
                },
                onCreateChild = { username, password, displayName ->
                    viewModel.createChildAccount(username, password, displayName) {
                        showCreateChildDialog = false
                    }
                },
                isLoading = isAuthLoading,
                errorMessage = authError
            )
        }

        if (showPinDialog) {
            val parentPin = parentUsers.firstOrNull()?.pin?.takeIf { it.isNotBlank() } ?: "1234"
            ParentPinDialog(
                onDismiss = { showPinDialog = false },
                expectedPin = parentPin,
                onPinVerified = {
                    showPinDialog = false
                    if (pendingSwitchUser != null) {
                        viewModel.switchUser(pendingSwitchUser!!)
                        pendingSwitchUser = null
                    } else {
                        showProfileSwitchDialog = true
                    }
                }
            )
        }

        if (showProfileSwitchDialog) {
            Dialog(onDismissRequest = { showProfileSwitchDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(22.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Switch Profile",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showProfileSwitchDialog = false },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }

                        if (allUsers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            allUsers.forEach { user ->
                                val isCurrent = user.userId == currentUser?.userId
                                val roleColor = if (user.role == "parent") PurpleAccent else EmeraldSafe

                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) IndigoPrimary.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            showProfileSwitchDialog = false
                                            viewModel.switchUser(user)
                                        }
                                        .testTag("profile_item_${user.userId}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(roleColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (user.role == "parent") Icons.Default.Person else Icons.Default.Face,
                                                    contentDescription = null,
                                                    tint = roleColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = user.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = user.role.replaceFirstChar { it.uppercase() },
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (isCurrent) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = IndigoPrimary
                                            ) {
                                                Text(
                                                    text = "Active",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = {
                                showProfileSwitchDialog = false
                                viewModel.signOut()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("auth_signout_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Sign Out", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

private fun sendParentSystemNotification(
    context: Context,
    notificationId: Int,
    title: String,
    message: String
) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FocusSenseApplication.CHANNEL_ALERTS,
                "FocusSense Safety Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Safety alerts from your child's paired phone"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FocusSenseApplication.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
