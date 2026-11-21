package com.example.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.data.model.InstalledAppEntity
import com.example.data.repository.AppUsageEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AppUsageTracker {

    fun todayDateKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun getStartOfTodayMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun collectTodayUsageStats(
        context: Context,
        blockedPackages: Set<String> = emptySet(),
        knownApps: List<InstalledAppEntity> = emptyList()
    ): List<AppUsageEntry> {
        if (!ChildPermissionManager.isUsageAccessGranted(context)) {
            return emptyList()
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val pm = context.packageManager
        val startMs = getStartOfTodayMillis()
        val endMs = System.currentTimeMillis()
        if (endMs <= startMs) return emptyList()

        // 1. Aggregate foreground time accurately from UsageEvents for today
        val foregroundDurations = mutableMapOf<String, Long>()
        val launchCounts = mutableMapOf<String, Int>()
        val activeStartByPkg = mutableMapOf<String, Long>()

        try {
            val events = usm.queryEvents(startMs, endMs)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        activeStartByPkg[pkg] = event.timeStamp
                        launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val started = activeStartByPkg.remove(pkg)
                        if (started != null && event.timeStamp > started) {
                            val delta = (event.timeStamp - started).coerceAtMost(8 * 3600_000L)
                            foregroundDurations[pkg] = (foregroundDurations[pkg] ?: 0L) + delta
                        }
                    }
                }
            }
            // Close any app currently in foreground up to now
            for ((pkg, started) in activeStartByPkg) {
                if (endMs > started) {
                    val delta = (endMs - started).coerceAtMost(4 * 3600_000L)
                    foregroundDurations[pkg] = (foregroundDurations[pkg] ?: 0L) + delta
                }
            }
        } catch (_: Exception) {
        }

        // 2. Supplement with queryAndAggregateUsageStats if events returned empty on certain OEMs
        if (foregroundDurations.isEmpty()) {
            try {
                val statsMap = usm.queryAndAggregateUsageStats(startMs, endMs)
                for ((pkg, stat) in statsMap) {
                    val fgMs = stat.totalTimeInForeground
                    if (fgMs > 0L) {
                        foregroundDurations[pkg] = maxOf(foregroundDurations[pkg] ?: 0L, fgMs)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Final fallback: queryUsageStats(INTERVAL_DAILY) for devices that restrict event streams
        if (foregroundDurations.isEmpty()) {
            try {
                val dailyList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
                dailyList?.forEach { stat ->
                    val fgMs = stat.totalTimeInForeground
                    if (fgMs > 0L) {
                        foregroundDurations[stat.packageName] =
                            maxOf(foregroundDurations[stat.packageName] ?: 0L, fgMs)
                    }
                }
            } catch (_: Exception) {
            }
        }

        val knownMap = knownApps.associateBy { it.packageName }
        val entries = mutableListOf<AppUsageEntry>()

        for ((pkg, rawMs) in foregroundDurations) {
            if (pkg == "com.android.systemui" || pkg == "android") continue
            val isLaunchable = try {
                pm.getLaunchIntentForPackage(pkg) != null || knownMap.containsKey(pkg)
            } catch (_: Exception) {
                false
            }
            if (!isLaunchable && rawMs < 60_000L) continue

            val appName = knownMap[pkg]?.appName ?: try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast('.')
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }

            val minutes = ((rawMs + 30_000L) / 60_000L).toInt().coerceAtLeast(
                if (rawMs >= 5_000L) 1 else 0
            )
            if (minutes == 0 && rawMs < 5_000L) continue

            entries.add(
                AppUsageEntry(
                    packageName = pkg,
                    appName = appName,
                    category = knownMap[pkg]?.category ?: InstalledAppScanner.categorizeApp(pkg, appName),
                    foregroundTimeMs = rawMs,
                    totalTimeMinutes = minutes,
                    launchCount = (launchCounts[pkg] ?: 1).coerceAtLeast(1),
                    isBlocked = blockedPackages.contains(pkg)
                )
            )
        }

        return entries.sortedByDescending { it.foregroundTimeMs }
    }
}
