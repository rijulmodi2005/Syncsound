package com.example.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.model.DiscoveredRoom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

class NsdDiscoveryManager(private val context: Context) {

    companion object {
        const val SERVICE_TYPE = "_syncsound._tcp."
        private const val SERVICE_PREFIX = "SyncSound_"

        fun getLocalIpv4Address(): String {
            return runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                val candidates = interfaces
                    .filter { it.isUp && !it.isLoopback }
                    .flatMap { it.inetAddresses.toList() }
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && it.isSiteLocalAddress }
                candidates.firstOrNull()?.hostAddress ?: "127.0.0.1"
            }.getOrDefault("127.0.0.1")
        }
    }

    private val nsdManager: NsdManager? =
        runCatching { context.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()
    private val wifiManager: WifiManager? =
        runCatching { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }.getOrNull()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null

    private val _discoveredRooms = MutableStateFlow<List<DiscoveredRoom>>(emptyList())
    val discoveredRooms: StateFlow<List<DiscoveredRoom>> = _discoveredRooms.asStateFlow()

    fun registerRoomService(roomCode: String, roomName: String, port: Int) {
        unregisterRoomService()
        val manager = nsdManager ?: return
        val safeName = roomName.replace(Regex("[^A-Za-z0-9 ]"), "").take(18).trim().ifEmpty { "Room" }
        val serviceName = "${SERVICE_PREFIX}${roomCode}_$safeName"

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                registeredServiceName = null
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun unregisterRoomService() {
        val manager = nsdManager ?: return
        registrationListener?.let { listener ->
            runCatching { manager.unregisterService(listener) }
        }
        registrationListener = null
        registeredServiceName = null
    }

    fun startDiscovery() {
        stopDiscovery()
        val manager = nsdManager ?: return
        acquireMulticastLock()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != SERVICE_TYPE) return
                if (service.serviceName == registeredServiceName) return
                if (!service.serviceName.startsWith(SERVICE_PREFIX)) return
                resolveFoundService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _discoveredRooms.value = _discoveredRooms.value.filterNot {
                    it.serviceName == service.serviceName
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }
        }
        discoveryListener = listener
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveFoundService(service: NsdServiceInfo) {
        val manager = nsdManager ?: return
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress ?: return
                val port = serviceInfo.port
                val parts = serviceInfo.serviceName.removePrefix(SERVICE_PREFIX).split("_", limit = 2)
                val code = parts.getOrNull(0)?.take(5) ?: return
                val roomName = parts.getOrNull(1)?.ifBlank { "SyncSound Room" } ?: "SyncSound Room"

                val item = DiscoveredRoom(
                    roomCode = code,
                    roomName = roomName,
                    hostAddress = host,
                    port = port,
                    serviceName = serviceInfo.serviceName
                )
                val updated = _discoveredRooms.value
                    .filterNot { it.roomCode == code } + item
                _discoveredRooms.value = updated
            }
        }
        runCatching { manager.resolveService(service, resolveListener) }
    }

    fun stopDiscovery() {
        val manager = nsdManager ?: return
        discoveryListener?.let { listener ->
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        runCatching {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("SyncSoundNsdLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
    }
}
