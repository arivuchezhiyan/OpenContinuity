/**
 * SMS Manager - handles SMS operations via protocol messages to Android
 * Manages conversation/message caching and send logic.
 */

import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import {
    MessageType,
    SmsConversation,
    SmsMessage,
    SmsSendPayload,
    SmsSendResultPayload,
    generateMessageId,
} from '../../shared/protocol';

export class SmsManager extends EventEmitter {
    private connectionManager: ConnectionManager;
    private conversations: SmsConversation[] = [];
    private messagesByThread: Map<number, SmsMessage[]> = new Map();

    constructor(connectionManager: ConnectionManager) {
        super();
        this.connectionManager = connectionManager;
        this.setupHandlers();
    }

    private setupHandlers(): void {
        // Conversations list response
        this.connectionManager.registerHandler(MessageType.SMS_CONVERSATIONS, (message) => {
            const convs = Array.isArray(message.payload)
                ? message.payload
                : [];
            this.conversations = convs as SmsConversation[];
            this.emit('conversationsUpdated', this.conversations);
        });

        // Messages for a thread
        this.connectionManager.registerHandler(MessageType.SMS_MESSAGES, (message) => {
            const msgs = message.payload as SmsMessage[];
            if (msgs && msgs.length > 0) {
                const threadId = msgs[0].threadId;
                this.messagesByThread.set(threadId, msgs);
            }
            this.emit('messagesUpdated', msgs);
        });

        // Incoming SMS notification
        this.connectionManager.registerHandler(MessageType.SMS_RECEIVED, (message) => {
            this.emit('smsReceived', message.payload);
        });

        // Send result
        this.connectionManager.registerHandler(MessageType.SMS_SEND_RESULT, (message) => {
            this.emit('sendResult', message.payload as SmsSendResultPayload);
        });
    }

    /** Request conversation list from Android. Returns cached data immediately. */
    requestConversations(): SmsConversation[] {
        this.connectionManager.send({
            type: MessageType.SMS_CONVERSATIONS,
            payload: {},
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });
        return this.conversations;
    }

    /** Request messages for a specific thread. Returns cached data immediately. */
    requestMessages(threadId: number): SmsMessage[] {
        this.connectionManager.send({
            type: MessageType.SMS_MESSAGES,
            payload: { threadId },
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });
        return this.messagesByThread.get(threadId) || [];
    }

    /** Send an SMS via Android. */
    sendSms(address: string, body: string): void {
        const payload: SmsSendPayload = {
            address,
            body,
            requestId: generateMessageId(),
        };
        this.connectionManager.send({
            type: MessageType.SMS_SEND,
            payload,
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });
    }

    getConversations(): SmsConversation[] {
        return this.conversations;
    }

    getMessages(threadId: number): SmsMessage[] {
        return this.messagesByThread.get(threadId) || [];
    }

    destroy(): void {
        this.removeAllListeners();
    }
}
