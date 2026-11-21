package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppUsageStatRecord
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.CoralDangerBg
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PurpleAccent

private val ChartPalette = listOf(
    IndigoPrimary,
    PurpleAccent,
    EmeraldSafe,
    AmberWarning,
    CoralDanger,
    Color(0xFF0EA5E9),
    Color(0xFFEC4899)
)

fun formatMinutesLabel(totalMinutes: Long): String {
    val hrs = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m"
        hrs > 0 -> "${hrs}h"
        else -> "${mins}m"
    }
}

@Composable
fun UsageStatsGraphsCard(
    title: String,
    subtitle: String,
    stats: List<AppUsageStatRecord>,
    hasUsagePermission: Boolean,
    showPermissionAction: Boolean,
    onRequestUsagePermission: () -> Unit,
    onRefreshStats: () -> Unit,
    blockedPackages: Set<String> = emptySet(),
    onToggleAppBlock: ((packageName: String, currentlyBlocked: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sortedStats = remember(stats) { stats.sortedByDescending { it.foregroundTimeMs } }
    val totalMinutes = remember(sortedStats) { sortedStats.sumOf { it.totalTimeMinutes.coerceAtLeast(1L) } }

    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }

    val availableCategories = remember(sortedStats) {
        listOf("All") + sortedStats.map { it.category.ifBlank { "General" } }.distinct().take(5)
    }

    val displayedApps = remember(sortedStats, selectedCategory) {
        if (selectedCategory == "All") sortedStats
        else sortedStats.filter { it.category.ifBlank { "General" }.equals(selectedCategory, ignoreCase = true) }
    }
    val maxDisplayedMinutes = (displayedApps.maxOfOrNull { it.totalTimeMinutes } ?: 1L).coerceAtLeast(1L)
    val selectedApp = remember(sortedStats, selectedPkg) {
        sortedStats.find { it.packageName == selectedPkg }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("usage_stats_graphs_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(IndigoPrimary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "Screen Time",
                            tint = IndigoPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshStats,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("usage_stats_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Stats",
                        tint = IndigoPrimary
                    )
                }
            }

            if (showPermissionAction && !hasUsagePermission) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AmberWarning.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Usage Access required",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = onRequestUsagePermission,
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("grant_usage_access_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (sortedStats.isEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No usage recorded yet today",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                return@Card
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Interactive Donut Chart + Clickable Top App Legend
            val topSlices = sortedStats.take(5)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clickable { selectedPkg = null },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val baseStroke = 20.dp.toPx()
                        val diameter = size.minDimension - baseStroke
                        val topLeft = Offset(
                            (size.width - diameter) / 2f,
                            (size.height - diameter) / 2f
                        )
                        val arcSize = Size(diameter, diameter)

                        drawArc(
                            color = Color(0xFFE2E8F0),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = baseStroke)
                        )

                        var startAngle = -90f
                        val gapDegrees = if (topSlices.size > 1) 4f else 0f
                        topSlices.forEachIndexed { idx, item ->
                            val fraction = item.totalTimeMinutes.toFloat() / totalMinutes.toFloat().coerceAtLeast(1f)
                            val sweep = (fraction * 360f - gapDegrees).coerceAtLeast(4f)
                            val isSelected = selectedPkg == item.packageName
                            val sliceAlpha = if (selectedPkg == null || isSelected) 1f else 0.35f
                            drawArc(
                                color = ChartPalette[idx % ChartPalette.size].copy(alpha = sliceAlpha),
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = if (isSelected) baseStroke + 6.dp.toPx() else baseStroke,
                                    cap = StrokeCap.Round
                                )
                            )
                            startAngle += fraction * 360f
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        if (selectedApp != null) {
                            Text(
                                text = formatMinutesLabel(selectedApp.totalTimeMinutes),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = selectedApp.appName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = formatMinutesLabel(totalMinutes),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${sortedStats.size} apps",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    topSlices.forEachIndexed { idx, item ->
                        val pct = ((item.totalTimeMinutes * 100f) / totalMinutes.toFloat().coerceAtLeast(1f)).toInt()
                        val isSelected = selectedPkg == item.packageName
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) ChartPalette[idx % ChartPalette.size].copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    selectedPkg = if (isSelected) null else item.packageName
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ChartPalette[idx % ChartPalette.size])
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.appName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "$pct% · ${formatMinutesLabel(item.totalTimeMinutes)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Interactive Category Filter Chips
            if (availableCategories.size > 2) {
                Spacer(modifier = Modifier.height(14.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(availableCategories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IndigoPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Interactive Per-App Horizontal Bar Rows
            displayedApps.take(10).forEachIndexed { idx, item ->
                val barColor = ChartPalette[idx % ChartPalette.size]
                val targetRatio = (item.totalTimeMinutes.toFloat() / maxDisplayedMinutes.toFloat()).coerceIn(0.06f, 1f)
                val animatedRatio by animateFloatAsState(
                    targetValue = targetRatio,
                    animationSpec = tween(durationMillis = 450),
                    label = "BarWidth_$idx"
                )
                val isBlocked = blockedPackages.contains(item.packageName) || item.isBlocked
                val isSelected = selectedPkg == item.packageName

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isSelected) Modifier.border(
                                1.dp,
                                barColor.copy(alpha = 0.45f),
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .clickable {
                            selectedPkg = if (isSelected) null else item.packageName
                        }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item.appName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${item.launchCount}x",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatMinutesLabel(item.totalTimeMinutes),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = barColor
                            )
                            if (onToggleAppBlock != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isBlocked) CoralDangerBg else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onToggleAppBlock(item.packageName, !isBlocked) }
                                        .testTag("usage_lock_toggle_${item.packageName}")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isBlocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (isBlocked) "Unblock ${item.appName}" else "Block ${item.appName}",
                                            tint = if (isBlocked) CoralDanger else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            } else if (isBlocked) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Blocked",
                                    tint = CoralDanger,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor.copy(alpha = 0.14f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedRatio)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor)
                        )
                    }
                }
            }
        }
    }
}
