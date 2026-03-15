package com.opencontinuity.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Restarts the connection foreground service after OEM/background kills.
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ServiceRestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Restarting ConnectionService from alarm")

        val serviceIntent = Intent(context, ConnectionService::class.java).apply {
            action = ConnectionService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}