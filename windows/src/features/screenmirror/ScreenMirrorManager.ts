/**
 * Screen Mirror Manager - handles screen mirroring stream lifecycle
 */

import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import {
    MessageType,
    StreamStartPayload,
    StreamStopPayload,
    generateMessageId,
} from '../../shared/protocol';

export class ScreenMirrorManager extends EventEmitter {
    private connectionManager: ConnectionManager;
    private isMirroring = false;

    constructor(connectionManager: ConnectionManager) {
        super();
        this.connectionManager = connectionManager;
        this.setupHandlers();
    }

    private setupHandlers(): void {
        this.connectionManager.registerHandler(MessageType.STREAM_OFFER, (message) => {
            const payload = message.payload as any;
            // Only handle screen mirror frames
            if (payload.streamType === 'screen') {
                this.emit('frame', payload);
            }
        });
    }

    startMirror(fps = 15, bitrate = 1000000): void {
        const payload: StreamStartPayload = {
            streamType: 'screen',
            fps,
            bitrate,
        };

        this.connectionManager.send({
            type: MessageType.STREAM_START,
            payload,
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });

        this.isMirroring = true;
        this.emit('mirrorStarted');
    }

    stopMirror(): void {
        const payload: StreamStopPayload = {
            streamType: 'screen',
        };

        this.connectionManager.send({
            type: MessageType.STREAM_STOP,
            payload,
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });

        this.isMirroring = false;
        this.emit('mirrorStopped');
    }

    getIsMirroring(): boolean {
        return this.isMirroring;
    }

    destroy(): void {
        if (this.isMirroring) {
            this.stopMirror();
        }
        this.removeAllListeners();
    }
}
