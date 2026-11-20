package com.example.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppSettings
import com.example.model.PlaylistInfo
import com.example.model.RecentRoomEntry
import com.example.model.SyncJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.syncSoundDataStore: DataStore<Preferences> by preferencesDataStore(name = "syncsound_prefs")

class SettingsAndHistoryRepository(private val context: Context) {

    private object Keys {
        val SETTINGS_JSON = stringPreferencesKey("app_settings_json")
        val RECENT_ROOMS_JSON = stringPreferencesKey("recent_rooms_json")
        val PLAYLISTS_JSON = stringPreferencesKey("playlists_json")
        val DEVICE_ID = stringPreferencesKey("device_id")
    }

    val settingsFlow: Flow<AppSettings> = context.syncSoundDataStore.data.map { prefs ->
        val raw = prefs[Keys.SETTINGS_JSON]
        if (raw.isNullOrBlank()) {
            defaultSettings()
        } else {
            runCatching { SyncJson.decodeFromString<AppSettings>(raw) }
                .getOrElse { defaultSettings() }
        }
    }

    val recentRoomsFlow: Flow<List<RecentRoomEntry>> = context.syncSoundDataStore.data.map { prefs ->
        val raw = prefs[Keys.RECENT_ROOMS_JSON]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { SyncJson.decodeFromString<List<RecentRoomEntry>>(raw) }.getOrDefault(emptyList())
    }

    val playlistsFlow: Flow<List<PlaylistInfo>> = context.syncSoundDataStore.data.map { prefs ->
        val raw = prefs[Keys.PLAYLISTS_JSON]
        if (raw.isNullOrBlank()) emptyList()
        else runCatching { SyncJson.decodeFromString<List<PlaylistInfo>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = context.syncSoundDataStore.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = "and-${UUID.randomUUID().toString().take(8)}"
        context.syncSoundDataStore.edit { it[Keys.DEVICE_ID] = generated }
        return generated
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.syncSoundDataStore.edit { prefs ->
            val current = prefs[Keys.SETTINGS_JSON]?.let {
                runCatching { SyncJson.decodeFromString<AppSettings>(it) }.getOrNull()
            } ?: defaultSettings()
            val updated = transform(current)
            prefs[Keys.SETTINGS_JSON] = SyncJson.encodeToString(AppSettings.serializer(), updated)
        }
    }

    suspend fun recordRecentRoom(entry: RecentRoomEntry) {
        context.syncSoundDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_ROOMS_JSON]?.let {
                runCatching { SyncJson.decodeFromString<List<RecentRoomEntry>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = (listOf(entry) + current.filterNot { it.roomCode == entry.roomCode }).take(8)
            prefs[Keys.RECENT_ROOMS_JSON] = SyncJson.encodeToString(updated)
        }
    }

    suspend fun removeRecentRoom(roomCode: String) {
        context.syncSoundDataStore.edit { prefs ->
            val current = prefs[Keys.RECENT_ROOMS_JSON]?.let {
                runCatching { SyncJson.decodeFromString<List<RecentRoomEntry>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = current.filterNot { it.roomCode == roomCode }
            prefs[Keys.RECENT_ROOMS_JSON] = SyncJson.encodeToString(updated)
        }
    }

    suspend fun savePlaylists(playlists: List<PlaylistInfo>) {
        context.syncSoundDataStore.edit { prefs ->
            prefs[Keys.PLAYLISTS_JSON] = SyncJson.encodeToString(playlists)
        }
    }

    private fun defaultSettings(): AppSettings {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android Device"
        return AppSettings(deviceName = model)
    }
}
