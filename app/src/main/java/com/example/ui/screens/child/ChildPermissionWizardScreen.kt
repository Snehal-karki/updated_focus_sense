package com.example.ui.screens.child

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.LocationTrackingService
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.EmeraldSafeBg
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PurpleAccent
import com.example.util.ChildPermissionManager
import com.example.util.ChildPermissionStatus

@Composable
fun ChildPermissionWizardScreen(
    childName: String,
    onContinueToDashboard: () -> Unit,
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionStatus = ChildPermissionManager.checkAllPermissions(context)
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            try {
                val intent = Intent(context, LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {}
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionStatus = ChildPermissionManager.checkAllPermissions(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ReadinessProgressCard(
                childName = childName,
                status = permissionStatus,
                onRefresh = { permissionStatus = ChildPermissionManager.checkAllPermissions(context) }
            )
        }

        item {
            PermissionChecklistCard(
                title = "Screen Safety",
                benefit = "Detects unsafe messages and private browsing",
                icon = Icons.Default.Visibility,
                accentColor = IndigoPrimary,
                isGranted = permissionStatus.isAccessibilityEnabled,
                onAction = { ChildPermissionManager.openAccessibilitySettings(context) },
                testTag = "perm_btn_accessibility"
            )
        }

        item {
            PermissionChecklistCard(
                title = "App Usage",
                benefit = "Tracks screen time and daily limits",
                icon = Icons.Default.DataUsage,
                accentColor = PurpleAccent,
                isGranted = permissionStatus.isUsageAccessGranted,
                onAction = { ChildPermissionManager.openUsageAccessSettings(context) },
                testTag = "perm_btn_usage"
            )
        }

        item {
            PermissionChecklistCard(
                title = "Location",
                benefit = "Shares check-in area with parents",
                icon = Icons.Default.LocationOn,
                accentColor = EmeraldSafe,
                isGranted = permissionStatus.isLocationGranted,
                onAction = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                testTag = "perm_btn_location"
            )
        }

        item {
            PermissionChecklistCard(
                title = "Background Run",
                benefit = "Keeps focus rules active reliably",
                icon = Icons.Default.BatteryChargingFull,
                accentColor = AmberWarning,
                isGranted = permissionStatus.isBatteryExempted,
                onAction = { ChildPermissionManager.openBatteryOptimizationSettings(context) },
                testTag = "perm_btn_battery"
            )
        }

        item {
            PermissionChecklistCard(
                title = "Focus Overlay",
                benefit = "Shows shield when an app is paused",
                icon = Icons.Default.Layers,
                accentColor = PurpleAccent,
                isGranted = permissionStatus.isOverlayGranted,
                onAction = { ChildPermissionManager.openOverlaySettings(context) },
                testTag = "perm_btn_overlay"
            )
        }

        item {
            PermissionChecklistCard(
                title = "Notifications",
                benefit = "Focus timer & parent updates",
                icon = Icons.Default.Notifications,
                accentColor = IndigoPrimary,
                isGranted = permissionStatus.isNotificationGranted,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                testTag = "perm_btn_notifications"
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onContinueToDashboard,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (permissionStatus.isFullyProtected) EmeraldSafe else IndigoPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("perm_btn_continue")
            ) {
                Text(
                    text = if (permissionStatus.isFullyProtected) "All Set — Back to Study" else "Continue",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReadinessProgressCard(
    childName: String,
    status: ChildPermissionStatus,
    onRefresh: () -> Unit
) {
    val isComplete = status.isFullyProtected
    val progressColor by animateColorAsState(
        targetValue = when {
            isComplete -> EmeraldSafe
            status.allCrucialGranted -> IndigoPrimary
            else -> AmberWarning
        },
        label = "ReadinessColor"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = status.progressPercent,
        animationSpec = tween(durationMillis = 450),
        label = "ReadinessProgress"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, progressColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
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
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(progressColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isComplete) Icons.Default.CheckCircle else Icons.Default.Shield,
                            contentDescription = "Setup status",
                            tint = progressColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Device Setup",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$childName · ${status.grantedCount}/${status.totalCount} active",
                            fontSize = 12.sp,
                            color = progressColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("perm_btn_refresh")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh status",
                        tint = IndigoPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun PermissionChecklistCard(
    title: String,
    benefit: String,
    icon: ImageVector,
    accentColor: Color,
    isGranted: Boolean,
    onAction: () -> Unit,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isGranted) 1.dp else 2.dp),
        border = BorderStroke(
            1.dp,
            if (isGranted) EmeraldSafe.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .let { mod -> if (!isGranted) mod.clickable { onAction() } else mod }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isGranted) EmeraldSafe.copy(alpha = 0.12f) else accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isGranted) EmeraldSafe else accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = benefit,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isGranted) {
                Surface(
                    color = EmeraldSafeBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Enabled",
                            tint = EmeraldSafe,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "On",
                            color = EmeraldSafe,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(testTag)
                ) {
                    Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
