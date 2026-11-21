package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.data.remote.GeneratePairingCodeResponseDto
import com.example.data.repository.ActiveAppBlockInfo
import com.example.data.repository.FocusSenseRepository
import com.example.data.repository.SentinelEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FocusSenseViewModel(
    private val repository: FocusSenseRepository
) : ViewModel() {

    // Current User Session
    val currentUser: StateFlow<UserEntity?> = repository.currentUser
    val isSessionReady: StateFlow<Boolean> = repository.isSessionReady
    val selectedChildId: StateFlow<String> = repository.selectedChildId
    val eventStream = repository.eventStream

    // Pomodoro Child Focus Lock
    val pomodoroState = repository.pomodoroState

    fun startPomodoroFocus(blockedPackages: Set<String>, appName: String, durationMinutes: Int) {
        repository.startPomodoro(blockedPackages, appName, durationMinutes)
    }

    fun pausePomodoroFocus() {
        repository.pausePomodoro()
    }

    fun resumePomodoroFocus() {
        repository.resumePomodoro()
    }

    fun stopPomodoroFocus() {
        repository.stopPomodoro()
    }

    val childrenUsers: StateFlow<List<UserEntity>> = repository.childrenUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val parentUsers: StateFlow<List<UserEntity>> = repository.parentUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allUsers: StateFlow<List<UserEntity>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDevices: StateFlow<List<DeviceEntity>> = repository.allDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dynamic child-dependent flows
    val activityLogs: StateFlow<List<ActivityLogEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getLogsForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val flaggedAlerts: StateFlow<List<ActivityLogEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getFlaggedLogsForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scheduleRules: StateFlow<List<ScheduleRuleEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getRulesForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedApps: StateFlow<List<com.example.data.model.InstalledAppEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getInstalledApps(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestLocation: StateFlow<LocationPointEntity?> = selectedChildId
        .flatMapLatest { childId -> repository.getLatestLocationForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val locationHistory: StateFlow<List<LocationPointEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getLocationHistoryForChild(childId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Educational YouTube Channels Whitelist
    val allowedYouTubeChannels: StateFlow<List<com.example.data.model.AllowedYouTubeChannelEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getAllowedYouTubeChannels(childId.ifBlank { "child-default" }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whitelistedYouTubeChannels: StateFlow<List<com.example.data.model.AllowedYouTubeChannelEntity>> = selectedChildId
        .flatMapLatest { childId -> repository.getWhitelistedYouTubeChannels(childId.ifBlank { "child-default" }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleYouTubeChannelAllowed(channelId: String, isAllowed: Boolean) {
        viewModelScope.launch {
            repository.toggleYouTubeChannelAllowed(channelId, isAllowed)
        }
    }

    fun removeYouTubeChannel(channelId: String) {
        viewModelScope.launch {
            repository.removeYouTubeChannel(channelId)
        }
    }

    private val _searchResults = MutableStateFlow<List<com.example.data.remote.YouTubeChannelSearchResult>>(emptyList())
    val searchResults: StateFlow<List<com.example.data.remote.YouTubeChannelSearchResult>> = _searchResults.asStateFlow()

    private val _isSearchingChannels = MutableStateFlow(false)
    val isSearchingChannels: StateFlow<Boolean> = _isSearchingChannels.asStateFlow()

    fun searchChannels(query: String) {
        viewModelScope.launch {
            _isSearchingChannels.value = true
            try {
                val results = com.example.data.remote.YouTubeSearchService.searchChannels(query)
                _searchResults.value = results
            } finally {
                _isSearchingChannels.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun addAllowedChannel(result: com.example.data.remote.YouTubeChannelSearchResult) {
        viewModelScope.launch {
            val childId = selectedChildId.value.ifBlank { "child-default" }
            val newChannel = com.example.data.model.AllowedYouTubeChannelEntity(
                channelId = result.channelId.ifBlank { java.util.UUID.randomUUID().toString() },
                childId = childId,
                channelName = result.title,
                handle = result.handle,
                description = result.description,
                category = "Study & Focus",
                subscriberCount = result.subscriberCount,
                avatarUrl = result.avatarUrl,
                channelUrl = result.channelUrl,
                isAllowed = true
            )
            repository.addYouTubeChannel(newChannel)
        }
    }

    fun addCustomYouTubeChannel(channelName: String, category: String = "Study & Focus", channelUrl: String = "") {
        viewModelScope.launch {
            val childId = selectedChildId.value.ifBlank { "child-default" }
            val cleanName = channelName.trim()
            val handleClean = cleanName.lowercase().replace(" ", "").replace("@", "")
            val finalUrl = if (channelUrl.isNotBlank()) channelUrl else "https://m.youtube.com/@$handleClean"
            val newChannel = com.example.data.model.AllowedYouTubeChannelEntity(
                channelId = java.util.UUID.randomUUID().toString(),
                childId = childId,
                channelName = cleanName,
                handle = "@$handleClean",
                description = "Focus study channel",
                category = category,
                subscriberCount = "Allowed Channel",
                videoCount = "Playlists & Videos",
                avatarUrl = "",
                channelUrl = finalUrl,
                isAllowed = true
            )
            repository.addYouTubeChannel(newChannel)
        }
    }

    private val _activeRestrictionOverlay = MutableStateFlow<ActiveAppBlockInfo?>(null)
    val activeRestrictionOverlay: StateFlow<ActiveAppBlockInfo?> = _activeRestrictionOverlay.asStateFlow()

    // Auth & Onboarding State
    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        viewModelScope.launch {
            repository.childrenUsers.collectLatest { children ->
                if (children.isNotEmpty() && repository.selectedChildId.value.isBlank()) {
                    repository.setSelectedChild(children.first().userId)
                }
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun signUpParent(
        name: String,
        familyName: String,
        email: String,
        password: String,
        pin: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.signUpParent(name, familyName, email, password, pin)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Sign up failed. Please try again." }
            )
        }
    }

    fun signInParent(
        email: String,
        password: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.signInParent(email, password)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Sign in failed. Please try again." }
            )
        }
    }

    fun createChildAccount(
        username: String,
        password: String,
        displayName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.createChildAccount(username, password, displayName)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Failed to create child account." }
            )
        }
    }

    fun pairChildDevice(
        parentEmail: String,
        parentPassword: String,
        childName: String,
        deviceName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.pairChildDevice(parentEmail, parentPassword, childName, deviceName)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Pairing failed. Please check parent credentials." }
            )
        }
    }

    private val _activePairingCode = MutableStateFlow<GeneratePairingCodeResponseDto?>(null)
    val activePairingCode: StateFlow<GeneratePairingCodeResponseDto?> = _activePairingCode.asStateFlow()

    fun generatePairingCode() {
        viewModelScope.launch {
            val result = repository.generatePairingCode()
            result.onSuccess {
                _activePairingCode.value = it
            }.onFailure {
                _authError.value = it.message ?: "Failed to generate pairing code."
            }
        }
    }

    fun loadActivePairingCode() {
        viewModelScope.launch {
            val code = repository.getActivePairingCode()
            _activePairingCode.value = code
        }
    }

    fun pairChildWithCode(
        pairingCode: String,
        childName: String,
        deviceName: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.pairChildWithCode(pairingCode, childName, deviceName)
            _isAuthLoading.value = false
            result.fold(
                onSuccess = { onSuccess() },
                onFailure = { _authError.value = it.message ?: "Invalid or expired pairing code." }
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
        }
    }

    fun switchUser(user: UserEntity) {
        repository.switchUser(user)
        if (user.role == "child") {
            repository.setSelectedChild(user.userId)
        }
    }

    fun selectChild(childId: String) {
        repository.setSelectedChild(childId)
    }

    fun syncData() {
        viewModelScope.launch {
            repository.syncOfflineQueue()
        }
    }

    fun acknowledgeAlert(logId: String) {
        viewModelScope.launch {
            repository.acknowledgeAlert(logId)
        }
    }

    fun removeLog(logId: String) {
        viewModelScope.launch {
            repository.removeVulnerableLog(logId)
        }
    }

    fun addScheduleRule(
        name: String,
        category: String,
        startTime: String,
        endTime: String,
        days: String,
        restrictedPackages: String
    ) {
        viewModelScope.launch {
            val effectiveChildId = selectedChildId.value.ifBlank {
                childrenUsers.value.firstOrNull()?.userId ?: "child-default"
            }
            val rule = ScheduleRuleEntity(
                ruleId = UUID.randomUUID().toString(),
                childId = effectiveChildId,
                ruleName = name,
                category = category,
                startTime = startTime,
                endTime = endTime,
                dayOfWeek = days,
                restrictedPackages = restrictedPackages,
                isActive = true
            )
            repository.addScheduleRule(rule)
        }
    }

    fun updateScheduleRule(rule: ScheduleRuleEntity) {
        viewModelScope.launch {
            repository.updateScheduleRule(rule)
        }
    }

    fun deleteScheduleRule(ruleId: String) {
        viewModelScope.launch {
            repository.deleteScheduleRule(ruleId)
        }
    }

    fun toggleRuleActive(ruleId: String, isActive: Boolean) {
        viewModelScope.launch {
            repository.toggleRuleActive(ruleId, isActive)
        }
    }

    fun refreshInstalledApps(context: android.content.Context) {
        viewModelScope.launch {
            if (currentUser.value?.role == "child") {
                repository.scanAndSyncInstalledApps(context, selectedChildId.value)
            } else {
                repository.fetchRemoteChildData(selectedChildId.value)
            }
        }
    }

    fun requestChildLocation(childId: String = selectedChildId.value) {
        viewModelScope.launch {
            repository.requestChildLocationPing(childId)
        }
    }

    fun checkAndRespondToLocationPing(context: android.content.Context) {
        viewModelScope.launch {
            repository.checkAndRespondToLocationPing(context)
        }
    }

    fun fetchRemoteChildData(childId: String = selectedChildId.value) {
        viewModelScope.launch {
            repository.fetchRemoteChildData(childId)
        }
    }

    fun toggleAppBlock(packageName: String, isBlocked: Boolean) {
        viewModelScope.launch {
            repository.toggleAppBlock(selectedChildId.value, packageName, isBlocked)
        }
    }

    fun syncSchedules() {
        viewModelScope.launch {
            repository.syncSchedulesWithServer(selectedChildId.value)
        }
    }

    fun verifyParentPin(pin: String): Boolean {
        // Parent PIN defaults to "1234"
        val parent = parentUsers.value.firstOrNull()
        val expectedPin = parent?.pin?.takeIf { it.isNotBlank() } ?: "1234"
        return pin == expectedPin
    }

    fun updateParentPin(newPin: String) {
        viewModelScope.launch {
            val parent = parentUsers.value.firstOrNull() ?: return@launch
            repository.updateParentPin(parent.userId, newPin)
        }
    }

    fun dismissRestrictionOverlay() {
        _activeRestrictionOverlay.value = null
    }
}

class FocusSenseViewModelFactory(
    private val repository: FocusSenseRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusSenseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FocusSenseViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
