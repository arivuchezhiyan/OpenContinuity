package com.opencontinuity.features.sms

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.opencontinuity.core.connection.ConnectionManager
import com.opencontinuity.core.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * SMS Manager - handles SMS operations
 */
class SmsDataManager(
    private val context: Context,
    private val connectionManager: ConnectionManager
) {
    companion object {
        private const val TAG = "SmsDataManager"
    }

    private val _conversations = MutableStateFlow<List<SmsConversation>>(emptyList())
    val conversations = _conversations.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        // Register handlers
        connectionManager.registerHandler(MessageType.SMS_CONVERSATIONS) { sessionId, _ ->
            handleConversationsRequest(sessionId)
        }

        connectionManager.registerHandler(MessageType.SMS_MESSAGES) { sessionId, message ->
            handleMessagesRequest(sessionId, message)
        }

        connectionManager.registerHandler(MessageType.SMS_SEND) { sessionId, message ->
            handleSmsSend(sessionId, message)
        }

        Log.i(TAG, "SMS data manager started")
    }

    fun stop() {
        scope.cancel()
        Log.i(TAG, "SMS data manager stopped")
    }

    private suspend fun handleConversationsRequest(sessionId: String) {
        val conversations = getConversations()
        _conversations.value = conversations

        val message = ProtocolMessage(
            type = MessageType.SMS_CONVERSATIONS,
            payload = protocolJson.encodeToJsonElement(conversations)
        )

        connectionManager.sendToSession(sessionId, message)
    }

    private suspend fun handleMessagesRequest(sessionId: String, message: ProtocolMessage) {
        val request = protocolJson.decodeFromJsonElement<Map<String, Long>>(message.payload)
        val threadId = request["threadId"] ?: return

        val messages = getMessagesForThread(threadId)

        val responseMessage = ProtocolMessage(
            type = MessageType.SMS_MESSAGES,
            payload = protocolJson.encodeToJsonElement(messages)
        )

        connectionManager.sendToSession(sessionId, responseMessage)
    }

    private suspend fun handleSmsSend(sessionId: String, message: ProtocolMessage) {
        val sendRequest = protocolJson.decodeFromJsonElement<SmsSendPayload>(message.payload)

        val success = sendSms(sendRequest.address, sendRequest.body)

        val result = SmsSendResultPayload(
            requestId = sendRequest.requestId,
            success = success,
            errorMessage = if (!success) "Failed to send SMS" else null
        )

        val responseMessage = ProtocolMessage(
            type = MessageType.SMS_SEND_RESULT,
            payload = protocolJson.encodeToJsonElement(result)
        )

        connectionManager.sendToSession(sessionId, responseMessage)
    }

    private fun getConversations(limit: Int = 50): List<SmsConversation> {
        val conversations = mutableListOf<SmsConversation>()

        try {
            val uri = Telephony.Sms.Conversations.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.Conversations.THREAD_ID,
                Telephony.Sms.Conversations.SNIPPET,
                Telephony.Sms.Conversations.MESSAGE_COUNT
            )

            context.contentResolver.query(
                uri, projection, null, null,
                "${Telephony.Sms.Conversations.DATE} DESC"
            )?.use { cursor ->
                val threadIdIndex = cursor.getColumnIndex(Telephony.Sms.Conversations.THREAD_ID)
                val snippetIndex = cursor.getColumnIndex(Telephony.Sms.Conversations.SNIPPET)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val threadId = cursor.getLong(threadIdIndex)
                    val snippet = cursor.getString(snippetIndex)

                    // Get the address for this thread
                    val threadInfo = getThreadInfo(threadId)
                    if (threadInfo != null) {
                        conversations.add(
                            SmsConversation(
                                threadId = threadId,
                                address = threadInfo.address,
                                contactName = getContactName(threadInfo.address),
                                snippet = snippet,
                                timestamp = threadInfo.timestamp,
                                unreadCount = threadInfo.unreadCount
                            )
                        )
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get conversations", e)
        }

        return conversations
    }

    private fun getThreadInfo(threadId: Long): ThreadInfo? {
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
            )
            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = arrayOf(threadId.toString())

            context.contentResolver.query(
                uri, projection, selection, selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS))
                    val date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE))

                    // Count unread messages
                    val unreadCount = countUnreadMessages(threadId)

                    return ThreadInfo(address, date, unreadCount)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get thread info", e)
        }
        return null
    }

    private fun countUnreadMessages(threadId: Long): Int {
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0"
            val selectionArgs = arrayOf(threadId.toString())

            context.contentResolver.query(
                uri, arrayOf("COUNT(*)"), selection, selectionArgs, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to count unread messages", e)
        }
        return 0
    }

    private fun getMessagesForThread(threadId: Long, limit: Int = 100): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ
            )
            val selection = "${Telephony.Sms.THREAD_ID} = ?"
            val selectionArgs = arrayOf(threadId.toString())

            context.contentResolver.query(
                uri, projection, selection, selectionArgs,
                "${Telephony.Sms.DATE} DESC LIMIT $limit"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(cursor.getColumnIndex(Telephony.Sms._ID))
                    val address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS))
                    val body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY))
                    val date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE))
                    val type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE))
                    val read = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.READ))

                    messages.add(
                        SmsMessage(
                            id = id,
                            threadId = threadId,
                            address = address ?: "",
                            body = body ?: "",
                            timestamp = date,
                            isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                            isRead = read == 1
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get messages", e)
        }

        return messages.reversed() // Return in chronological order
    }

    private fun getContactName(phoneNumber: String): String? {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )

            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get contact name", e)
        }
        return null
    }

    private fun sendSms(address: String, body: String): Boolean {
        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(address, null, body, null, null)
            Log.i(TAG, "SMS sent to $address")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
            false
        }
    }

    private data class ThreadInfo(
        val address: String,
        val timestamp: Long,
        val unreadCount: Int
    )
}
