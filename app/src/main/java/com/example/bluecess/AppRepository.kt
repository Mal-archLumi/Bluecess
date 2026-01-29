package com.example.bluecess.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bluecess_preferences")

class AppRepository(private val context: Context) {
    private val ROUTED_APPS_KEY = stringSetPreferencesKey("routed_apps")
    private val EXCLUSIVE_MODE_KEY = booleanPreferencesKey("exclusive_mode")
    
    suspend fun addAppRouting(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[ROUTED_APPS_KEY] ?: emptySet()
            preferences[ROUTED_APPS_KEY] = currentApps + packageName
        }
    }
    
    suspend fun removeAppRouting(packageName: String) {
        context.dataStore.edit { preferences ->
            val currentApps = preferences[ROUTED_APPS_KEY] ?: emptySet()
            preferences[ROUTED_APPS_KEY] = currentApps - packageName
        }
    }
    
    fun isAppRouted(packageName: String): Boolean {
        // This is a synchronous version for quick UI updates
        // In production, use Flow for reactive updates
        return getRoutedAppsSync().contains(packageName)
    }
    
    private fun getRoutedAppsSync(): Set<String> {
        // This is a simplified sync version
        // In production, use proper synchronization
        return emptySet() // Will be populated from async loading
    }
    
    val routedApps: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[ROUTED_APPS_KEY] ?: emptySet()
        }
    
    suspend fun setExclusiveMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[EXCLUSIVE_MODE_KEY] = enabled
        }
    }
    
    fun isExclusiveModeEnabled(): Boolean {
        return false // Will be loaded from DataStore
    }
    
    fun getRoutedApps(): List<String> {
        return getRoutedAppsSync().toList()
    }
}