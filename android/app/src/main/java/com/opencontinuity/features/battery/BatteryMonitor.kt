package com.opencontinuity.features.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Battery Monitor - monitors and reports battery status to connected Windows clients
 */
class BatteryMonitor(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "BatteryMonitor"
        private const val UPDATE_INTERVAL_MS = 60_000L // 1 minute
    }

    private var scope: CoroutineScope? = null
    private var periodicJob: Job? = null

    private val _batteryStatus = kotlinx.coroutines.flow.MutableStateFlow<BatteryStatusPayload?>(null)
    val batteryStatus = _batteryStatus.asStateFlow()

    private var lastBatteryLevel = -1
    private var lastChargingState = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                onBatteryChanged(intent)
            }
        }
    }

    fun start() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Register battery change receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)

        // Start periodic status updates
        periodicJob = scope?.launch {
            while (isActive) {
                sendDeviceStatus()
                delay(UPDATE_INTERVAL_MS)
            }
        }

        Log.i(TAG, "Battery monitor started")
    }

    fun stop() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver not registered")
        }
        periodicJob?.cancel()
        scope?.cancel()
        scope = null
        Log.i(TAG, "Battery monitor stopped")
    }

    private fun onBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = (level * 100 / scale.toFloat()).toInt()

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL

        // Only send update if there's a significant change
        if (batteryPct != lastBatteryLevel || isCharging != lastChargingState) {
            lastBatteryLevel = batteryPct
            lastChargingState = isCharging

            scope?.launch {
                sendBatteryStatus(intent)
            }
        }
    }

    private suspend fun sendBatteryStatus(intent: Intent) {
        val batteryStatusPayload = extractBatteryStatus(intent)
        _batteryStatus.value = batteryStatusPayload

        val message = ProtocolMessage(
            type = MessageType.BATTERY_STATUS,
            payload = protocolJson.encodeToJsonElement(batteryStatusPayload)
        )

        connectionManager.broadcast(message)
        Log.d(TAG, "Battery status sent: ${batteryStatusPayload.level}%")
    }

    private fun extractBatteryStatus(intent: Intent): BatteryStatusPayload {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val batteryPct = (level * 100 / scale.toFloat()).toInt()

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "None"
        }

        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val healthStr = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        return BatteryStatusPayload(
            level = batteryPct,
            isCharging = isCharging,
            chargeType = chargeType,
            temperature = temperature,
            health = healthStr
        )
    }

    private suspend fun sendDeviceStatus() {
        // Get battery info
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryStatus = batteryIntent?.let { extractBatteryStatus(it) } ?: return

        // Get storage info
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockSizeLong * stat.blockCountLong
        val availableBytes = stat.blockSizeLong * stat.availableBlocksLong
        val usedBytes = totalBytes - availableBytes

        // Get WiFi info
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val isWifiConnected = wifiManager.isWifiEnabled && wifiInfo.networkId != -1

        val deviceStatus = DeviceStatusPayload(
            deviceName = android.os.Build.MODEL,
            battery = batteryStatus,
            storageUsedBytes = usedBytes,
            storageTotalBytes = totalBytes,
            wifiConnected = isWifiConnected,
            wifiSsid = if (isWifiConnected) wifiInfo.ssid?.removeSurrounding("\"") else null
        )

        val message = ProtocolMessage(
            type = MessageType.DEVICE_STATUS,
            payload = protocolJson.encodeToJsonElement(deviceStatus)
        )

        connectionManager.broadcast(message)
        Log.d(TAG, "Device status sent")
    }
}
