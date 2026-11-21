package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import java.util.UUID

@Entity(tableName = "family_groups")
data class FamilyGroupEntity(
    @PrimaryKey val groupId: String = UUID.randomUUID().toString(),
    val familyName: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String = UUID.randomUUID().toString(),
    val groupId: String,
    val email: String,
    val password: String,
    val role: String, // "parent" or "child"
    val name: String,
    val pin: String = "1234",
    val avatar: String = "default"
)

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val deviceId: String = UUID.randomUUID().toString(),
    val userId: String,
    val deviceName: String,
    val token: String = UUID.randomUUID().toString(),
    val batteryPercent: Int = 85,
    val isOnline: Boolean = true,
    val lastActive: Long = System.currentTimeMillis()
)

@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey @param:Json(name = "log_id") val logId: String = UUID.randomUUID().toString(),
    @param:Json(name = "child_id") val childId: String,
    @param:Json(name = "package_name") val packageName: String,
    @param:Json(name = "app_name") val appName: String,
    @param:Json(name = "content_title") val contentTitle: String = "",
    @param:Json(name = "extracted_text") val extractedText: String,
    @param:Json(name = "is_flagged") val isFlagged: Boolean,
    @param:Json(name = "threat_category") val threatCategory: String? = null, // "Cyberbullying", "Explicit Content", "Academic Distraction", "Self-Harm Risk", "Stranger Risk", "Safe"
    @param:Json(name = "confidence_score") val confidenceScore: Float = 0.0f,
    @param:Json(name = "ai_analysis_summary") val aiAnalysisSummary: String = "",
    @param:Json(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis(),
    @param:Json(name = "is_synced") val isSynced: Boolean = false,
    @param:Json(name = "is_acknowledged") val isAcknowledged: Boolean = false
)

@Entity(tableName = "schedule_rules")
data class ScheduleRuleEntity(
    @PrimaryKey @param:Json(name = "rule_id") val ruleId: String = UUID.randomUUID().toString(),
    @param:Json(name = "child_id") val childId: String,
    @param:Json(name = "rule_name") val ruleName: String,
    val category: String, // "Study", "Homework", "Bedtime", "Outdoor", "Free Time"
    @param:Json(name = "start_time") val startTime: String, // "15:00"
    @param:Json(name = "end_time") val endTime: String,   // "17:00"
    @param:Json(name = "day_of_week") val dayOfWeek: String = "Mon,Tue,Wed,Thu,Fri,Sat,Sun",
    @param:Json(name = "restricted_packages") val restrictedPackages: String, // comma-separated package names
    @param:Json(name = "is_active") val isActive: Boolean = true
)

@Entity(tableName = "location_history")
data class LocationPointEntity(
    @PrimaryKey @param:Json(name = "loc_id") val locId: String = UUID.randomUUID().toString(),
    @param:Json(name = "child_id") val childId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 5.0f,
    @param:Json(name = "location_name") val locationName: String = "Current Location",
    @param:Json(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis(),
    @param:Json(name = "is_synced") val isSynced: Boolean = false
)

data class RestrictedAppInfo(
    val packageName: String,
    val appName: String,
    val iconName: String,
    val category: String,
    val isInstalledOnDevice: Boolean = false
)

@Entity(tableName = "installed_apps")
data class InstalledAppEntity(
    @PrimaryKey val id: String, // childId + "_" + packageName
    @param:Json(name = "child_id") val childId: String,
    @param:Json(name = "package_name") val packageName: String,
    @param:Json(name = "app_name") val appName: String,
    val category: String,
    @param:Json(name = "is_blocked") val isBlocked: Boolean = false,
    @param:Json(name = "is_system_app") val isSystemApp: Boolean = false,
    @param:Json(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)

val PREDEFINED_RESTRICTED_APPS = listOf(
    RestrictedAppInfo("com.google.android.youtube", "YouTube", "video", "Entertainment / Streaming"),
    RestrictedAppInfo("com.zhiliaoapp.musically", "TikTok", "social", "Social Media"),
    RestrictedAppInfo("com.roblox.client", "Roblox", "game", "Gaming"),
    RestrictedAppInfo("com.instagram.android", "Instagram", "social", "Social Media"),
    RestrictedAppInfo("com.discord", "Discord", "chat", "Messaging / Community"),
    RestrictedAppInfo("com.snapchat.android", "Snapchat", "camera", "Ephemeral Social"),
    RestrictedAppInfo("com.netflix.mediaclient", "Netflix", "video", "Entertainment / Video"),
    RestrictedAppInfo("com.epicgames.fortnite", "Fortnite", "game", "Gaming")
)
