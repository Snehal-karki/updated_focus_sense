package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppUsageStatRecord
import com.example.data.model.InstalledAppEntity
import com.example.data.model.UserEntity
import com.example.data.remote.SupabaseClient
import com.example.data.repository.FocusSenseRepository
import com.example.util.AppUsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class LiveScreenAndStatsViewModel(
    private val focusRepo: FocusSenseRepository
) : ViewModel() {

    private val _isSyncingUsage = MutableStateFlow(false)
    val isSyncingUsage: StateFlow<Boolean> = _isSyncingUsage.asStateFlow()

    private val _localMyUsageStats = MutableStateFlow<List<AppUsageStatRecord>>(emptyList())
    private val _usageRefreshTrigger = MutableStateFlow(0L)

    private val activeTargetChildId: StateFlow<String> = combine(
        focusRepo.currentUser,
        focusRepo.selectedChildId
    ) { user, selectedChild ->
        when {
            user == null -> ""
            user.role == "child" -> user.userId
            else -> selectedChild
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    /**
     * Authoritative Child Daily Usage Stats pulled directly from Supabase table `fs_app_usage_stats`.
     */
    val childUsageStats: StateFlow<List<AppUsageStatRecord>> = combine(
        activeTargetChildId,
        _usageRefreshTrigger
    ) { childId, refreshTick -> childId to refreshTick }
        .flatMapLatest { (childId, _) ->
            if (childId.isBlank()) {
                flowOf(emptyList())
            } else {
                flow {
                    while (currentCoroutineContext().isActive) {
                        val todayKey = AppUsageTracker.todayDateKey()
                        val remote = withContext(Dispatchers.IO) {
                            SupabaseClient.fetchAppUsageStats(childId, todayKey)
                        }
                        if (remote.isNotEmpty()) {
                            emit(remote)
                        } else if (focusRepo.currentUser.value?.role == "child" &&
                            focusRepo.currentUser.value?.userId == childId &&
                            _localMyUsageStats.value.isNotEmpty()
                        ) {
                            emit(_localMyUsageStats.value)
                        } else {
                            emit(emptyList())
                        }
                        delay(20_000L)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /**
     * Current device's own usage stats (Child pushes to Supabase; Parent displays locally).
     */
    val myUsageStats: StateFlow<List<AppUsageStatRecord>> = combine(
        focusRepo.currentUser,
        _localMyUsageStats,
        childUsageStats
    ) { user, localStats, syncedChildStats ->
        when {
            user == null -> emptyList()
            localStats.isNotEmpty() -> localStats
            user.role == "child" -> syncedChildStats
            else -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun refreshChildUsageStats(childId: String = activeTargetChildId.value) {
        if (childId.isBlank()) return
        _usageRefreshTrigger.value = System.currentTimeMillis()
    }

    fun collectAndSyncDeviceUsageStats(
        context: Context,
        user: UserEntity,
        installedApps: List<InstalledAppEntity>,
        blockedPackages: Set<String>
    ) {
        viewModelScope.launch {
            _isSyncingUsage.value = true
            try {
                val appContext = context.applicationContext
                val dateKey = AppUsageTracker.todayDateKey()
                val entries = withContext(Dispatchers.IO) {
                    AppUsageTracker.collectTodayUsageStats(
                        context = appContext,
                        blockedPackages = blockedPackages,
                        knownApps = installedApps
                    )
                }

                if (entries.isNotEmpty()) {
                    _localMyUsageStats.value = entries.map { entry ->
                        AppUsageStatRecord(
                            id = "${user.userId}_${dateKey}_${entry.packageName}",
                            subjectUserId = user.userId,
                            role = user.role,
                            dateKey = dateKey,
                            packageName = entry.packageName,
                            appName = entry.appName,
                            category = entry.category,
                            totalTimeMinutes = entry.totalTimeMinutes.toLong(),
                            foregroundTimeMs = entry.foregroundTimeMs,
                            launchCount = entry.launchCount.toLong(),
                            isBlocked = entry.isBlocked
                        )
                    }
                }

                // Only Child device writes usage records into Supabase `fs_app_usage_stats`
                if (user.role == "child" && entries.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        SupabaseClient.syncAppUsageStats(
                            childId = user.userId,
                            dateKey = dateKey,
                            entries = entries
                        )
                    }
                    _usageRefreshTrigger.value = System.currentTimeMillis()
                } else if (user.role == "parent") {
                    _usageRefreshTrigger.value = System.currentTimeMillis()
                }
            } finally {
                _isSyncingUsage.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
