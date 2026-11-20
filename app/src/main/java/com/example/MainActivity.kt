package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.model.DeviceRole
import com.example.model.SessionConnectionState
import com.example.sync.QrCodeUtils
import com.example.ui.SyncSoundViewModel
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CreateRoomScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JoinRoomScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SpeakerDashboardScreen
import com.example.ui.theme.SyncSoundTheme
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object CreateRoomRoute

@Serializable
data class JoinRoomRoute(
    val code: String = "",
    val hostIp: String = "",
    val port: Int = 8765,
    val token: String = "",
    val relayUrl: String = ""
)

@Serializable
data object AdminDashboardRoute

@Serializable
data object SpeakerDashboardRoute

@Serializable
data object SettingsRoute

class MainActivity : ComponentActivity() {

    private val viewModel: SyncSoundViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingUri(intent?.data)

        setContent {
            SyncSoundApp(
                viewModel = viewModel,
                onShareText = { text ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Invite to SyncSound Room"))
                },
                onLaunchBrowserUri = { uri ->
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingUri(intent.data)
    }

    private fun handleIncomingUri(uri: Uri?) {
        if (uri == null) return
        if (uri.scheme == "syncsound" && uri.host == "spotify-callback") {
            viewModel.handleSpotifyCallback(uri)
        } else if (uri.scheme == "syncsound" && uri.host == "join") {
            val parsed = QrCodeUtils.parseQrPayload(uri.toString()) ?: return
            viewModel.joinRoom(
                roomCode = parsed.roomCode,
                deviceName = viewModel.appSettings.value.deviceName,
                hostIp = parsed.hostIp,
                port = parsed.port,
                joinToken = parsed.joinToken,
                remoteRelayUrl = parsed.relayUrl
            )
        }
    }
}

@Composable
fun SyncSoundApp(
    viewModel: SyncSoundViewModel,
    onShareText: (String) -> Unit,
    onLaunchBrowserUri: (Uri) -> Unit
) {
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val roomInfo by viewModel.roomInfo.collectAsStateWithLifecycle()
    val roomSettings by viewModel.roomSettings.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val playerProgress by viewModel.playerProgress.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val discoveredRooms by viewModel.discoveredRooms.collectAsStateWithLifecycle()
    val recentRooms by viewModel.recentRooms.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val speakerVolume by viewModel.speakerVolume.collectAsStateWithLifecycle()
    val speakerMuted by viewModel.speakerMuted.collectAsStateWithLifecycle()
    val speakerDelayMs by viewModel.speakerDelayMs.collectAsStateWithLifecycle()
    val youtubeResults by viewModel.youtubeSearchResults.collectAsStateWithLifecycle()
    val youtubeSearching by viewModel.youtubeSearching.collectAsStateWithLifecycle()
    val youtubeError by viewModel.youtubeSearchError.collectAsStateWithLifecycle()
    val spotifyState by viewModel.spotifyState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    LaunchedEffect(connectionState, activeRole) {
        when {
            connectionState == SessionConnectionState.HOSTING && activeRole == DeviceRole.ADMIN -> {
                navController.navigate(AdminDashboardRoute) {
                    popUpTo(HomeRoute) { inclusive = false }
                    launchSingleTop = true
                }
            }
            connectionState == SessionConnectionState.CONNECTED && activeRole == DeviceRole.SPEAKER -> {
                navController.navigate(SpeakerDashboardRoute) {
                    popUpTo(HomeRoute) { inclusive = false }
                    launchSingleTop = true
                }
            }
            connectionState == SessionConnectionState.IDLE && activeRole == null -> {
                val current = navController.currentDestination?.route.orEmpty()
                if (current.contains("AdminDashboardRoute") || current.contains("SpeakerDashboardRoute")) {
                    navController.popBackStack(HomeRoute, inclusive = false)
                }
            }
        }
    }

    SyncSoundTheme(darkTheme = appSettings.isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = HomeRoute
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        connectionState = connectionState,
                        activeRole = activeRole,
                        activeRoom = roomInfo,
                        discoveredRooms = discoveredRooms,
                        recentRooms = recentRooms,
                        onStartDiscovery = { viewModel.startDiscovery() },
                        onStopDiscovery = { viewModel.stopDiscovery() },
                        onCreateRoomClick = { navController.navigate(CreateRoomRoute) },
                        onJoinRoomClick = { navController.navigate(JoinRoomRoute()) },
                        onQuickJoinDiscovered = { discovered ->
                            navController.navigate(
                                JoinRoomRoute(
                                    code = discovered.roomCode,
                                    hostIp = discovered.hostAddress,
                                    port = discovered.port
                                )
                            )
                        },
                        onResumeRecent = { entry ->
                            if (entry.role == DeviceRole.ADMIN) {
                                ensureNotificationPermission()
                                viewModel.createRoom(
                                    roomName = entry.roomName,
                                    roomCode = entry.roomCode,
                                    mode = entry.connectionMode,
                                    remoteRelayUrl = entry.remoteRelayUrl
                                )
                            } else {
                                navController.navigate(
                                    JoinRoomRoute(
                                        code = entry.roomCode,
                                        hostIp = entry.hostIp,
                                        port = entry.port,
                                        token = entry.joinToken,
                                        relayUrl = entry.remoteRelayUrl
                                    )
                                )
                            }
                        },
                        onRemoveRecent = { code -> viewModel.removeRecentRoom(code) },
                        onOpenActiveSession = {
                            if (activeRole == DeviceRole.ADMIN) {
                                navController.navigate(AdminDashboardRoute)
                            } else if (activeRole == DeviceRole.SPEAKER) {
                                navController.navigate(SpeakerDashboardRoute)
                            }
                        },
                        onSettingsClick = { navController.navigate(SettingsRoute) }
                    )
                }

                composable<CreateRoomRoute> {
                    val initialCode = remember { viewModel.generateRoomCode() }
                    CreateRoomScreen(
                        initialCode = initialCode,
                        defaultRelayUrl = appSettings.remoteRelayUrl,
                        onGenerateNewCode = { viewModel.generateRoomCode() },
                        onShareInvite = onShareText,
                        onLaunchRoom = { roomName, code, mode, relay ->
                            ensureNotificationPermission()
                            viewModel.createRoom(roomName, code, mode, relay)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<JoinRoomRoute> { backStackEntry ->
                    val args = backStackEntry.toRoute<JoinRoomRoute>()
                    JoinRoomScreen(
                        defaultDeviceName = appSettings.deviceName,
                        prefillCode = args.code,
                        prefillHostIp = args.hostIp,
                        prefillPort = args.port,
                        prefillToken = args.token,
                        prefillRelayUrl = args.relayUrl,
                        discoveredRooms = discoveredRooms,
                        connectionState = connectionState,
                        statusBanner = statusBanner,
                        onStartDiscovery = { viewModel.startDiscovery() },
                        onStopDiscovery = { viewModel.stopDiscovery() },
                        onJoinRoom = { code, name, hostIp, port, token, relay ->
                            ensureNotificationPermission()
                            viewModel.joinRoom(code, name, hostIp, port, token, relay)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<AdminDashboardRoute> {
                    AdminDashboardScreen(
                        roomInfo = roomInfo,
                        roomSettings = roomSettings,
                        devices = devices,
                        snapshot = snapshot,
                        progress = playerProgress,
                        playlists = playlists,
                        youtubeSearchResults = youtubeResults,
                        youtubeSearching = youtubeSearching,
                        youtubeSearchError = youtubeError,
                        spotifyState = spotifyState,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onStop = { viewModel.stopPlayback() },
                        onNext = { viewModel.nextTrack() },
                        onPrev = { viewModel.previousTrack() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onSeekBy = { viewModel.seekBy(it) },
                        onMasterVolumeChange = { viewModel.setMasterVolume(it) },
                        onAddLocalUris = { viewModel.addLocalAudioFiles(it) },
                        onAddStreamUrl = { url, title -> viewModel.addStreamUrl(url, title) },
                        onAddYouTubeUrl = { viewModel.addYouTubeUrlOrId(it) },
                        onSearchYouTube = { viewModel.searchYouTube(it) },
                        onAddTrack = { viewModel.addTrack(it) },
                        onSelectQueueIndex = { viewModel.selectQueueIndex(it) },
                        onRemoveQueueIndex = { viewModel.removeQueueIndex(it) },
                        onSavePlaylist = { viewModel.saveCurrentQueueAsPlaylist(it) },
                        onLoadPlaylist = { viewModel.loadPlaylist(it) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onConnectSpotify = {
                            viewModel.prepareSpotifyLoginUri()?.let(onLaunchBrowserUri)
                        },
                        onSyncSpotifyTrack = { viewModel.syncSpotifyCurrentTrack() },
                        onDeviceVolumeChange = { id, vol -> viewModel.setDeviceVolume(id, vol) },
                        onToggleDeviceMute = { id -> viewModel.toggleDeviceMute(id) },
                        onDeviceDelayChange = { id, delay -> viewModel.setDeviceDelay(id, delay) },
                        onKickDevice = { id -> viewModel.kickDevice(id) },
                        onTestAllSpeakers = { viewModel.triggerTestChime() },
                        onUpdateRoomSettings = { viewModel.updateRoomSettings(it) },
                        onShareInvite = onShareText,
                        onCloseRoom = {
                            viewModel.closeOrLeaveRoom()
                            navController.popBackStack(HomeRoute, inclusive = false)
                        }
                    )
                }

                composable<SpeakerDashboardRoute> {
                    SpeakerDashboardScreen(
                        roomInfo = roomInfo,
                        roomSettings = roomSettings,
                        snapshot = snapshot,
                        progress = playerProgress,
                        telemetry = telemetry,
                        localVolume = speakerVolume,
                        isMuted = speakerMuted,
                        delayCalibrationMs = speakerDelayMs,
                        onVolumeChange = { viewModel.setSpeakerLocalVolume(it) },
                        onToggleMute = { viewModel.toggleSpeakerMute() },
                        onDelayChange = { viewModel.setSpeakerDelay(it) },
                        onResyncNow = { viewModel.resyncSpeaker() },
                        onLeaveRoom = {
                            viewModel.closeOrLeaveRoom()
                            navController.popBackStack(HomeRoute, inclusive = false)
                        }
                    )
                }

                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = appSettings,
                        onUpdateSettings = { viewModel.updateAppSettings(it) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
            )
        }
    }
}
