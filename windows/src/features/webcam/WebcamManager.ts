/**
 * Webcam Manager - handles phone camera as webcam stream lifecycle
 */

import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import {
    MessageType,
    StreamStartPayload,
    StreamStopPayload,
    generateMessageId,
} from '../../shared/protocol';

export class WebcamManager extends EventEmitter {
    private connectionManager: ConnectionManager;
    private isStreaming = false;

    constructor(connectionManager: ConnectionManager) {
        super();
        this.connectionManager = connectionManager;
        this.setupHandlers();
    }

    private setupHandlers(): void {
        this.connectionManager.registerHandler(MessageType.STREAM_OFFER, (message) => {
            const payload = message.payload as any;
            // Only handle camera stream frames
            if (payload.streamType === 'camera' || (!payload.streamType && this.isStreaming)) {
                this.emit('frame', payload);
            }
        });
    }

    startStream(fps = 30, bitrate = 2000000): void {
        const payload: StreamStartPayload = {
            streamType: 'camera',
            fps,
            bitrate,
        };

        this.connectionManager.send({
            type: MessageType.STREAM_START,
            payload,
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });

        this.isStreaming = true;
        this.emit('streamStarted');
    }

    stopStream(): void {
        const payload: StreamStopPayload = {
            streamType: 'camera',
        };

        this.connectionManager.send({
            type: MessageType.STREAM_STOP,
            payload,
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });

        this.isStreaming = false;
        this.emit('streamStopped');
    }

    getIsStreaming(): boolean {
        return this.isStreaming;
    }

    destroy(): void {
        if (this.isStreaming) {
            this.stopStream();
        }
        this.removeAllListeners();
    }
}
