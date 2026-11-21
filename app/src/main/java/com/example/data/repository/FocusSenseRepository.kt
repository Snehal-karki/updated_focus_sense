package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.ai.ThreatAnalysisResult
import com.example.ai.ThreatEvaluationEngine
import com.example.data.local.FocusSenseDatabase
import com.example.data.local.SecurePreferences
import com.example.data.model.ActivityLogEntity
import com.example.data.model.DeviceEntity
import com.example.data.model.FamilyGroupEntity
import com.example.data.model.InstalledAppEntity
import com.example.data.model.LocationPointEntity
import com.example.data.model.ScheduleRuleEntity
import com.example.data.model.UserEntity
import com.example.data.remote.ApiClient
import com.example.data.remote.ClaimPairingCodeDto
import com.example.data.remote.GeneratePairingCodeResponseDto
import com.example.data.remote.PairChildResponseDto
import com.example.data.remote.SupabaseClient
import com.example.data.remote.SyncRequest
import com.example.util.InstalledAppScanner
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class SentinelEvent {
    data class ContentFlagged(val log: ActivityLogEntity, val threatResult: ThreatAnalysisResult) : SentinelEvent()
    data class AppRestricted(val appName: String, val ruleName: String, val endTime: String) : SentinelEvent()
    data class LocationUpdated(val locationName: String, val lat: Double, val lng: Double) : SentinelEvent()
    data class SyncCompleted(val syncedCount: Int) : SentinelEvent()
}

data class ActiveAppBlockInfo(
    val isBlocked: Boolean,
    val ruleName: String = "",
    val category: String = "",
    val endTime: String = "",
    val restrictedAppName: String = ""
)

class FocusSenseRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = FocusSenseDatabase.getInstance(context)
    private val threatEngine = ThreatEvaluationEngine()
    private val scope = CoroutineScope(Dispatchers.IO)

    // Current Session State
    private val prefs = context.getSharedPreferences("focussense_session_prefs", Context.MODE_PRIVATE)
    private val securePrefs = com.example.data.local.SecurePreferences(context)
    val deviceId: String get() = securePrefs.deviceId
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isSessionReady = MutableStateFlow<Boolean>(false)
    val isSessionReady: StateFlow<Boolean> = _isSessionReady.asStateFlow()

    private val _selectedChildId = MutableStateFlow<String>("")
    val selectedChildId: StateFlow<String> = _selectedChildId.asStateFlow()

    // Network & Zero Data-Loss Sync State
    private val _isNetworkConnected = MutableStateFlow<Boolean>(true)
    val isNetworkConnected: StateFlow<Boolean> = _isNetworkConnected.asStateFlow()

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Central Server & Database URL (FastAPI on Render + Supabase PostgreSQL)
    private val defaultBackendUrl = "https://snehal-karki-2.onrender.com"
    private val _serverUrl = MutableStateFlow<String>(
        run {
            val saved = prefs.getString("server_api_url", null)?.trim()
            if (saved.isNullOrBlank() ||
                saved == "https://ais-dev-oopvudz6wyelc5jkmrnerc-562689220446.asia-east1.run.app" ||
                saved == "https://parental-controll.onrender.com"
            ) {
                prefs.edit().putString("server_api_url", defaultBackendUrl).apply()
                defaultBackendUrl
            } else {
                saved
            }
        }
    )
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    // Real-Time Push / Sentinel Event Stream
    private val _eventStream = MutableSharedFlow<SentinelEvent>(extraBufferCapacity = 64)
    val eventStream: SharedFlow<SentinelEvent> = _eventStream.asSharedFlow()

    fun updateServerUrl(url: String) {
        val cleanUrl = url.trim()
        _serverUrl.value = cleanUrl
        prefs.edit().putString("server_api_url", cleanUrl).apply()
    }

    suspend fun testServerConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        com.example.data.remote.SupabaseClient.ping()
    }

    // In-Memory App Blocking Cache for 0ms Latency in AccessibilityService
    private val _instantBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val instantBlockedPackages: StateFlow<Set<String>> = _instantBlockedPackages.asStateFlow()

    private val _temporaryUnlockedPackages = ConcurrentHashMap<String, Long>()

    // Pomodoro Child Focus Lock State (supports single/multi-app selection or "*" for all distracting apps)
    data class PomodoroState(
        val blockedPackages: Set<String> = emptySet(),
        val appName: String = "",
        val durationMinutes: Int = 25,
        val remainingSeconds: Long = 0L,
        val isRunning: Boolean = false
    ) {
        val isBlockingAll: Boolean
            get() = blockedPackages.any { it == "*" || it.equals("all", ignoreCase = true) }

        val packageName: String
            get() = blockedPackages.firstOrNull() ?: ""
    }

    private val _pomodoroState = MutableStateFlow<PomodoroState?>(null)
    val pomodoroState: StateFlow<PomodoroState?> = _pomodoroState.asStateFlow()

    private var pomodoroJob: Job? = null

    fun startPomodoro(packageName: String, appName: String, durationMinutes: Int) {
        startPomodoro(setOf(packageName), appName, durationMinutes)
    }

    fun startPomodoro(blockedPackages: Set<String>, appName: String, durationMinutes: Int) {
        val normalizedPkgs = blockedPackages.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        if (normalizedPkgs.isEmpty()) return

        pomodoroJob?.cancel()
        val totalSecs = (durationMinutes * 60).toLong().coerceAtLeast(60L)
        _pomodoroState.value = PomodoroState(
            blockedPackages = normalizedPkgs,
            appName = appName,
            durationMinutes = durationMinutes,
            remainingSeconds = totalSecs,
            isRunning = true
        )

        pomodoroJob = CoroutineScope(Dispatchers.IO).launch {
            var currentSecs = totalSecs
            while (currentSecs > 0 && _pomodoroState.value?.isRunning == true) {
                delay(1000)
                currentSecs--
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = currentSecs)
            }
            if (currentSecs <= 0) {
                _pomodoroState.value = null
            }
        }
    }

    fun pausePomodoro() {
        _pomodoroState.value = _pomodoroState.value?.copy(isRunning = false)
        pomodoroJob?.cancel()
    }

    fun resumePomodoro() {
        val current = _pomodoroState.value ?: return
        if (current.remainingSeconds <= 0) {
            _pomodoroState.value = null
            return
        }
        _pomodoroState.value = current.copy(isRunning = true)

        pomodoroJob?.cancel()
        pomodoroJob = CoroutineScope(Dispatchers.IO).launch {
            var currentSecs = current.remainingSeconds
            while (currentSecs > 0 && _pomodoroState.value?.isRunning == true) {
                delay(1000)
                currentSecs--
                _pomodoroState.value = _pomodoroState.value?.copy(remainingSeconds = currentSecs)
            }
            if (currentSecs <= 0) {
                _pomodoroState.value = null
            }
        }
    }

    fun stopPomodoro() {
        pomodoroJob?.cancel()
        _pomodoroState.value = null
    }

    fun isPackageBlockedSync(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val tempExpiry = _temporaryUnlockedPackages[lower] ?: 0L
        if (tempExpiry > System.currentTimeMillis()) {
            return false
        }
        return _instantBlockedPackages.value.any { it.equals(lower, ignoreCase = true) }
    }

    fun temporarilyUnlockApp(packageName: String, minutes: Int = 15) {
        val lower = packageName.lowercase()
        _temporaryUnlockedPackages[lower] = System.currentTimeMillis() + (minutes * 60 * 1000L)
    }

    fun isPackageTemporarilyUnlocked(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val tempExpiry = _temporaryUnlockedPackages[lower] ?: 0L
        return tempExpiry > System.currentTimeMillis()
    }

    init {
        // Wire secure preferences to API client for JWT and device ID header injection
        ApiClient.setSecurePreferences(securePrefs)

        // Restore active user session from local preferences
        scope.launch {
            val savedUserId = prefs.getString("saved_user_id", null)
            var activeUser: UserEntity? = null
            if (!savedUserId.isNullOrBlank()) {
                activeUser = db.userDao().getUserByIdSync(savedUserId)
            }

            if (activeUser != null) {
                _currentUser.value = activeUser
                if (activeUser.role == "child") {
                    _selectedChildId.value = activeUser.userId
                } else {
                    val children = db.userDao().getUsersByRoleSync("child")
                    if (children.isNotEmpty()) {
                        _selectedChildId.value = children.first().userId
                    }
                }
            }

            // Initialize in-memory blocked apps cache from DB
            try {
                val blocked = db.installedAppDao().getBlockedPackageNamesSync()
                _instantBlockedPackages.value = blocked.map { it.lowercase() }.toSet()
            } catch (_: Exception) {}

            _isSessionReady.value = true
        }
    }

    suspend fun launchDemoParent() = withContext(Dispatchers.IO) {
        val parent = db.userDao().getUsersByRoleSync("parent").firstOrNull()
        if (parent != null) {
            _currentUser.value = parent
            prefs.edit().putString("saved_user_id", parent.userId).apply()
            securePrefs.userId = parent.userId
            securePrefs.userRole = "parent"
            securePrefs.familyId = parent.groupId
            val children = db.userDao().getUsersByRoleSync("child")
            if (children.isNotEmpty()) {
                _selectedChildId.value = children.first().userId
            }
        }
    }

    suspend fun launchDemoChild() = withContext(Dispatchers.IO) {
        val child = db.userDao().getUsersByRoleSync("child").firstOrNull()
        if (child != null) {
            _currentUser.value = child
            prefs.edit().putString("saved_user_id", child.userId).apply()
            securePrefs.userId = child.userId
            securePrefs.userRole = "child"
            securePrefs.familyId = child.groupId
            _selectedChildId.value = child.userId
        }
    }

    // Role & User Authentication Methods
    suspend fun signUpParent(
        name: String,
        familyName: String,
        email: String,
        password: String,
        pin: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()

            // 1. Direct register to Supabase PostgreSQL (mukkrkppvfxnbbxozvwj)
            val supabaseResult = com.example.data.remote.SupabaseClient.registerParent(
                name = name,
                familyName = familyName,
                email = normalizedEmail,
                passwordHash = password,
                deviceId = securePrefs.deviceId
            )

            if (supabaseResult.isSuccess) {
                val record = supabaseResult.getOrThrow()
                val group = FamilyGroupEntity(
                    groupId = record.familyId ?: UUID.randomUUID().toString(),
                    familyName = if (familyName.isNotBlank()) familyName.trim() else "${name.trim()}'s Family"
                )
                db.familyGroupDao().insert(group)

                val parent = UserEntity(
                    userId = record.id,
                    groupId = group.groupId,
                    email = record.email,
                    password = password,
                    role = "parent",
                    name = record.displayName,
                    pin = pin
                )
                db.userDao().insert(parent)

                val parentDevice = DeviceEntity(
                    deviceId = com.example.data.remote.SupabaseClient.toUuid(securePrefs.deviceId),
                    userId = parent.userId,
                    deviceName = "${name.trim()}'s Parent Phone",
                    batteryPercent = 100,
                    isOnline = true
                )
                db.deviceDao().insert(parentDevice)

                securePrefs.userId = parent.userId
                securePrefs.userRole = "parent"
                securePrefs.familyId = parent.groupId
                prefs.edit().putString("saved_user_id", parent.userId).apply()
                _currentUser.value = parent
                return@withContext Result.success(parent)
            } else {
                val err = supabaseResult.exceptionOrNull()
                Log.e("FocusSenseRepo", "Supabase register failed: ${err?.message}", err)
                return@withContext Result.failure(err ?: Exception("Registration failed on Supabase."))
            }

            // 2. Offline / Local Fallback
            val existing = db.userDao().getUserByEmail(normalizedEmail)
            if (existing != null) {
                return@withContext Result.failure(Exception("An account with email '$normalizedEmail' already exists. Please sign in."))
            }

            val group = FamilyGroupEntity(
                groupId = "group-${UUID.randomUUID().toString().take(8)}",
                familyName = if (familyName.isNotBlank()) familyName.trim() else "${name.trim()}'s Family"
            )
            db.familyGroupDao().insert(group)

            val parentUser = UserEntity(
                userId = "parent-${UUID.randomUUID().toString().take(8)}",
                groupId = group.groupId,
                email = normalizedEmail,
                password = password,
                role = "parent",
                name = name.trim(),
                pin = if (pin.isNotBlank()) pin.trim() else "1234"
            )
            db.userDao().insert(parentUser)

            val parentDevice = DeviceEntity(
                deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                userId = parentUser.userId,
                deviceName = "${name.trim()}'s Parent Phone",
                batteryPercent = 100,
                isOnline = true
            )
            db.deviceDao().insert(parentDevice)

            // Persist session
            prefs.edit().putString("saved_user_id", parentUser.userId).apply()
            _currentUser.value = parentUser
            Result.success(parentUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signInParent(email: String, password: String): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedEmail = email.trim().lowercase()

            // 1. Attempt login with Supabase
            val supabaseResult = com.example.data.remote.SupabaseClient.loginParent(
                email = normalizedEmail,
                passwordExpected = password
            )
            if (supabaseResult.isSuccess) {
                val record = supabaseResult.getOrThrow()
                val familyId = record.familyId ?: UUID.randomUUID().toString()
                val group = FamilyGroupEntity(
                    groupId = familyId,
                    familyName = "My Family"
                )
                db.familyGroupDao().insert(group)

                val parent = UserEntity(
                    userId = record.id,
                    groupId = familyId,
                    email = record.email,
                    password = password,
                    role = "parent",
                    name = record.displayName,
                    pin = "1234"
                )
                db.userDao().insert(parent)

                // Sync all children & their devices of this family from Supabase
                val children = com.example.data.remote.SupabaseClient.fetchFamilyChildren(familyId)
                for (c in children) {
                    val childEntity = UserEntity(
                        userId = c.id,
                        groupId = familyId,
                        email = c.email,
                        password = password,
                        role = "child",
                        name = c.displayName,
                        pin = ""
                    )
                    db.userDao().insert(childEntity)
                    com.example.data.remote.SupabaseClient.fetchDevicesForUser(c.id).forEach { dev ->
                        db.deviceDao().insert(dev)
                    }
                }
                if (children.isNotEmpty()) {
                    _selectedChildId.value = children.first().id
                }

                // Register this Parent device in Supabase fs_devices
                com.example.data.remote.SupabaseClient.ensureDeviceRegistered(
                    deviceId = securePrefs.deviceId,
                    userId = parent.userId,
                    deviceName = "${parent.name}'s Parent Phone"
                )

                securePrefs.userId = parent.userId
                securePrefs.userRole = "parent"
                securePrefs.familyId = parent.groupId
                prefs.edit().putString("saved_user_id", parent.userId).apply()
                _currentUser.value = parent
                return@withContext Result.success(parent)
            }

            // 2. Offline / Local fallback check
            val user = db.userDao().getUserByEmail(normalizedEmail)
            if (user == null) {
                val err = supabaseResult.exceptionOrNull()
                return@withContext Result.failure(err ?: Exception("No account found for '$normalizedEmail'. Please verify credentials or create an account."))
            }
            if (user.password != password) {
                return@withContext Result.failure(Exception("Incorrect password. Please verify and try again."))
            }

            prefs.edit().putString("saved_user_id", user.userId).apply()
            _currentUser.value = user
            if (user.role == "child") {
                _selectedChildId.value = user.userId
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createChildAccount(
        username: String,
        password: String,
        displayName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val parent = _currentUser.value ?: return@withContext Result.failure(Exception("Not logged in as parent."))
            val cleanUsername = username.trim().lowercase()

            // 1. Direct creation in Supabase fs_users
            val supabaseResult = com.example.data.remote.SupabaseClient.createChildAccount(
                username = cleanUsername,
                displayName = displayName,
                familyId = parent.groupId,
                parentId = parent.userId,
                passwordHash = password
            )

            val childId = if (supabaseResult.isSuccess) {
                supabaseResult.getOrThrow().id
            } else {
                Log.w("FocusSenseRepo", "Supabase child creation failed: ${supabaseResult.exceptionOrNull()?.message}")
                UUID.randomUUID().toString()
            }

            val newChild = UserEntity(
                userId = childId,
                groupId = parent.groupId,
                email = "$cleanUsername@focussense.local",
                password = password,
                role = "child",
                name = displayName.trim(),
                pin = ""
            )
            db.userDao().insert(newChild)
            if (_selectedChildId.value.isBlank()) {
                _selectedChildId.value = childId
            }
            Result.success(newChild)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pairChildDevice(
        parentEmail: String,
        parentPassword: String,
        childName: String,
        deviceName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val normalizedParentEmail = parentEmail.trim().lowercase()

            // 1. Direct Supabase Device Pairing
            val supabasePairResult = com.example.data.remote.SupabaseClient.pairChildDevice(
                parentEmail = normalizedParentEmail,
                parentPasswordExpected = parentPassword,
                childName = childName,
                deviceName = deviceName,
                deviceId = securePrefs.deviceId
            )

            if (supabasePairResult.isSuccess) {
                val (childRecord, deviceId) = supabasePairResult.getOrThrow()
                val familyId = childRecord.familyId ?: UUID.randomUUID().toString()

                val group = FamilyGroupEntity(
                    groupId = familyId,
                    familyName = "${childName.trim()}'s Family"
                )
                db.familyGroupDao().insert(group)

                val childUser = UserEntity(
                    userId = childRecord.id,
                    groupId = familyId,
                    email = childRecord.email,
                    password = parentPassword,
                    role = "child",
                    name = childRecord.displayName,
                    pin = ""
                )
                db.userDao().insert(childUser)

                val childDevice = DeviceEntity(
                    deviceId = deviceId,
                    userId = childUser.userId,
                    deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Phone",
                    batteryPercent = 95,
                    isOnline = true
                )
                db.deviceDao().insert(childDevice)

                securePrefs.userId = childUser.userId
                securePrefs.userRole = "child"
                securePrefs.familyId = childUser.groupId
                prefs.edit().putString("saved_user_id", childUser.userId).apply()
                _currentUser.value = childUser
                _selectedChildId.value = childUser.userId
                return@withContext Result.success(childUser)
            } else {
                Log.w("FocusSenseRepo", "Supabase pair failed: ${supabasePairResult.exceptionOrNull()?.message}")
            }

            // 2. Offline Local Creation Fallback
            var parentUser = db.userDao().getUserByEmail(normalizedParentEmail)
            val groupId = if (parentUser != null) {
                if (parentUser.password != parentPassword) {
                    return@withContext Result.failure(Exception("Parent password incorrect. Cannot link device."))
                }
                parentUser.groupId
            } else {
                val newGroupId = "group-${UUID.randomUUID().toString().take(8)}"
                val newGroup = FamilyGroupEntity(
                    groupId = newGroupId,
                    familyName = "${childName.trim()}'s Family"
                )
                db.familyGroupDao().insert(newGroup)

                val newParent = UserEntity(
                    userId = "parent-${UUID.randomUUID().toString().take(8)}",
                    groupId = newGroupId,
                    email = normalizedParentEmail,
                    password = parentPassword,
                    role = "parent",
                    name = "Parent ($normalizedParentEmail)",
                    pin = "1234"
                )
                db.userDao().insert(newParent)
                newGroupId
            }

            val childId = "child-${UUID.randomUUID().toString().take(8)}"
            val childUser = UserEntity(
                userId = childId,
                groupId = groupId,
                email = "${childName.trim().lowercase().replace(" ", "")}@family.focussense",
                password = parentPassword,
                role = "child",
                name = childName.trim(),
                pin = ""
            )
            db.userDao().insert(childUser)

            val childDevice = DeviceEntity(
                deviceId = "dev-${UUID.randomUUID().toString().take(8)}",
                userId = childId,
                deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Device",
                batteryPercent = 95,
                isOnline = true
            )
            db.deviceDao().insert(childDevice)

            // Persist session as child device
            prefs.edit().putString("saved_user_id", childId).apply()
            _currentUser.value = childUser
            _selectedChildId.value = childId

            Result.success(childUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        prefs.edit().remove("saved_user_id").apply()
        securePrefs.clearSession()
        _currentUser.value = null
        _selectedChildId.value = ""
    }

    suspend fun generatePairingCode(): Result<GeneratePairingCodeResponseDto> = withContext(Dispatchers.IO) {
        val parent = _currentUser.value
        if (parent == null || parent.role != "parent") {
            return@withContext Result.failure(Exception("Must be authenticated as Parent to generate pairing codes."))
        }
        val codeRes = com.example.data.remote.SupabaseClient.generatePairingCode(parent.groupId)
        if (codeRes.isSuccess) {
            Result.success(
                GeneratePairingCodeResponseDto(
                    status = "success",
                    pairing_code = codeRes.getOrThrow(),
                    expires_at = System.currentTimeMillis() + 86_400_000L,
                    group_id = parent.groupId
                )
            )
        } else {
            Result.failure(codeRes.exceptionOrNull() ?: Exception("Failed to generate pairing code."))
        }
    }

    suspend fun getActivePairingCode(): GeneratePairingCodeResponseDto? = withContext(Dispatchers.IO) {
        val parent = _currentUser.value ?: return@withContext null
        if (parent.role != "parent") return@withContext null
        val code = com.example.data.remote.SupabaseClient.getActivePairingCode(parent.groupId) ?: return@withContext null
        GeneratePairingCodeResponseDto(
            status = "success",
            pairing_code = code,
            expires_at = System.currentTimeMillis() + 86_400_000L,
            group_id = parent.groupId
        )
    }

    suspend fun pairChildWithCode(
        pairingCode: String,
        childName: String,
        deviceName: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val cleanCode = pairingCode.trim().uppercase()
            val supabasePairRes = com.example.data.remote.SupabaseClient.pairChildWithCode(
                pairingCode = cleanCode,
                childName = childName,
                deviceId = securePrefs.deviceId,
                deviceName = deviceName
            )
            if (supabasePairRes.isFailure) {
                return@withContext Result.failure(
                    supabasePairRes.exceptionOrNull()
                        ?: Exception("Invalid or expired pairing code. Please generate a fresh code on the Parent device.")
                )
            }

            val (childRecord, deviceId) = supabasePairRes.getOrThrow()
            val familyId = childRecord.familyId ?: UUID.randomUUID().toString()

            securePrefs.userId = childRecord.id
            securePrefs.userRole = "child"
            securePrefs.familyId = familyId

            val group = FamilyGroupEntity(
                groupId = familyId,
                familyName = "${childName.trim()}'s Family"
            )
            db.familyGroupDao().insert(group)

            val childUser = UserEntity(
                userId = childRecord.id,
                groupId = familyId,
                email = childRecord.email,
                password = "",
                role = "child",
                name = childRecord.displayName,
                pin = ""
            )
            db.userDao().insert(childUser)

            val childDevice = DeviceEntity(
                deviceId = deviceId,
                userId = childRecord.id,
                deviceName = if (deviceName.isNotBlank()) deviceName.trim() else "${childName.trim()}'s Phone",
                batteryPercent = 95,
                isOnline = true
            )
            db.deviceDao().insert(childDevice)

            prefs.edit().putString("saved_user_id", childRecord.id).apply()
            _currentUser.value = childUser
            _selectedChildId.value = childRecord.id

            Result.success(childUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Role & User Switching
    fun switchUser(user: UserEntity) {
        prefs.edit().putString("saved_user_id", user.userId).apply()
        securePrefs.userId = user.userId
        securePrefs.userRole = user.role
        securePrefs.familyId = user.groupId
        _currentUser.value = user
    }

    fun setSelectedChild(childId: String) {
        _selectedChildId.value = childId
    }

    fun toggleNetworkSimulation(connected: Boolean) {
        _isNetworkConnected.value = connected
    }

    // Database Flows
    val familyGroup: Flow<FamilyGroupEntity?> = db.familyGroupDao().getFamilyGroup()
    val allUsers: Flow<List<UserEntity>> = db.userDao().getAllUsers()
    val childrenUsers: Flow<List<UserEntity>> = db.userDao().getUsersByRole("child")
    val parentUsers: Flow<List<UserEntity>> = db.userDao().getUsersByRole("parent")
    val allDevices: Flow<List<DeviceEntity>> = db.deviceDao().getAllDevices()

    fun getDevicesForUser(userId: String): Flow<List<DeviceEntity>> =
        db.deviceDao().getDevicesForUser(userId)

    val allLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getAllLogs()
    val flaggedLogs: Flow<List<ActivityLogEntity>> = db.activityLogDao().getFlaggedLogs()

    fun getLogsForChild(childId: String): Flow<List<ActivityLogEntity>> =
        db.activityLogDao().getLogsForChild(childId)

    fun getFlaggedLogsForChild(childId: String): Flow<List<ActivityLogEntity>> =
        db.activityLogDao().getFlaggedLogsForChild(childId)

    val allScheduleRules: Flow<List<ScheduleRuleEntity>> = db.scheduleRuleDao().getAllRules()

    fun getRulesForChild(childId: String): Flow<List<ScheduleRuleEntity>> =
        db.scheduleRuleDao().getRulesForChild(childId)

    fun getLatestLocationForChild(childId: String): Flow<LocationPointEntity?> =
        db.locationDao().getLatestLocationForChild(childId)

    fun getLocationHistoryForChild(childId: String): Flow<List<LocationPointEntity>> =
        db.locationDao().getLocationHistoryForChild(childId)

    val unsyncedLogsCount: Flow<Int> = db.activityLogDao().getUnsyncedCount()
    val unsyncedLocationCount: Flow<Int> = db.locationDao().getUnsyncedCount()

    // 1. Accessibility Content Processing & AI Evaluation (Child device only)
    suspend fun processExtractedContent(
        childId: String,
        packageName: String,
        appName: String,
        contentTitle: String,
        extractedText: String,
        forceOffline: Boolean = false
    ): ActivityLogEntity = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        if (user == null || user.role != "child") {
            // Parent devices never extract content or upload child activity
            return@withContext ActivityLogEntity(
                logId = UUID.randomUUID().toString(),
                childId = "",
                packageName = packageName,
                appName = appName,
                contentTitle = contentTitle,
                extractedText = extractedText,
                isFlagged = false,
                threatCategory = "Safe",
                confidenceScore = 0f,
                aiAnalysisSummary = "Ignored on parent device",
                recordedAt = System.currentTimeMillis(),
                isSynced = false,
                isAcknowledged = true
            )
        }
        val actualChildId = user.userId

        val analysis = threatEngine.evaluateContent(
            appName = appName,
            contentTitle = contentTitle,
            extractedText = extractedText,
            serverUrl = _serverUrl.value,
            forceOfflineOnly = forceOffline || !_isNetworkConnected.value
        )

        val log = ActivityLogEntity(
            logId = UUID.randomUUID().toString(),
            childId = actualChildId,
            packageName = packageName,
            appName = appName,
            contentTitle = contentTitle,
            extractedText = extractedText,
            isFlagged = analysis.isFlagged,
            threatCategory = analysis.threatCategory,
            confidenceScore = analysis.confidenceScore,
            aiAnalysisSummary = analysis.aiAnalysisSummary,
            recordedAt = System.currentTimeMillis(),
            isSynced = _isNetworkConnected.value, // Auto-synced if online, queued if offline
            isAcknowledged = false
        )

        // Zero Data-Loss Guarantee: Insert into local Room SQLite DB immediately
        db.activityLogDao().insert(log)

        // Only auto-lock the app if CRITICAL instant intervention is warranted (not on minor/medium flags)
        if (analysis.isFlagged &&
            analysis.severityLevel == "CRITICAL" &&
            analysis.recommendedAction == "INSTANT_BLOCK" &&
            !com.example.service.ScraperFilterEngine.isIgnoredPackage(packageName, "com.example")
        ) {
            toggleAppBlock(actualChildId, packageName, true)
        }

        // Live Real-Time Cloud Sync: push directly to Supabase so Parent device gets notified
        if (_isNetworkConnected.value) {
            val pushed = com.example.data.remote.SupabaseClient.pushActivityLog(log, actualChildId, securePrefs.deviceId)
            if (!pushed) {
                db.activityLogDao().insert(log.copy(isSynced = false))
            }
        }

        log
    }

    private fun isEssentialSystemPackage(lowerPkg: String): Boolean {
        return lowerPkg == "com.example" ||
            lowerPkg == "com.focussense.family" ||
            lowerPkg.contains("launcher") ||
            lowerPkg == "com.android.systemui" ||
            lowerPkg == "com.android.settings" ||
            lowerPkg.contains("dialer") ||
            lowerPkg.contains("telecom") ||
            lowerPkg.contains("contacts") ||
            lowerPkg == "com.android.phone" ||
            lowerPkg == "com.google.android.packageinstaller" ||
            lowerPkg.contains("inputmethod") ||
            lowerPkg.contains("keyboard")
    }

    // 2. Scheduled & Instant App Blocking Engine
    suspend fun checkAppRestriction(childId: String, packageName: String): ActiveAppBlockInfo = withContext(Dispatchers.IO) {
        val lowerPkg = packageName.lowercase()

        // Never lock FocusSense itself, essential phone/dialer, settings, or launchers
        if (isEssentialSystemPackage(lowerPkg)) {
            return@withContext ActiveAppBlockInfo(isBlocked = false)
        }

        // 1. Temporary PIN Override Check
        if (isPackageTemporarilyUnlocked(lowerPkg)) {
            return@withContext ActiveAppBlockInfo(isBlocked = false)
        }

        // 2. Instant Parent Block List Check (strict package match)
        val isInstantBlocked = _instantBlockedPackages.value.any { it.equals(lowerPkg, ignoreCase = true) }
        if (isInstantBlocked) {
            return@withContext ActiveAppBlockInfo(
                isBlocked = true,
                ruleName = "Parent Instant Lock",
                category = "Instant Restriction",
                endTime = "Until Unlocked by Parent",
                restrictedAppName = packageName
            )
        }

        // 2b. Pomodoro Child Focus Lock Check (STRICTLY active ONLY while timer is running with remainingSeconds > 0)
        val pomodoro = _pomodoroState.value
        if (pomodoro != null && pomodoro.isRunning && pomodoro.remainingSeconds > 0L) {
            val isPomodoroTarget = pomodoro.isBlockingAll ||
                pomodoro.blockedPackages.any { it.equals(lowerPkg, ignoreCase = true) }

            if (isPomodoroTarget) {
                val minsLeft = (pomodoro.remainingSeconds / 60L) + 1L
                return@withContext ActiveAppBlockInfo(
                    isBlocked = true,
                    ruleName = "Pomodoro Study Lock",
                    category = "Child Focus Mode",
                    endTime = "$minsLeft min remaining",
                    restrictedAppName = pomodoro.appName
                )
            }
        }

        // 3. Scheduled Curfew Rules Check
        val effectiveChildId = childId.ifBlank { _selectedChildId.value.ifBlank { _currentUser.value?.userId ?: "child-default" } }
        val childRules = if (effectiveChildId.isNotBlank()) {
            db.scheduleRuleDao().getRulesForChildSync(effectiveChildId)
        } else {
            emptyList()
        }
        val allActive = db.scheduleRuleDao().getAllActiveRulesSync()
        val rules = (childRules + allActive).distinctBy { it.ruleId }

        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)
        val currentTimeMinutes = currentHour * 60 + currentMinute

        // Day of week check (e.g. Mon, Tue)
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val currentDayName = dayNames[now.get(Calendar.DAY_OF_WEEK) - 1]

        for (rule in rules) {
            if (!rule.isActive) continue

            // Check days (allows "Daily", "All", "Everyday", empty, or comma-separated days)
            val daySpec = rule.dayOfWeek.trim()
            val dayMatches = daySpec.isBlank() ||
                    daySpec.contains("All", ignoreCase = true) ||
                    daySpec.contains("Daily", ignoreCase = true) ||
                    daySpec.contains("Everyday", ignoreCase = true) ||
                    daySpec.contains(currentDayName, ignoreCase = true)

            if (!dayMatches) continue

            // Parse start and end time
            val startParts = rule.startTime.split(":")
            val endParts = rule.endTime.split(":")
            if (startParts.size != 2 || endParts.size != 2) continue

            val startMinutes = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
            val endMinutes = (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)

            val isTimeMatch = when {
                // If explicitly configured for 24/7 (00:00 to 23:59)
                startMinutes == 0 && endMinutes >= 1439 -> true
                // Equal non-full-day start and end time means zero duration
                startMinutes == endMinutes -> false
                startMinutes < endMinutes -> currentTimeMinutes >= startMinutes && currentTimeMinutes < endMinutes
                startMinutes > endMinutes -> currentTimeMinutes >= startMinutes || currentTimeMinutes < endMinutes
                else -> false
            }

            if (isTimeMatch) {
                // Skip stale "Instant Block" schedule rules when package was already unlocked in fs_app_policies
                if (rule.category.equals("Instant Block", ignoreCase = true)) {
                    val pkgList = rule.restrictedPackages.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
                    val stillInstantBlocked = pkgList.any { _instantBlockedPackages.value.contains(it) }
                    if (!stillInstantBlocked) continue
                }

                val packages = rule.restrictedPackages.split(",").map { it.trim().lowercase() }
                val matchesApp = packages.any { pkgPattern ->
                    pkgPattern == "*" ||
                    pkgPattern == "all" ||
                    pkgPattern == lowerPkg
                }

                if (matchesApp) {
                    val displayAppName = rule.ruleName.substringAfter("Parent App Lock: ").takeIf { it.isNotBlank() } ?: packageName
                    return@withContext ActiveAppBlockInfo(
                        isBlocked = true,
                        ruleName = rule.ruleName,
                        category = rule.category,
                        endTime = rule.endTime,
                        restrictedAppName = displayAppName
                    )
                }
            }
        }

        ActiveAppBlockInfo(isBlocked = false)
    }

    // 3. Location Logging (Child phone only)
    suspend fun recordLocation(
        childId: String,
        lat: Double,
        lng: Double,
        accuracy: Float,
        locationName: String
    ): Unit = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        if (user == null || user.role != "child") {
            // Parent devices must NEVER record their own location as child telemetry
            Log.w("FocusSenseRepo", "Rejecting location record attempt from non-child role: ${user?.role}")
            return@withContext
        }
        val actualChildId = user.userId
        val point = LocationPointEntity(
            locId = UUID.randomUUID().toString(),
            childId = actualChildId,
            latitude = lat,
            longitude = lng,
            accuracy = accuracy,
            locationName = locationName,
            recordedAt = System.currentTimeMillis(),
            isSynced = false
        )
        db.locationDao().insert(point)
        _eventStream.tryEmit(SentinelEvent.LocationUpdated(locationName, lat, lng))

        if (_isNetworkConnected.value) {
            val pushed = com.example.data.remote.SupabaseClient.pushLocation(point, actualChildId, securePrefs.deviceId)
            if (pushed) {
                db.locationDao().insert(point.copy(isSynced = true))
            }
        }
    }

    // Parent manual GPS ping request to Child device
    suspend fun requestChildLocationPing(childId: String): Boolean = withContext(Dispatchers.IO) {
        val targetChildId = childId.ifBlank { _selectedChildId.value }
        if (targetChildId.isBlank()) return@withContext false
        val requested = com.example.data.remote.SupabaseClient.requestLocationPing(targetChildId)
        val latest = com.example.data.remote.SupabaseClient.fetchLatestLocation(targetChildId)
        if (latest != null) {
            val resolvedLoc = if (com.example.util.LocationGeocoderHelper.needsPlaceResolution(latest.locationName)) {
                latest.copy(
                    locationName = com.example.util.LocationGeocoderHelper.resolvePlaceLabel(
                        appContext,
                        latest.latitude,
                        latest.longitude
                    )
                )
            } else {
                latest
            }
            db.locationDao().insert(resolvedLoc)
        }
        requested || latest != null
    }

    // Child checks for pending location ping requests from Parent and acquires real GPS fix
    suspend fun checkAndRespondToLocationPing(context: Context) = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        if (user == null || user.role != "child") return@withContext
        val childId = user.userId
        try {
            if (com.example.data.remote.SupabaseClient.checkPendingLocationPing(childId)) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { loc ->
                        if (loc != null) {
                            scope.launch {
                                val placeName = com.example.util.LocationGeocoderHelper.resolvePlaceLabel(
                                    appContext,
                                    loc.latitude,
                                    loc.longitude
                                )
                                recordLocation(
                                    childId = childId,
                                    lat = loc.latitude,
                                    lng = loc.longitude,
                                    accuracy = loc.accuracy,
                                    locationName = placeName
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("FocusSenseRepo", "checkAndRespondToLocationPing error: ${e.message}")
        }
    }

    // 4. Zero Data-Loss Guarantee: Sync queued offline data to Supabase Cloud (Child only)
    suspend fun syncOfflineQueue(): Int = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        if (user == null || user.role != "child") {
            // Parent devices never upload telemetry
            return@withContext 0
        }
        val childId = user.userId
        _isSyncing.value = true
        var totalSynced = 0
        try {
            val unsyncedLogs = db.activityLogDao().getUnsyncedLogs()
            val unsyncedLocations = db.locationDao().getUnsyncedLocations()

            var logsSynced = 0
            for (log in unsyncedLogs) {
                if (com.example.data.remote.SupabaseClient.pushActivityLog(log, childId, securePrefs.deviceId)) {
                    db.activityLogDao().insert(log.copy(isSynced = true))
                    logsSynced++
                }
            }

            var locsSynced = 0
            for (loc in unsyncedLocations) {
                if (com.example.data.remote.SupabaseClient.pushLocation(loc, childId, securePrefs.deviceId)) {
                    db.locationDao().insert(loc.copy(isSynced = true))
                    locsSynced++
                }
            }

            totalSynced = logsSynced + locsSynced
            if (totalSynced > 0) {
                Log.i("FocusSenseRepo", "Successfully synced $totalSynced records to Supabase Cloud.")
            }
        } catch (e: Exception) {
            Log.e("FocusSenseRepo", "Sync to Supabase failed: ${e.message}")
        } finally {
            _isSyncing.value = false
            if (totalSynced > 0) {
                _eventStream.tryEmit(SentinelEvent.SyncCompleted(totalSynced))
            }
        }
        totalSynced
    }

    // 5. Parent Administrative Actions & App Management
    suspend fun acknowledgeAlert(logId: String) = withContext(Dispatchers.IO) {
        db.activityLogDao().markAcknowledged(logId)
    }

    suspend fun removeVulnerableLog(logId: String) = withContext(Dispatchers.IO) {
        db.activityLogDao().deleteLog(logId)
    }

    suspend fun addScheduleRule(rule: ScheduleRuleEntity) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().insert(rule)
        com.example.data.remote.SupabaseClient.saveScheduleRule(rule)
    }

    suspend fun updateScheduleRule(rule: ScheduleRuleEntity) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().update(rule)
        com.example.data.remote.SupabaseClient.saveScheduleRule(rule)
    }

    suspend fun deleteScheduleRule(ruleId: String) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().delete(ruleId)
        com.example.data.remote.SupabaseClient.deleteScheduleRule(ruleId)
    }

    suspend fun toggleRuleActive(ruleId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        db.scheduleRuleDao().toggleRuleActive(ruleId, isActive)
        val rule = db.scheduleRuleDao().getAllActiveRulesSync().find { it.ruleId == ruleId }
        if (rule != null) {
            com.example.data.remote.SupabaseClient.saveScheduleRule(rule.copy(isActive = isActive))
        }
    }

    suspend fun syncSchedulesWithServer(childId: String) = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        val effectiveChildId = if (user?.role == "child") user.userId else childId.ifBlank { _selectedChildId.value }
        if (effectiveChildId.isBlank()) return@withContext

        try {
            if (user?.role == "child") {
                // Child fetches authoritative app lock policies + schedule rules directly from Supabase
                val remoteApps = com.example.data.remote.SupabaseClient.fetchInstalledApps(effectiveChildId)
                if (remoteApps.isNotEmpty()) {
                    db.installedAppDao().insertAll(remoteApps)
                    val remoteBlocked = remoteApps.filter { it.isBlocked }.map { it.packageName.lowercase() }.toSet()
                    _instantBlockedPackages.value = remoteBlocked
                }

                // Reconcile local schedule rules with server so deleted instant-blocks or disabled curfews never linger 24/7
                val remoteSchedules = com.example.data.remote.SupabaseClient.fetchScheduleRules(effectiveChildId)
                db.scheduleRuleDao().deleteRulesForChild(effectiveChildId)
                if (remoteSchedules.isNotEmpty()) {
                    db.scheduleRuleDao().insertAll(remoteSchedules)
                }
            } else {
                // Parent fetches child schedules from Supabase
                val remoteSchedules = com.example.data.remote.SupabaseClient.fetchScheduleRules(effectiveChildId)
                db.scheduleRuleDao().deleteRulesForChild(effectiveChildId)
                if (remoteSchedules.isNotEmpty()) {
                    db.scheduleRuleDao().insertAll(remoteSchedules)
                }
            }
        } catch (e: Exception) {
            Log.w("FocusSenseRepo", "syncSchedulesWithServer error: ${e.message}")
        }
    }

    // Installed Applications & Remote App Locker
    fun getInstalledApps(childId: String): Flow<List<InstalledAppEntity>> {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value }
        return if (effectiveChildId.isNotBlank()) {
            db.installedAppDao().getInstalledApps(effectiveChildId)
        } else {
            db.installedAppDao().getAllInstalledApps()
        }
    }

    suspend fun scanAndSyncInstalledApps(context: Context, childId: String) = withContext(Dispatchers.IO) {
        val user = _currentUser.value ?: return@withContext
        val role = user.role

        if (role == "child") {
            // Child Phone: Scan local hardware packages and push to Supabase (respecting authoritative server blocks)
            val actualChildId = user.userId
            val scannedApps = InstalledAppScanner.getInstalledLauncherApps(context, actualChildId)
            val remoteApps = com.example.data.remote.SupabaseClient.fetchInstalledApps(actualChildId)
            val remoteBlocked = remoteApps
                .filter { it.isBlocked }
                .map { it.packageName.lowercase() }
                .toSet()

            val mergedApps = scannedApps.map { app ->
                if (remoteBlocked.contains(app.packageName.lowercase())) app.copy(isBlocked = true) else app
            }

            db.installedAppDao().insertAll(mergedApps)
            _instantBlockedPackages.value = remoteBlocked

            com.example.data.remote.SupabaseClient.syncInstalledApps(actualChildId, mergedApps)
        } else {
            // Parent Phone: Fetch the selected child's real installed apps from Supabase
            // STRICT RULE: NEVER scan parent phone, NEVER fallback to local device scanner
            val effectiveChildId = childId.ifBlank { _selectedChildId.value }
            if (effectiveChildId.isBlank()) return@withContext

            val remoteApps = com.example.data.remote.SupabaseClient.fetchInstalledApps(effectiveChildId)
            if (remoteApps.isNotEmpty()) {
                db.installedAppDao().insertAll(remoteApps)
                val remoteBlocked = remoteApps.filter { it.isBlocked }.map { it.packageName.lowercase() }.toSet()
                _instantBlockedPackages.value = remoteBlocked
            }
        }
    }

    // Parent fetches all remote telemetry for the selected child (and discovers newly paired children/devices)
    suspend fun fetchRemoteChildData(childId: String) = withContext(Dispatchers.IO) {
        val user = _currentUser.value

        // 0. Discover newly paired children & hardware devices in this family group
        if (user != null && user.role == "parent" && user.groupId.isNotBlank()) {
            try {
                val remoteChildren = com.example.data.remote.SupabaseClient.fetchFamilyChildren(user.groupId)
                if (remoteChildren.isNotEmpty()) {
                    remoteChildren.forEach { rec ->
                        db.userDao().insert(
                            UserEntity(
                                userId = rec.id,
                                groupId = rec.familyId ?: user.groupId,
                                email = rec.email,
                                password = "",
                                role = "child",
                                name = rec.displayName,
                                pin = "",
                                avatar = "default"
                            )
                        )
                        val devs = com.example.data.remote.SupabaseClient.fetchDevicesForUser(rec.id)
                        devs.forEach { dev ->
                            db.deviceDao().insert(dev)
                        }
                    }
                    if (_selectedChildId.value.isBlank()) {
                        _selectedChildId.value = remoteChildren.first().id
                    }
                }
            } catch (e: Exception) {
                Log.w("FocusSenseRepo", "fetchFamilyChildren error: ${e.message}")
            }
        }

        val effectiveChildId = childId.ifBlank { _selectedChildId.value }
        if (effectiveChildId.isBlank()) return@withContext

        try {
            // 1. Fetch remote child apps from Supabase
            val remoteApps = com.example.data.remote.SupabaseClient.fetchInstalledApps(effectiveChildId)
            if (remoteApps.isNotEmpty()) {
                db.installedAppDao().insertAll(remoteApps)
                val remoteBlocked = remoteApps.filter { it.isBlocked }.map { it.packageName.lowercase() }.toSet()
                _instantBlockedPackages.value = remoteBlocked
            }

            // 2. Fetch remote child location from Supabase & resolve place name
            val latestLoc = com.example.data.remote.SupabaseClient.fetchLatestLocation(effectiveChildId)
            if (latestLoc != null) {
                val resolvedLoc = if (com.example.util.LocationGeocoderHelper.needsPlaceResolution(latestLoc.locationName)) {
                    latestLoc.copy(
                        locationName = com.example.util.LocationGeocoderHelper.resolvePlaceLabel(
                            appContext,
                            latestLoc.latitude,
                            latestLoc.longitude
                        )
                    )
                } else {
                    latestLoc
                }
                db.locationDao().insert(resolvedLoc)
            }

            // 3. Fetch remote child activity logs from Supabase & notify Parent phone for NEW flagged alerts
            val remoteLogs = com.example.data.remote.SupabaseClient.fetchChildLogs(effectiveChildId)
            if (remoteLogs.isNotEmpty()) {
                val existingById = db.activityLogDao().getAllLogsSync().associateBy { it.logId }
                val notifiedIds = (prefs.getStringSet("notified_threat_log_ids", emptySet()) ?: emptySet()).toMutableSet()
                val now = System.currentTimeMillis()
                val mergedLogs = remoteLogs.map { remote ->
                    val existing = existingById[remote.logId]
                    val ack = existing?.isAcknowledged == true || remote.isAcknowledged
                    remote.copy(isAcknowledged = ack)
                }

                db.activityLogDao().insertAll(mergedLogs)

                if (user?.role == "parent") {
                    for (logItem in mergedLogs) {
                        val isRecent = (now - logItem.recordedAt) <= 24 * 3600_000L
                        if (logItem.isFlagged && !logItem.isAcknowledged && isRecent && !notifiedIds.contains(logItem.logId)) {
                            notifiedIds.add(logItem.logId)
                            val synthResult = ThreatAnalysisResult(
                                isFlagged = true,
                                threatCategory = logItem.threatCategory ?: "Safety Alert",
                                confidenceScore = logItem.confidenceScore,
                                aiAnalysisSummary = logItem.aiAnalysisSummary,
                                parentActionGuidance = "Review flagged activity from ${logItem.appName}.",
                                detectionEngine = "Cloud Sentinel Sync"
                            )
                            _eventStream.tryEmit(SentinelEvent.ContentFlagged(logItem, synthResult))
                        }
                    }
                    if (notifiedIds.size > 200) {
                        val trimmed = notifiedIds.toList().takeLast(150).toSet()
                        prefs.edit().putStringSet("notified_threat_log_ids", trimmed).apply()
                    } else {
                        prefs.edit().putStringSet("notified_threat_log_ids", notifiedIds).apply()
                    }
                }
            }

            // 4. Fetch schedules from Supabase (reconciling stale deleted rules)
            val remoteSchedules = com.example.data.remote.SupabaseClient.fetchScheduleRules(effectiveChildId)
            db.scheduleRuleDao().deleteRulesForChild(effectiveChildId)
            if (remoteSchedules.isNotEmpty()) {
                db.scheduleRuleDao().insertAll(remoteSchedules)
            }
        } catch (e: Exception) {
            Log.w("FocusSenseRepo", "fetchRemoteChildData error: ${e.message}")
        }
    }

    suspend fun toggleAppBlock(childId: String, packageName: String, isBlocked: Boolean) = withContext(Dispatchers.IO) {
        val effectiveChildId = childId.ifBlank { _selectedChildId.value.ifBlank { "child-default" } }
        val lowerPkg = packageName.lowercase()

        // 1. Update Room DB
        db.installedAppDao().updateAppBlockStatus(packageName, isBlocked)
        db.installedAppDao().updateAppBlockStatusForChild(effectiveChildId, packageName, isBlocked)

        // 2. Update In-Memory Instant Block Cache
        _instantBlockedPackages.value = if (isBlocked) {
            _instantBlockedPackages.value + lowerPkg
        } else {
            _instantBlockedPackages.value - lowerPkg
        }

        // 3. Create or Remove Instant Rule in schedule_rules
        val instantRuleId = "instant-block-${lowerPkg.replace('.', '-')}"
        val appName = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        val rule = ScheduleRuleEntity(
            ruleId = instantRuleId,
            childId = effectiveChildId,
            ruleName = "Parent App Lock: $appName",
            category = "Instant Block",
            startTime = "00:00",
            endTime = "23:59",
            dayOfWeek = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
            restrictedPackages = packageName,
            isActive = isBlocked
        )

        if (isBlocked) {
            db.scheduleRuleDao().insert(rule)
        } else {
            db.scheduleRuleDao().delete(instantRuleId)
        }

        // 4. Push directly to Supabase Cloud
        com.example.data.remote.SupabaseClient.toggleAppBlock(effectiveChildId, packageName, appName, isBlocked)
        if (isBlocked) {
            com.example.data.remote.SupabaseClient.saveScheduleRule(rule)
        } else {
            com.example.data.remote.SupabaseClient.deleteScheduleRule(instantRuleId)
        }
    }

    suspend fun updateParentPin(userId: String, newPin: String) = withContext(Dispatchers.IO) {
        db.userDao().updatePin(userId, newPin)
    }

    fun getAllowedYouTubeChannels(childId: String): Flow<List<com.example.data.model.AllowedYouTubeChannelEntity>> {
        return db.youtubeDao().getAllChannelsForChild(childId)
    }

    fun getWhitelistedYouTubeChannels(childId: String): Flow<List<com.example.data.model.AllowedYouTubeChannelEntity>> {
        return db.youtubeDao().getWhitelistedChannels(childId)
    }

    suspend fun toggleYouTubeChannelAllowed(channelId: String, isAllowed: Boolean) = withContext(Dispatchers.IO) {
        db.youtubeDao().updateAllowedStatus(channelId, isAllowed)
    }

    suspend fun addYouTubeChannel(channel: com.example.data.model.AllowedYouTubeChannelEntity) = withContext(Dispatchers.IO) {
        db.youtubeDao().insert(channel)
    }

    suspend fun removeYouTubeChannel(channelId: String) = withContext(Dispatchers.IO) {
        db.youtubeDao().removeChannel(channelId)
    }
}
