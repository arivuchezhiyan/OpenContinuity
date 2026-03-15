package com.opencontinuity.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Discovery Manager handles mDNS service advertisement and discovery
 * Uses Android's Network Service Discovery (NSD) API
 */
class DiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryManager"
        private const val SERVICE_TYPE = "_opencontinuity._tcp."
        private const val SERVICE_NAME = "OpenContinuity"
        /** UDP port for direct broadcast beacon — works on hotspot, tethering, any subnet */
        const val BEACON_PORT = 8768
    }

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _discoveredServices = MutableStateFlow<List<DiscoveredService>>(emptyList())
    val discoveredServices: StateFlow<List<DiscoveredService>> = _discoveredServices.asStateFlow()

    private var serverPort: Int = 0

    // ── UDP broadcast beacon ──────────────────────────────────────────────────
    private val beaconScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var beaconJob: Job? = null

    /**
     * Broadcast a UDP beacon every 5 seconds on ALL active network interfaces
     * (Wi-Fi, hotspot ap0, USB-tethering rndis0, …) so the Windows app can
     * discover us regardless of which network topology is in use.
     */
    fun startBeacon(port: Int, deviceName: String) {
        beaconJob?.cancel()
        // Capabilities advertised in the beacon so Windows can negotiate features
        // without a full WebSocket handshake.
        val capabilities = "\"clipboard\":true,\"input\":true,\"fileTransfer\":true," +
                "\"dragDrop\":true,\"notifications\":true,\"sms\":true," +
                "\"camera\":true,\"screenMirror\":true,\"battery\":true"
        val payload = """
            |{"type":"opencontinuity","deviceName":"$deviceName",
            |"port":$port,"platform":"android",
            |"protocolVersion":"2.0.0",
            |"capabilities":{$capabilities}}
        """.trimMargin().replace("\n", "").toByteArray(Charsets.UTF_8)

        beaconJob = beaconScope.launch {
            while (isActive) {
                try {
                    NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { iface ->
                        if (!iface.isUp || iface.isLoopback || iface.isVirtual) return@forEach
                        iface.interfaceAddresses.forEach addr@{ ifAddr ->
                            val broadcast = ifAddr.broadcast ?: return@addr
                            try {
                                DatagramSocket().use { sock ->
                                    sock.broadcast = true
                                    sock.send(DatagramPacket(payload, payload.size, broadcast, BEACON_PORT))
                                }
                            } catch (_: Exception) { /* ignore per-interface errors */ }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Beacon send error", e)
                }
                delay(5_000)
            }
        }
        Log.i(TAG, "UDP beacon started on port $BEACON_PORT")
    }

    fun stopBeacon() {
        beaconJob?.cancel()
        beaconJob = null
        Log.i(TAG, "UDP beacon stopped")
    }
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Start advertising this device's service
     */
    fun startAdvertising(port: Int, deviceName: String) {
        if (_isAdvertising.value) {
            Log.w(TAG, "Already advertising")
            return
        }

        serverPort = port

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$SERVICE_NAME-$deviceName"
            serviceType = SERVICE_TYPE
            setPort(port)
            // Add attributes for device identification
            setAttribute("deviceName", deviceName)
            setAttribute("platform", "android")
            setAttribute("version", "1.0.0")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Service registered: ${info.serviceName}")
                _isAdvertising.value = true
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service registration failed: $errorCode")
                _isAdvertising.value = false
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "Service unregistered: ${info.serviceName}")
                _isAdvertising.value = false
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service unregistration failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register service", e)
        }
    }

    /**
     * Stop advertising this device's service
     */
    fun stopAdvertising() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister service", e)
            }
        }
        registrationListener = null
        _isAdvertising.value = false
    }

    /**
     * Start discovering other OpenContinuity devices
     */
    fun startDiscovery() {
        if (discoveryListener != null) {
            Log.w(TAG, "Already discovering")
            return
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service found: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "Service lost: ${serviceInfo.serviceName}")
                _discoveredServices.value = _discoveredServices.value.filter {
                    it.serviceName != serviceInfo.serviceName
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    /**
     * Stop discovering devices
     */
    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop discovery", e)
            }
        }
        discoveryListener = null
        _discoveredServices.value = emptyList()
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                Log.i(TAG, "Service resolved: ${resolvedInfo.serviceName} at ${resolvedInfo.host}:${resolvedInfo.port}")

                val discoveredService = DiscoveredService(
                    serviceName = resolvedInfo.serviceName,
                    host = resolvedInfo.host,
                    port = resolvedInfo.port,
                    deviceName = resolvedInfo.attributes["deviceName"]?.decodeToString() ?: "Unknown",
                    platform = resolvedInfo.attributes["platform"]?.decodeToString() ?: "unknown"
                )

                val currentList = _discoveredServices.value.toMutableList()
                currentList.removeAll { it.serviceName == discoveredService.serviceName }
                currentList.add(discoveredService)
                _discoveredServices.value = currentList
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Service resolve failed: ${serviceInfo.serviceName}, error: $errorCode")
            }
        })
    }

    /**
     * Get the device's current IP address on WiFi
     */
    fun getLocalIpAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress

            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }

            // Fallback: iterate network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains('.') == true) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local IP address", e)
        }
        return null
    }

    fun cleanup() {
        stopBeacon()
        stopAdvertising()
        stopDiscovery()
    }
}

/**
 * Represents a discovered OpenContinuity service
 */
data class DiscoveredService(
    val serviceName: String,
    val host: InetAddress,
    val port: Int,
    val deviceName: String,
    val platform: String
)
