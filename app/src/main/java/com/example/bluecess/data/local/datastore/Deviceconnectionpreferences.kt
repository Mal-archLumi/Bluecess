package com.example.bluecess.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_connections")

class DeviceConnectionPreferences(private val context: Context) {
    
    companion object {
        private val CONNECTED_DEVICES_KEY = stringPreferencesKey("connected_devices")
    }

    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun saveConnectedDevices(devices: Map<String, Boolean>) {
        context.dataStore.edit { preferences ->
            val jsonString = json.encodeToString(devices)
            preferences[CONNECTED_DEVICES_KEY] = jsonString
        }
    }

    fun getConnectedDevices(): Flow<Map<String, Boolean>> {
        return context.dataStore.data.map { preferences ->
            val jsonString = preferences[CONNECTED_DEVICES_KEY] ?: "{}"
            try {
                json.decodeFromString<Map<String, Boolean>>(jsonString)
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }

    suspend fun clearConnectedDevices() {
        context.dataStore.edit { preferences ->
            preferences.remove(CONNECTED_DEVICES_KEY)
        }
    }
}