package com.example.ui.screens.child

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.AllowedYouTubeChannelEntity
import com.example.data.model.AppUsageStatRecord
import com.example.data.model.RestrictedAppInfo
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.data.remote.YouTubeChannelSearchResult
import com.example.data.repository.FocusSenseRepository
import com.example.ui.components.UsageStatsGraphsCard
import com.example.ui.screens.child.youtube.FocusYouTubeScreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PurpleAccent
import com.example.util.ChildPermissionManager
import com.example.util.InstalledAppScanner
import java.util.Locale

@Composable
fun ChildDashboardScreen(
    currentChild: UserEntity?,
    scheduleRules: List<ScheduleRuleEntity>,
    pomodoroState: FocusSenseRepository.PomodoroState? = null,
    allowedYouTubeChannels: List<AllowedYouTubeChannelEntity> = emptyList(),
    onToggleYouTubeChannelAllowed: (channelId: String, isAllowed: Boolean) -> Unit = { _, _ -> },
    onRemoveYouTubeChannel: (channelId: String) -> Unit = {},
    searchResults: List<YouTubeChannelSearchResult> = emptyList(),
    isSearchingChannels: Boolean = false,
    onSearchChannels: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onAddChannelFromSearch: (YouTubeChannelSearchResult) -> Unit = {},
    initialTab: Int = 0,
    onStartPomodoro: (pkgs: Set<String>, name: String, mins: Int) -> Unit = { _, _, _ -> },
    onPausePomodoro: () -> Unit = {},
    onResumePomodoro: () -> Unit = {},
    onStopPomodoro: () -> Unit = {},
    onRequestParentUnlock: () -> Unit,
    myUsageStats: List<AppUsageStatRecord> = emptyList(),
    onRefreshUsageStats: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionStatus by remember {
        mutableStateOf(ChildPermissionManager.checkAllPermissions(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = ChildPermissionManager.checkAllPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }

    Scaffold(
        modifier = modifier.fillMaxWidth(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Study YouTube") },
                    label = { Text("Study YT", fontSize = 10.sp) },
                    modifier = Modifier.testTag("child_tab_youtube")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Usage Stats") },
                    label = { Text("Stats", fontSize = 10.sp) },
                    modifier = Modifier.testTag("child_tab_stats")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Focus Timer") },
                    label = { Text("Focus", fontSize = 10.sp) },
                    modifier = Modifier.testTag("child_tab_focus")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Schedule") },
                    label = { Text("Schedule", fontSize = 10.sp) },
                    modifier = Modifier.testTag("child_tab_schedule")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = {
                        Box {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Setup & Security",
                                tint = if (permissionStatus.isFullyProtected) EmeraldSafe else CoralDanger
                            )
                            if (!permissionStatus.isFullyProtected) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CoralDanger)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    },
                    label = {
                        Text(
                            text = if (permissionStatus.isFullyProtected) "Security" else "Setup",
                            fontSize = 10.sp,
                            color = if (permissionStatus.isFullyProtected) EmeraldSafe else CoralDanger
                        )
                    },
                    modifier = Modifier.testTag("child_tab_permissions")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedTab != 4 && !permissionStatus.isFullyProtected) {
                Surface(
                    color = if (permissionStatus.allCrucialGranted) AmberWarningBg else CoralDangerBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedTab = 4 }
                        .testTag("child_permission_alert_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (permissionStatus.allCrucialGranted) Icons.Default.Warning else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (permissionStatus.allCrucialGranted) AmberWarning else CoralDanger,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Setup ${permissionStatus.grantedCount}/${permissionStatus.totalCount} · Tap to finish",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (permissionStatus.allCrucialGranted) AmberWarning else CoralDanger
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Open setup",
                            tint = if (permissionStatus.allCrucialGranted) AmberWarning else CoralDanger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> FocusYouTubeScreen(
                        channels = allowedYouTubeChannels,
                        searchResults = searchResults,
                        isSearching = isSearchingChannels,
                        onSearchChannels = onSearchChannels,
                        onClearSearch = onClearSearch,
                        onAddChannelFromSearch = onAddChannelFromSearch,
                        onToggleChannelAllowed = onToggleYouTubeChannelAllowed,
                        onRemoveChannel = onRemoveYouTubeChannel
                    )
                    1 -> ChildStatsTab(
                        hasUsagePermission = permissionStatus.isUsageAccessGranted,
                        myUsageStats = myUsageStats,
                        onRequestUsagePermission = { ChildPermissionManager.openUsageAccessSettings(context) },
                        onRefreshUsageStats = onRefreshUsageStats
                    )
                    2 -> ChildFocusShieldTab(
                        pomodoroState = pomodoroState,
                        onStartPomodoro = onStartPomodoro,
                        onPausePomodoro = onPausePomodoro,
                        onResumePomodoro = onResumePomodoro,
                        onStopPomodoro = onStopPomodoro
                    )
                    3 -> ChildScheduleTab(
                        rules = scheduleRules
                    )
                    4 -> Column(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            color = PurpleAccent.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Parent Lock",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PurpleAccent
                                )
                                OutlinedButton(
                                    onClick = onRequestParentUnlock,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("child_tab_switch_account")
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Parent Unlock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        ChildPermissionWizardScreen(
                            childName = currentChild?.name ?: "Child",
                            onContinueToDashboard = { selectedTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: CHILD SCREEN TIME STATS
// -------------------------------------------------------------
@Composable
private fun ChildStatsTab(
    hasUsagePermission: Boolean,
    myUsageStats: List<AppUsageStatRecord>,
    onRequestUsagePermission: () -> Unit,
    onRefreshUsageStats: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            UsageStatsGraphsCard(
                title = "My Screen Time Today",
                subtitle = "Tap a slice for details",
                stats = myUsageStats,
                hasUsagePermission = hasUsagePermission,
                showPermissionAction = true,
                onRequestUsagePermission = onRequestUsagePermission,
                onRefreshStats = onRefreshUsageStats
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 2: POMODORO FOCUS RING
// -------------------------------------------------------------
@Composable
private fun ChildFocusShieldTab(
    pomodoroState: FocusSenseRepository.PomodoroState?,
    onStartPomodoro: (pkgs: Set<String>, name: String, mins: Int) -> Unit,
    onPausePomodoro: () -> Unit,
    onResumePomodoro: () -> Unit,
    onStopPomodoro: () -> Unit
) {
    val context = LocalContext.current
    val detectedApps = remember {
        InstalledAppScanner.getInstalledLauncherApps(context, "child-default").map {
            RestrictedAppInfo(
                packageName = it.packageName,
                appName = it.appName,
                iconName = "app",
                category = it.category,
                isInstalledOnDevice = true
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ChildPomodoroFocusCard(
                pomodoroState = pomodoroState,
                installedApps = detectedApps,
                onStartPomodoro = onStartPomodoro,
                onPausePomodoro = onPausePomodoro,
                onResumePomodoro = onResumePomodoro,
                onStopPomodoro = onStopPomodoro
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildPomodoroFocusCard(
    pomodoroState: FocusSenseRepository.PomodoroState?,
    installedApps: List<RestrictedAppInfo>,
    onStartPomodoro: (pkgs: Set<String>, name: String, mins: Int) -> Unit,
    onPausePomodoro: () -> Unit,
    onResumePomodoro: () -> Unit,
    onStopPomodoro: () -> Unit
) {
    var selectedMins by remember { mutableIntStateOf(25) }
    var blockAllSelected by remember { mutableStateOf(false) }
    var selectedPackages by remember(installedApps) {
        val defaults = installedApps
            .filter {
                it.category.contains("Social", ignoreCase = true) ||
                    it.category.contains("Gaming", ignoreCase = true) ||
                    it.category.contains("Streaming", ignoreCase = true)
            }
            .take(3)
            .map { it.packageName }
            .toSet()
        mutableStateOf(
            if (defaults.isNotEmpty()) defaults
            else installedApps.take(1).map { it.packageName }.toSet()
        )
    }
    var showAppPickerDialog by remember { mutableStateOf(false) }

    val totalPlannedSeconds = (pomodoroState?.durationMinutes ?: selectedMins).coerceAtLeast(1) * 60f
    val remainingSecs = pomodoroState?.remainingSeconds ?: (selectedMins * 60L)
    val minsLeft = remainingSecs / 60
    val secsLeft = remainingSecs % 60
    val timerDisplay = String.format(Locale.getDefault(), "%02d:%02d", minsLeft, secsLeft)

    val progressFraction = (remainingSecs.toFloat() / totalPlannedSeconds).coerceIn(0f, 1f)
    val animatedSweep by animateFloatAsState(
        targetValue = 360f * progressFraction,
        animationSpec = tween(durationMillis = 350),
        label = "PomodoroRingSweep"
    )
    val ringColor = when {
        pomodoroState == null -> IndigoPrimary
        pomodoroState.isRunning -> CoralDanger
        else -> AmberWarning
    }

    val selectedSummaryLabel = remember(blockAllSelected, selectedPackages, installedApps) {
        when {
            blockAllSelected -> "All Apps"
            selectedPackages.isEmpty() -> "Choose apps"
            selectedPackages.size == 1 -> installedApps.find { it.packageName == selectedPackages.first() }?.appName ?: "1 app"
            else -> "${selectedPackages.size} apps"
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Focus Timer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ringColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = when {
                            pomodoroState == null -> "Unlocked"
                            pomodoroState.isRunning -> "Focusing"
                            else -> "Paused"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ringColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Animated Circular Progress Countdown Ring
            Box(
                modifier = Modifier.size(196.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = ringColor.copy(alpha = 0.14f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )

                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = timerDisplay,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pomodoroState?.appName ?: selectedSummaryLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (pomodoroState == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        val isSelected = selectedMins == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMins = mins },
                            label = { Text("${mins}m", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoPrimary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { showAppPickerDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pomodoro_multi_app_picker_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pause Apps: $selectedSummaryLabel",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!blockAllSelected && selectedPackages.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedPackages.take(6).forEach { pkg ->
                            val label = installedApps.find { it.packageName == pkg }?.appName ?: pkg.substringAfterLast('.')
                            FilterChip(
                                selected = true,
                                onClick = { selectedPackages = selectedPackages - pkg },
                                label = { Text("$label ✕", fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val canStart = blockAllSelected || selectedPackages.isNotEmpty()
                Button(
                    onClick = {
                        val pkgsToBlock = if (blockAllSelected) setOf("*") else selectedPackages
                        onStartPomodoro(pkgsToBlock, selectedSummaryLabel, selectedMins)
                    },
                    enabled = canStart,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralDanger),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pomodoro_start_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Focus (${selectedMins}m)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pomodoroState.isRunning) {
                        OutlinedButton(
                            onClick = onPausePomodoro,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause")
                        }
                    } else {
                        Button(
                            onClick = onResumePomodoro,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume")
                        }
                    }

                    OutlinedButton(
                        onClick = onStopPomodoro,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralDanger),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "End")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("End")
                    }
                }
            }
        }
    }

    if (showAppPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAppPickerDialog = false },
            title = { Text("Pause Apps While Focusing", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { blockAllSelected = !blockAllSelected }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = blockAllSelected,
                            onCheckedChange = { blockAllSelected = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("All Distracting Apps", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        items(installedApps) { app ->
                            val isChecked = !blockAllSelected && selectedPackages.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        blockAllSelected = false
                                        selectedPackages = if (selectedPackages.contains(app.packageName)) {
                                            selectedPackages - app.packageName
                                        } else {
                                            selectedPackages + app.packageName
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        blockAllSelected = false
                                        selectedPackages = if (checked) {
                                            selectedPackages + app.packageName
                                        } else {
                                            selectedPackages - app.packageName
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(app.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAppPickerDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 3: CHILD SCHEDULE
// -------------------------------------------------------------
@Composable
private fun ChildScheduleTab(
    rules: List<ScheduleRuleEntity>
) {
    val context = LocalContext.current
    var filterCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Homework", "Study", "Bedtime", "Free Time")

    val appNamesMap = remember {
        InstalledAppScanner.getInstalledLauncherApps(context, "child-default")
            .associate { it.packageName to it.appName }
    }

    val filteredRules = remember(rules, filterCategory) {
        if (filterCategory == "All") rules
        else rules.filter { it.category.contains(filterCategory, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = filterCategory == cat,
                        onClick = { filterCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = IndigoPrimary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        if (filteredRules.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No schedules right now",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            items(filteredRules, key = { it.ruleId }) { rule ->
                ChildRuleCard(rule = rule, appNamesMap = appNamesMap)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChildRuleCard(
    rule: ScheduleRuleEntity,
    appNamesMap: Map<String, String>
) {
    val friendlyApps = remember(rule.restrictedPackages, appNamesMap) {
        rule.restrictedPackages.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { pkg ->
                when {
                    pkg == "*" || pkg == "all" -> "All Apps"
                    appNamesMap.containsKey(pkg) -> appNamesMap[pkg]!!
                    else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (rule.isActive) EmeraldSafeBg else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (rule.isActive) Icons.Default.HourglassBottom else Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = if (rule.isActive) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = rule.ruleName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${rule.startTime} – ${rule.endTime} · ${rule.dayOfWeek}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (rule.isActive) EmeraldSafeBg else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (rule.isActive) "Active" else "Off",
                        color = if (rule.isActive) EmeraldSafe else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (friendlyApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    friendlyApps.take(5).forEach { label ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (friendlyApps.size > 5) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "+${friendlyApps.size - 5}",
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
