package com.example.ui.screens.parent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DeviceEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.remote.GeneratePairingCodeResponseDto
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoDark
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.Navy800
import com.example.ui.theme.PurpleAccent
import com.example.util.LocationGeocoderHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// -------------------------------------------------------------
// TAB 2: LIMITS & SCHEDULES (SEGMENTED FOR ZERO CLUTTER)
// -------------------------------------------------------------
@Composable
fun LimitsAndSchedulesTab(
    childName: String,
    rules: List<ScheduleRuleEntity>,
    installedApps: List<InstalledAppEntity>,
    onToggleRule: (String, Boolean) -> Unit,
    onEditRule: (ScheduleRuleEntity) -> Unit,
    onDeleteRule: (String) -> Unit,
    onToggleAppBlock: (String, Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onAddNewClick: () -> Unit
) {
    var segment by remember { mutableIntStateOf(0) } // 0 = App Locks, 1 = Schedules
    var appCategoryFilter by remember { mutableStateOf("All") }
    var appSearchQuery by remember { mutableStateOf("") }

    val blockedCount = remember(installedApps) { installedApps.count { it.isBlocked } }
    val filteredApps = remember(installedApps, appCategoryFilter, appSearchQuery) {
        installedApps.filter { app ->
            val matchesFilter = when (appCategoryFilter) {
                "All" -> true
                "Locked" -> app.isBlocked
                "Social" -> app.category.contains("Social", ignoreCase = true)
                "Gaming" -> app.category.contains("Gaming", ignoreCase = true)
                "Streaming" -> app.category.contains("Streaming", ignoreCase = true) || app.category.contains("Video", ignoreCase = true)
                "Browsers" -> app.category.contains("Browser", ignoreCase = true)
                else -> true
            }
            val matchesSearch = appSearchQuery.isBlank() ||
                app.appName.contains(appSearchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 92.dp),
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
                listOf(
                    "App Locks ($blockedCount)" to 0,
                    "Schedules (${rules.size})" to 1
                ).forEach { (label, idx) ->
                    val selected = segment == idx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) IndigoPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { segment = idx }
                            .testTag(if (idx == 0) "limits_seg_apps" else "limits_seg_schedules")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (segment == 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        placeholder = { Text("Search apps...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRefreshApps,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(IndigoPrimary.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh child apps",
                            tint = IndigoPrimary
                        )
                    }
                }
            }

            item {
                val categories = listOf("All", "Locked", "Social", "Gaming", "Streaming", "Browsers")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = appCategoryFilter == cat,
                            onClick = { appCategoryFilter = cat },
                            label = {
                                Text(
                                    text = if (cat == "Locked") "Locked ($blockedCount)" else cat,
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            if (installedApps.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("child_installed_apps_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Waiting for $childName's apps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Open FocusSense on $childName's phone to load apps.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onRefreshApps,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Refresh")
                            }
                        }
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { appItem ->
                    val blocked = appItem.isBlocked
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (blocked) CoralDangerBg.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (blocked) CoralDanger.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onToggleAppBlock(appItem.packageName, !blocked) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (blocked) CoralDanger.copy(alpha = 0.16f) else IndigoPrimary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = appItem.appName.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = if (blocked) CoralDanger else IndigoPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = appItem.appName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (blocked) CoralDanger else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = appItem.category,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = blocked,
                                onCheckedChange = { isChecked -> onToggleAppBlock(appItem.packageName, isChecked) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CoralDanger
                                ),
                                modifier = Modifier.testTag("app_block_switch_${appItem.packageName}")
                            )
                        }
                    }
                }
            }
        } else {
            if (rules.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No Schedules Yet",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pause distracting apps during study or bedtime.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = onAddNewClick,
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Schedule", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(rules, key = { it.ruleId }) { rule ->
                    ScheduleRuleCard(
                        rule = rule,
                        installedApps = installedApps,
                        onToggle = { onToggleRule(rule.ruleId, it) },
                        onEdit = { onEditRule(rule) },
                        onDelete = { onDeleteRule(rule.ruleId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleRuleCard(
    rule: ScheduleRuleEntity,
    installedApps: List<InstalledAppEntity>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val restrictedApps = remember(rule.restrictedPackages) {
        rule.restrictedPackages.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val activeDaySet = remember(rule.dayOfWeek) {
        rule.dayOfWeek.split(",").map { it.trim() }.toSet()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onEdit() }
            .testTag("rule_card_${rule.ruleId}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.ruleName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IndigoPrimary.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "${rule.startTime} – ${rule.endTime} · ${rule.category}",
                            fontSize = 11.sp,
                            color = IndigoPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Switch(
                    checked = rule.isActive,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("rule_switch_${rule.ruleId}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Weekday Mini-Pills
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                weekDays.forEach { day ->
                    val on = activeDaySet.any { it.equals(day, ignoreCase = true) }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (on && rule.isActive) IndigoPrimary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.take(1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (on && rule.isActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                restrictedApps.take(5).forEach { pkg ->
                    val foundApp = installedApps.find { it.packageName.equals(pkg, ignoreCase = true) }
                    val simpleName = when {
                        pkg == "*" || pkg == "all" -> "All Apps"
                        foundApp != null -> foundApp.appName
                        else -> pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = simpleName,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (restrictedApps.size > 5) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = "+${restrictedApps.size - 5}",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit schedule",
                        tint = IndigoPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_rule_${rule.ruleId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete schedule",
                        tint = CoralDanger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: LOCATION & CHECK-INS (COHESIVE SINGLE HERO CARD)
// -------------------------------------------------------------
@Composable
fun LiveLocationMapTab(
    childName: String,
    latestLocation: LocationPointEntity?,
    locationHistory: List<LocationPointEntity>,
    onRefreshLocation: () -> Unit
) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("MMM dd · hh:mm a", Locale.getDefault()) }

    var resolvedPlaceLabel by remember(latestLocation?.locId, latestLocation?.locationName) {
        mutableStateOf(
            latestLocation?.let { loc ->
                if (LocationGeocoderHelper.needsPlaceResolution(loc.locationName)) {
                    LocationGeocoderHelper.sanitizePlaceLabel(null, loc.latitude, loc.longitude)
                } else {
                    LocationGeocoderHelper.sanitizePlaceLabel(loc.locationName, loc.latitude, loc.longitude)
                }
            }
        )
    }

    LaunchedEffect(latestLocation?.locId, latestLocation?.latitude, latestLocation?.longitude, latestLocation?.locationName) {
        val loc = latestLocation ?: return@LaunchedEffect
        resolvedPlaceLabel = if (LocationGeocoderHelper.needsPlaceResolution(loc.locationName)) {
            LocationGeocoderHelper.resolvePlaceLabel(context, loc.latitude, loc.longitude)
        } else {
            LocationGeocoderHelper.sanitizePlaceLabel(loc.locationName, loc.latitude, loc.longitude)
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
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("current_location_card")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Custom Radar Pin Graphic Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp)
                            .background(Brush.linearGradient(listOf(Navy800, IndigoDark)))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = size.minDimension * 0.44f,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.14f),
                                radius = size.minDimension * 0.28f,
                                center = center,
                                style = Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = (if (latestLocation != null) EmeraldSafe else AmberWarning).copy(alpha = 0.25f),
                                radius = size.minDimension * 0.15f,
                                center = center
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (latestLocation != null) EmeraldSafe else AmberWarning,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (latestLocation != null) "Live" else "Waiting",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (latestLocation != null) EmeraldSafe else AmberWarning),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "Current Location",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "$childName's Area",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = resolvedPlaceLabel ?: "Waiting for $childName's device",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = latestLocation?.let { "Updated ${timeFormat.format(Date(it.recordedAt))}" }
                                ?: "Tap below to request a location update",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = onRefreshLocation,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("refresh_location_btn")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh Location", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Check-Ins (${locationHistory.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (locationHistory.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No check-ins recorded yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(locationHistory, key = { it.locId }) { point ->
                val sanitizedLabel = remember(point.locId, point.locationName, point.latitude, point.longitude) {
                    if (LocationGeocoderHelper.needsPlaceResolution(point.locationName)) {
                        LocationGeocoderHelper.sanitizePlaceLabel(null, point.latitude, point.longitude)
                    } else {
                        LocationGeocoderHelper.sanitizePlaceLabel(point.locationName, point.latitude, point.longitude)
                    }
                }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(IndigoPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sanitizedLabel,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = timeFormat.format(Date(point.recordedAt)),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: FAMILY & DEVICES
// -------------------------------------------------------------
@Composable
fun FamilyAndDevicesTab(
    devices: List<DeviceEntity>,
    activePairingCode: GeneratePairingCodeResponseDto? = null,
    onGeneratePairingCode: (() -> Unit)? = null,
    googleAccountEmail: String? = null,
    onSwitchAccountRequested: () -> Unit = {},
    onChangePinRequested: () -> Unit = {},
    onSignOutAll: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var copiedFeedback by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Family Account Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dual_cloud_firebase_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PurpleAccent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Family Account",
                                    tint = PurpleAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Family Account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = googleAccountEmail?.takeIf { it.isNotBlank() } ?: "Active",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldSafeBg
                        ) {
                            Text(
                                text = "Linked",
                                color = EmeraldSafe,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onSwitchAccountRequested,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("devices_switch_role_button")
                        ) {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch Profile", fontSize = 12.sp)
                        }

                        if (onSignOutAll != null) {
                            OutlinedButton(
                                onClick = onSignOutAll,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralDanger),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("devices_google_signout_button")
                            ) {
                                Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 6-Digit Pairing Code Card (Tap code to copy)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pairing_code_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EmeraldSafe.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = "Pair Child Phone",
                                    tint = EmeraldSafe,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Pair Child Phone",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "6-digit setup code",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                copiedFeedback = false
                                onGeneratePairingCode?.invoke()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSafe),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("generate_pairing_code_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (activePairingCode != null && activePairingCode.pairing_code.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = EmeraldSafeBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        ClipData.newPlainText("FocusSense Pairing Code", activePairingCode.pairing_code)
                                    )
                                    copiedFeedback = true
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activePairingCode.pairing_code.chunked(3).joinToString(" "),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp,
                                    color = EmeraldSafe
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (copiedFeedback) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = EmeraldSafe,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (copiedFeedback) "Copied" else "Copy",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSafe
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Paired Devices
        item {
            Text(
                text = "Paired Devices (${devices.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (devices.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No paired devices yet.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        } else {
            items(devices, key = { it.deviceId }) { dev ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
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
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IndigoPrimary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = IndigoPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = dev.deviceName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldSafe)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Paired",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldSafe
                            )
                        }
                    }
                }
            }
        }

        // Interactive Parent PIN Setting Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onChangePinRequested() }
                    .testTag("change_parent_pin_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IndigoPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Parent PIN",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Parent PIN",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Tap to update 4-digit PIN",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onChangePinRequested,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
