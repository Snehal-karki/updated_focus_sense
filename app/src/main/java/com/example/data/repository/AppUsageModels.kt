package com.example.data.repository

data class AppUsageEntry(
    val packageName: String,
    val appName: String,
    val category: String,
    val foregroundTimeMs: Long,
    val totalTimeMinutes: Int,
    val launchCount: Int,
    val isBlocked: Boolean = false
)
