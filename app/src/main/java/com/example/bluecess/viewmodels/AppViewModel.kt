package com.example.bluecess.viewmodels

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluecess.domain.models.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Context.dataStore by preferencesDataStore(name = "app_routing_preferences")

class AppViewModel : ViewModel() {
    companion object {
        private const val TAG = "AppViewModel"
        private val AUDIO_RELATED_PERMISSIONS = setOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )
        
        // Common audio/player app package names
        private val AUDIO_APP_KEYWORDS = listOf(
            "music", "player", "audio", "sound", "radio", "podcast", "stream",
            "spotify", "youtube", "netflix", "disney", "prime", "soundcloud",
            "deezer", "tidal", "pandora", "apple.music", "viber", "whatsapp",
            "telegram", "messenger", "discord", "zoom", "meet", "teams",
            "tiktok", "instagram", "facebook", "twitter", "snapchat",
            "game", "media", "video", "recorder", "mp3", "flac", "wav"
        )
        
        // System apps that actually use audio
        private val AUDIO_SYSTEM_APPS = listOf(
            "com.android.dialer",
            "com.android.phone",
            "com.android.music",
            "com.android.soundrecorder",
            "com.google.android.apps.maps",  // Navigation voice
            "com.android.systemui",  // System sounds
            "com.google.android.tts"  // Text-to-speech
        )
    }
    
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()
    
    private val _filteredApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val filteredApps: StateFlow<List<AppInfo>> = _filteredApps.asStateFlow()
    
    var searchQuery by mutableStateOf("")
        private set
    
    var isLoading by mutableStateOf(false)
        private set
    
    var isExclusiveMode by mutableStateOf(true)
        private set
    
    fun loadApps(context: Context) {
        viewModelScope.launch {
            isLoading = true
            try {
                val appList = withContext(Dispatchers.IO) {
                    fetchAudioApps(context)
                }
                
                // Load saved preferences
                loadAppPreferences(context, appList)
                
                _apps.value = appList
                _filteredApps.value = appList
                Log.d(TAG, "Loaded ${appList.size} audio-related apps")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading apps", e)
            } finally {
                isLoading = false
            }
        }
    }
    
    private fun fetchAudioApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val appList = mutableListOf<AppInfo>()
        
        try {
            val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            
            packages.forEach { applicationInfo ->
                val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                val packageName = applicationInfo.packageName
                val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                // Skip system apps unless they're in our audio system apps list
                if (isSystemApp && !AUDIO_SYSTEM_APPS.any { packageName.contains(it) }) {
                    return@forEach
                }
                
                // Check if this is an audio-related app
                if (isAudioApp(packageManager, applicationInfo, appName, packageName)) {
                    try {
                        val icon = packageManager.getApplicationIcon(packageName)
                        appList.add(
                            AppInfo(
                                packageName = packageName,
                                name = appName,
                                icon = icon,
                                isAudioEnabled = false,
                                isSystemApp = isSystemApp
                            )
                        )
                    } catch (e: Exception) {
                        // App might not have an icon, add without icon
                        appList.add(
                            AppInfo(
                                packageName = packageName,
                                name = appName,
                                icon = null,
                                isAudioEnabled = false,
                                isSystemApp = isSystemApp
                            )
                        )
                    }
                }
            }
            
            // Sort by name
            appList.sortBy { it.name.lowercase() }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching audio apps", e)
        }
        
        return appList
    }
    
    private fun isAudioApp(
        packageManager: PackageManager,
        appInfo: ApplicationInfo,
        appName: String,
        packageName: String
    ): Boolean {
        // Check if app name contains audio-related keywords
        val lowerAppName = appName.lowercase()
        if (AUDIO_APP_KEYWORDS.any { keyword -> lowerAppName.contains(keyword) }) {
            return true
        }
        
        // Check if package name contains audio-related keywords
        val lowerPackageName = packageName.lowercase()
        if (AUDIO_APP_KEYWORDS.any { keyword -> lowerPackageName.contains(keyword) }) {
            return true
        }
        
        // Check if app has audio-related permissions
        try {
            val permissions = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            permissions?.requestedPermissions?.forEach { permission ->
                if (AUDIO_RELATED_PERMISSIONS.any { audioPerm -> permission.contains(audioPerm) }) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Some apps might not have permission info
        }
        
        // Check if app has audio-related features
        try {
            val features = packageManager.getSystemAvailableFeatures()
            val hasAudioFeature = features.any { feature ->
                feature?.name?.let { 
                    it.contains("audio") || it.contains("microphone") || it.contains("bluetooth")
                } ?: false
            }
            if (hasAudioFeature) {
                return true
            }
        } catch (e: Exception) {
            // Ignore feature check errors
        }
        
        // For system audio apps, always include
        if (AUDIO_SYSTEM_APPS.any { packageName.contains(it) }) {
            return true
        }
        
        return false
    }
    
    private suspend fun loadAppPreferences(context: Context, appList: List<AppInfo>) {
        try {
            val preferences = context.dataStore.data.first()
            
            val updatedList = appList.map { app ->
                val key = booleanPreferencesKey("app_enabled_${app.packageName}")
                val isEnabled = preferences[key] ?: false
                app.copy(isAudioEnabled = isEnabled)
            }
            
            _apps.value = updatedList
        } catch (e: Exception) {
            Log.e(TAG, "Error loading preferences", e)
        }
    }
    
    fun toggleAppAudio(context: Context?, packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                // Update local state
                val updatedApps = _apps.value.map { app ->
                    if (app.packageName == packageName) {
                        app.copy(isAudioEnabled = enabled)
                    } else {
                        // If exclusive mode is on, disable all other apps
                        if (isExclusiveMode && enabled) {
                            app.copy(isAudioEnabled = false)
                        } else {
                            app
                        }
                    }
                }
                
                _apps.value = updatedApps
                filterApps(searchQuery) // Refresh filtered list
                
                // Save preference if context is provided
                if (context != null) {
                    saveAppPreference(context, packageName, enabled)
                    
                    // If exclusive mode and we're enabling this app, save others as disabled
                    if (isExclusiveMode && enabled) {
                        updatedApps.forEach { app ->
                            if (app.packageName != packageName && app.isAudioEnabled) {
                                saveAppPreference(context, app.packageName, false)
                            }
                        }
                    }
                }
                
                Log.d(TAG, "Toggled $packageName to $enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling app", e)
            }
        }
    }
    
    private suspend fun saveAppPreference(context: Context, packageName: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            val key = booleanPreferencesKey("app_enabled_${packageName}")
            preferences[key] = enabled
        }
    }
    
    fun filterApps(query: String) {
        searchQuery = query
        viewModelScope.launch {
            if (query.isEmpty()) {
                _filteredApps.value = _apps.value
            } else {
                val filtered = _apps.value.filter { app ->
                    app.name.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
                }
                _filteredApps.value = filtered
            }
        }
    }
    
    fun toggleExclusiveMode(enabled: Boolean) {
        isExclusiveMode = enabled
        
        // If enabling exclusive mode and multiple apps are enabled, disable all but first
        if (enabled) {
            val enabledApps = _apps.value.filter { it.isAudioEnabled }
            if (enabledApps.size > 1) {
                // Keep only the first enabled app
                enabledApps.drop(1).forEach { app ->
                    toggleAppAudio(null, app.packageName, false)
                }
            }
        }
    }
    
    fun getEnabledApps(): List<AppInfo> {
        return _apps.value.filter { it.isAudioEnabled }
    }
}