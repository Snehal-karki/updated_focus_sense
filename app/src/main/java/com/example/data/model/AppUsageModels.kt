package com.example.data.model

data class AppUsageStatRecord(
    val id: String = "",
    val subjectUserId: String = "",
    val role: String = "child",
    val dateKey: String = "",
    val packageName: String = "",
    val appName: String = "",
    val category: String = "Application",
    val totalTimeMinutes: Long = 0L,
    val foregroundTimeMs: Long = 0L,
    val launchCount: Long = 0L,
    val isBlocked: Boolean = false
)
