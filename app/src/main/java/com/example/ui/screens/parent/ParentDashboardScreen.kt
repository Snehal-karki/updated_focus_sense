package com.example.ui.screens.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ActivityLogEntity
import com.example.data.model.AppUsageStatRecord
import com.example.data.model.DeviceEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.data.remote.GeneratePairingCodeResponseDto
import com.example.ui.components.ThreatCategoryBadge
import com.example.ui.components.UsageStatsGraphsCard
import com.example.ui.components.formatMinutesLabel
import com.example.ui.dialogs.AddScheduleRuleDialog
import com.example.ui.dialogs.ChangeParentPinDialog
import com.example.ui.dialogs.LogDetailDialog
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.util.ChildPermissionManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ParentDashboardScreen(
    selectedChild: UserEntity?,
    activityLogs: List<ActivityLogEntity>,
    flaggedAlerts: List<ActivityLogEntity>,
    scheduleRules: List<ScheduleRuleEntity>,
    latestLocation: LocationPointEntity?,
    locationHistory: List<LocationPointEntity>,
    devices: List<DeviceEntity>,
    onAcknowledgeAlert: (String) -> Unit,
    onRemoveLog: (String) -> Unit,
    onAddRule: (name: String, category: String, start: String, end: String, days: String, apps: String) -> Unit,
    onUpdateRule: (ScheduleRuleEntity) -> Unit = {},
    onToggleRule: (String, Boolean) -> Unit,
    onDeleteRule: (String) -> Unit,
    onRequestLocationPing: (() -> Unit)? = null,
    activePairingCode: GeneratePairingCodeResponseDto? = null,
    onGeneratePairingCode: (() -> Unit)? = null,
    installedApps: List<InstalledAppEntity> = emptyList(),
    onToggleAppBlock: (String, Boolean) -> Unit = { _, _ -> },
    onRefreshInstalledApps: () -> Unit = {},
    onSwitchAccountRequested: () -> Unit = {},
    onUpdateParentPin: (String) -> Unit = {},
    childUsageStats: List<AppUsageStatRecord> = emptyList(),
    myUsageStats: List<AppUsageStatRecord> = emptyList(),
    onRefreshUsageStats: () -> Unit = {},
    googleAccountEmail: String? = null,
    onSignOutAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedLogForDetail by remember { mutableStateOf<ActivityLogEntity?>(null) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ScheduleRuleEntity?>(null) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var logFilter by remember { mutableStateOf("all") }

    val childName = selectedChild?.name ?: "Child"
    val unacknowledgedThreats = remember(flaggedAlerts) { flaggedAlerts.filter { !it.isAcknowledged } }
    val blockedPackages = remember(installedApps) {
        installedApps.filter { it.isBlocked }.map { it.packageName }.toSet()
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
                    icon = {
                        if (unacknowledgedThreats.isNotEmpty()) {
                            BadgedBox(badge = { Badge { Text("${unacknowledgedThreats.size}") } }) {
                                Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                            }
                        } else {
                            Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                        }
                    },
                    label = { Text("Alerts", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_alerts")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Screen Time") },
                    label = { Text("Screen Time", fontSize = 10.sp) },
                    modifier = Modifier.testTag("tab_live_stats")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Schedule, contentDescription = "Limits") },
                    label = { Text("Limits", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_schedules")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
                    label = { Text("Map", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_location")
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Family") },
                    label = { Text("Family", fontSize = 11.sp) },
                    modifier = Modifier.testTag("tab_devices")
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 2) {
                FloatingActionButton(
                    onClick = {
                        editingRule = null
                        showAddRuleDialog = true
                    },
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_rule")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Schedule Rule")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> AlertsAndActivitiesTab(
                    childName = childName,
                    logs = activityLogs,
                    unacknowledgedThreats = unacknowledgedThreats,
                    filter = logFilter,
                    onFilterChange = { logFilter = it },
                    onSelectLog = { selectedLogForDetail = it },
                    onAcknowledge = onAcknowledgeAlert,
                    onAcknowledgeAll = {
                        unacknowledgedThreats.forEach { onAcknowledgeAlert(it.logId) }
                    }
                )
                1 -> ScreenTimeTab(
                    childName = childName,
                    childUsageStats = childUsageStats,
                    myUsageStats = myUsageStats,
                    blockedPackages = blockedPackages,
                    onToggleAppBlock = onToggleAppBlock,
                    onRefreshUsageStats = onRefreshUsageStats
                )
                2 -> LimitsAndSchedulesTab(
                    childName = childName,
                    rules = scheduleRules,
                    installedApps = installedApps,
                    onToggleRule = onToggleRule,
                    onEditRule = { rule ->
                        editingRule = rule
                        showAddRuleDialog = true
                    },
                    onDeleteRule = onDeleteRule,
                    onToggleAppBlock = onToggleAppBlock,
                    onRefreshApps = onRefreshInstalledApps,
                    onAddNewClick = {
                        editingRule = null
                        showAddRuleDialog = true
                    }
                )
                3 -> LiveLocationMapTab(
                    childName = childName,
                    latestLocation = latestLocation,
                    locationHistory = locationHistory,
                    onRefreshLocation = { onRequestLocationPing?.invoke() }
                )
                4 -> FamilyAndDevicesTab(
                    devices = devices,
                    activePairingCode = activePairingCode,
                    onGeneratePairingCode = onGeneratePairingCode,
                    googleAccountEmail = googleAccountEmail,
                    onSwitchAccountRequested = onSwitchAccountRequested,
                    onChangePinRequested = { showChangePinDialog = true },
                    onSignOutAll = onSignOutAll
                )
            }
        }
    }

    if (showAddRuleDialog) {
        AddScheduleRuleDialog(
            existingRule = editingRule,
            installedApps = installedApps,
            onDismiss = {
                showAddRuleDialog = false
                editingRule = null
            },
            onSaveRule = { name, category, start, end, days, apps ->
                val currentEditing = editingRule
                if (currentEditing == null) {
                    onAddRule(name, category, start, end, days, apps)
                } else {
                    onUpdateRule(
                        currentEditing.copy(
                            ruleName = name,
                            category = category,
                            startTime = start,
                            endTime = end,
                            dayOfWeek = days,
                            restrictedPackages = apps
                        )
                    )
                }
                showAddRuleDialog = false
                editingRule = null
            }
        )
    }

    if (showChangePinDialog) {
        ChangeParentPinDialog(
            onDismiss = { showChangePinDialog = false },
            onSavePin = { newPin ->
                onUpdateParentPin(newPin)
                showChangePinDialog = false
            }
        )
    }

    selectedLogForDetail?.let { log ->
        LogDetailDialog(
            log = log,
            onDismiss = { selectedLogForDetail = null },
            onAcknowledge = {
                onAcknowledgeAlert(log.logId)
                selectedLogForDetail = null
            },
            onRemove = {
                onRemoveLog(log.logId)
                selectedLogForDetail = null
            },
            isParent = true
        )
    }
}

// -------------------------------------------------------------
// TAB 0: ALERTS & ACTIVITIES FEED
// -------------------------------------------------------------
@Composable
private fun AlertsAndActivitiesTab(
    childName: String,
    logs: List<ActivityLogEntity>,
    unacknowledgedThreats: List<ActivityLogEntity>,
    filter: String,
    onFilterChange: (String) -> Unit,
    onSelectLog: (ActivityLogEntity) -> Unit,
    onAcknowledge: (String) -> Unit,
    onAcknowledgeAll: () -> Unit
) {
    val filteredLogs = remember(logs, filter) {
        when (filter) {
            "flagged" -> logs.filter { it.isFlagged }
            "safe" -> logs.filter { !it.isFlagged }
            else -> logs
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            if (unacknowledgedThreats.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CoralDangerBg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("urgent_alerts_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(CoralDanger),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Safety alerts",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${unacknowledgedThreats.size} New Safety Alert${if (unacknowledgedThreats.size > 1) "s" else ""}",
                                    fontWeight = FontWeight.Bold,
                                    color = CoralDanger,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "From $childName's phone",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7F1D1D)
                                )
                            }
                        }
                        TextButton(
                            onClick = onAcknowledgeAll,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(
                                text = "Ack All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralDanger
                            )
                        }
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldSafeBg.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(EmeraldSafe),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "All Clear",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "$childName is All Clear",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46),
                                fontSize = 14.sp
                            )
                            Text(
                                text = "No unreviewed safety alerts",
                                fontSize = 12.sp,
                                color = Color(0xFF047857)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = filter == "all",
                    onClick = { onFilterChange("all") },
                    label = { Text("All (${logs.size})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = IndigoPrimary,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filter == "flagged",
                    onClick = { onFilterChange("flagged") },
                    label = { Text("Alerts (${logs.count { it.isFlagged }})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CoralDanger,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = filter == "safe",
                    onClick = { onFilterChange("safe") },
                    label = { Text("Safe (${logs.count { !it.isFlagged }})", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldSafe,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No activity yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }
        } else {
            items(filteredLogs, key = { it.logId }) { log ->
                ActivityLogCard(
                    log = log,
                    onClick = { onSelectLog(log) },
                    onAcknowledge = { onAcknowledge(log.logId) }
                )
            }
        }
    }
}

@Composable
private fun ActivityLogCard(
    log: ActivityLogEntity,
    onClick: () -> Unit,
    onAcknowledge: () -> Unit
) {
    val timeAgo = remember(log.recordedAt) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(log.recordedAt))
    }
    val urgent = log.isFlagged && !log.isAcknowledged

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (urgent) CoralDangerBg.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (urgent) CoralDanger.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("activity_log_card_${log.logId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (log.isFlagged) CoralDanger.copy(alpha = 0.14f) else EmeraldSafe.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = log.appName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = if (log.isFlagged) CoralDanger else EmeraldSafe
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${log.appName} · $timeAgo",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (log.isFlagged) {
                        ThreatCategoryBadge(
                            category = log.threatCategory,
                            confidence = log.confidenceScore
                        )
                    } else {
                        Surface(shape = RoundedCornerShape(8.dp), color = EmeraldSafeBg) {
                            Text(
                                text = "Safe",
                                color = EmeraldSafe,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = log.extractedText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 17.sp
                )
            }

            if (urgent) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onAcknowledge,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CoralDanger.copy(alpha = 0.12f))
                        .testTag("ack_button_${log.logId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Acknowledge alert",
                        tint = CoralDanger,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 1: SCREEN TIME ANALYTICS
// -------------------------------------------------------------
@Composable
private fun ScreenTimeTab(
    childName: String,
    childUsageStats: List<AppUsageStatRecord>,
    myUsageStats: List<AppUsageStatRecord>,
    blockedPackages: Set<String>,
    onToggleAppBlock: (String, Boolean) -> Unit,
    onRefreshUsageStats: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var statsOwnerTab by remember { mutableIntStateOf(0) }
    var hasUsagePermission by remember {
        mutableStateOf(ChildPermissionManager.isUsageAccessGranted(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = ChildPermissionManager.isUsageAccessGranted(context)
                onRefreshUsageStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeStats = if (statsOwnerTab == 0) childUsageStats else myUsageStats
    val totalMinutes = remember(activeStats) { activeStats.sumOf { it.totalTimeMinutes } }
    val topApp = remember(activeStats) { activeStats.maxByOrNull { it.foregroundTimeMs }?.appName ?: "--" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (statsOwnerTab == 0) IndigoPrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { statsOwnerTab = 0 }
                        .testTag("stats_toggle_child")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$childName's Phone",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (statsOwnerTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (statsOwnerTab == 1) IndigoPrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { statsOwnerTab = 1 }
                        .testTag("stats_toggle_parent")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "My Phone",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (statsOwnerTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricTile(
                    label = "Today",
                    value = formatMinutesLabel(totalMinutes),
                    accentColor = IndigoPrimary,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricTile(
                    label = "Top App",
                    value = topApp,
                    accentColor = EmeraldSafe,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricTile(
                    label = "Locked",
                    value = "${blockedPackages.size}",
                    accentColor = CoralDanger,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            if (statsOwnerTab == 0) {
                UsageStatsGraphsCard(
                    title = "$childName's Usage",
                    subtitle = "Tap slice or lock icon",
                    stats = childUsageStats,
                    hasUsagePermission = true,
                    showPermissionAction = false,
                    onRequestUsagePermission = {},
                    onRefreshStats = onRefreshUsageStats,
                    blockedPackages = blockedPackages,
                    onToggleAppBlock = onToggleAppBlock
                )
            } else {
                UsageStatsGraphsCard(
                    title = "My Usage",
                    subtitle = "Tap a slice for details",
                    stats = myUsageStats,
                    hasUsagePermission = hasUsagePermission,
                    showPermissionAction = true,
                    onRequestUsagePermission = { ChildPermissionManager.openUsageAccessSettings(context) },
                    onRefreshStats = onRefreshUsageStats
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricTile(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
