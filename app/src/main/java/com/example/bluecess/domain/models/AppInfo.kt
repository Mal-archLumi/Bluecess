package com.example.bluecess.domain.models

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null,
    var isAudioEnabled: Boolean = false,
    val isSystemApp: Boolean = false,
    val lastUsedTimestamp: Long = 0
)

// For data storage
data class AppRoutingPreference(
    val packageName: String,
    val isEnabled: Boolean,
    val lastModified: Long = System.currentTimeMillis()
)