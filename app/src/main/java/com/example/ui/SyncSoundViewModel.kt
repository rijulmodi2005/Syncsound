package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsAndHistoryRepository
import com.example.data.SyncSessionManager
import com.example.model.AppSettings
import com.example.model.ConnectionMode
import com.example.model.PlaylistInfo
import com.example.model.RoomSettings
import com.example.model.TrackItem
import com.example.sources.MusicSourcesHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncSoundViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsAndHistoryRepository(application)
    val sessionManager = SyncSessionManager(application, repository)

    val appSettings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val recentRooms = repository.recentRoomsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val playlists = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectionState = sessionManager.connectionState
    val activeRole = sessionManager.activeRole
    val roomInfo = sessionManager.roomInfo
    val roomSettings = sessionManager.roomSettings
    val devices = sessionManager.devices
    val snapshot = sessionManager.snapshot
    val statusBanner = sessionManager.statusBanner
    val playerProgress = sessionManager.playerProgress
    val telemetry = sessionManager.telemetry
    val discoveredRooms = sessionManager.discoveredRooms
    val speakerVolume = sessionManager.speakerVolume
    val speakerMuted = sessionManager.speakerMuted
    val speakerDelayMs = sessionManager.speakerDelayMs
    val spotifyState = sessionManager.spotifyState

    private val _youtubeSearchResults = MutableStateFlow<List<TrackItem>>(emptyList())
    val youtubeSearchResults: StateFlow<List<TrackItem>> = _youtubeSearchResults.asStateFlow()

    private val _youtubeSearching = MutableStateFlow(false)
    val youtubeSearching: StateFlow<Boolean> = _youtubeSearching.asStateFlow()

    private val _youtubeSearchError = MutableStateFlow<String?>(null)
    val youtubeSearchError: StateFlow<String?> = _youtubeSearchError.asStateFlow()

    fun generateRoomCode(): String = sessionManager.generateRoomCode()

    fun startDiscovery() = sessionManager.startDiscovery()
    fun stopDiscovery() = sessionManager.stopDiscovery()
    fun clearBanner() = sessionManager.clearBanner()

    fun createRoom(
        roomName: String,
        roomCode: String,
        mode: ConnectionMode,
        remoteRelayUrl: String
    ) {
        sessionManager.createAndHostRoom(
            roomName = roomName,
            roomCode = roomCode,
            connectionMode = mode,
            remoteRelayUrl = remoteRelayUrl,
            settings = appSettings.value
        )
    }

    fun joinRoom(
        roomCode: String,
        deviceName: String,
        hostIp: String,
        port: Int,
        joinToken: String,
        remoteRelayUrl: String = ""
    ) {
        sessionManager.joinRoom(
            roomCode = roomCode,
            deviceName = deviceName,
            hostIp = hostIp,
            port = port,
            joinToken = joinToken,
            remoteRelayUrl = remoteRelayUrl
        )
    }

    fun removeRecentRoom(roomCode: String) {
        viewModelScope.launch { repository.removeRecentRoom(roomCode) }
    }

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.updateSettings(transform) }
    }

    fun addLocalAudioFiles(uris: List<Uri>) = sessionManager.addLocalAudioUris(uris)
    fun addStreamUrl(url: String, title: String) = sessionManager.addDirectStreamUrl(url, title)
    fun addYouTubeUrlOrId(urlOrId: String) = sessionManager.addYouTubeUrlOrId(urlOrId)
    fun addTrack(track: TrackItem) = sessionManager.addTrackDirectly(track)

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _youtubeSearching.value = true
            _youtubeSearchError.value = null
            val res = MusicSourcesHelper.searchYouTubeDataApi(query, appSettings.value.youtubeApiKey)
            res.onSuccess { list ->
                _youtubeSearchResults.value = list
            }.onFailure { err ->
                _youtubeSearchError.value = err.message
            }
            _youtubeSearching.value = false
        }
    }

    fun selectQueueIndex(index: Int) = sessionManager.selectQueueIndex(index, autoPlay = true)
    fun removeQueueIndex(index: Int) = sessionManager.removeQueueIndex(index)
    fun loadPlaylist(playlist: PlaylistInfo) = sessionManager.loadPlaylistIntoQueue(playlist, autoPlay = true)
    fun saveCurrentQueueAsPlaylist(name: String) = sessionManager.saveCurrentQueueAsPlaylist(name)
    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.savePlaylists(playlists.value.filterNot { it.id == playlistId })
        }
    }

    fun togglePlayPause() = sessionManager.togglePlayPause()
    fun stopPlayback() = sessionManager.stopPlayback()
    fun nextTrack() = sessionManager.nextTrack()
    fun previousTrack() = sessionManager.previousTrack()
    fun seekTo(posMs: Long) = sessionManager.seekTo(posMs)
    fun seekBy(deltaMs: Long) = sessionManager.seekBy(deltaMs)
    fun setMasterVolume(vol: Float) = sessionManager.setMasterVolume(vol)

    fun setDeviceVolume(deviceId: String, vol: Float) = sessionManager.setDeviceVolume(deviceId, vol)
    fun toggleDeviceMute(deviceId: String) = sessionManager.toggleDeviceMute(deviceId)
    fun setDeviceDelay(deviceId: String, delayMs: Long) = sessionManager.setDeviceDelayCalibration(deviceId, delayMs)
    fun kickDevice(deviceId: String) = sessionManager.kickDevice(deviceId)
    fun updateRoomSettings(transform: (RoomSettings) -> RoomSettings) = sessionManager.updateRoomSettings(transform)
    fun triggerTestChime() = sessionManager.triggerSpeakerTestChime()

    fun setSpeakerLocalVolume(vol: Float) = sessionManager.setSpeakerLocalVolume(vol)
    fun toggleSpeakerMute() = sessionManager.toggleSpeakerMute()
    fun setSpeakerDelay(delayMs: Long) = sessionManager.setSpeakerLocalDelay(delayMs)
    fun resyncSpeaker() = sessionManager.resyncSpeakerNow()

    fun prepareSpotifyLoginUri(): Uri? = sessionManager.prepareSpotifyLoginUri(appSettings.value.spotifyClientId)
    fun handleSpotifyCallback(uri: Uri) = sessionManager.handleSpotifyCallbackUri(uri, appSettings.value.spotifyClientId)
    fun syncSpotifyCurrentTrack() = sessionManager.syncSpotifyCurrentTrack()

    fun closeOrLeaveRoom() = sessionManager.closeOrLeaveRoom()

    override fun onCleared() {
        sessionManager.destroy()
        super.onCleared()
    }
}
