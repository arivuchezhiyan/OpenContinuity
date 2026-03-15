package com.opencontinuity.features.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.opencontinuity.OpenContinuityApp
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.encodeToJsonElement

/**
 * SMS Receiver - captures incoming SMS and forwards to PC
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val app = context.applicationContext as? OpenContinuityApp ?: return
        val connectionManager = app.connectionManager

        CoroutineScope(Dispatchers.IO).launch {
            for (smsMessage in messages) {
                try {
                    val message = SmsMessage(
                        id = System.currentTimeMillis(),
                        threadId = 0, // Will be resolved later
                        address = smsMessage.originatingAddress ?: "Unknown",
                        body = smsMessage.messageBody ?: "",
                        timestamp = smsMessage.timestampMillis,
                        isIncoming = true,
                        isRead = false
                    )

                    val protocolMessage = ProtocolMessage(
                        type = MessageType.SMS_RECEIVED,
                        payload = protocolJson.encodeToJsonElement(message)
                    )

                    connectionManager.broadcast(protocolMessage)
                    Log.d(TAG, "SMS received and sent to PC: ${message.address}")

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process incoming SMS", e)
                }
            }
        }
    }
}
