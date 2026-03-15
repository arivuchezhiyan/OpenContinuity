/**
 * Screenshot Sync Manager - handles screenshot sync between Android and Windows
 */

import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { MessageType, generateMessageId } from '../../shared/protocol';
import path from 'path';
import fs from 'fs';
import os from 'os';

export class ScreenshotSyncManager extends EventEmitter {
    private connectionManager: ConnectionManager;
    private saveDir: string;

    constructor(connectionManager: ConnectionManager, saveDir?: string) {
        super();
        this.connectionManager = connectionManager;
        this.saveDir = saveDir || path.join(os.homedir(), 'Downloads', 'OpenContinuity', 'Screenshots');
        this.ensureSaveDir();
        this.setupHandlers();
    }

    private ensureSaveDir(): void {
        if (!fs.existsSync(this.saveDir)) {
            fs.mkdirSync(this.saveDir, { recursive: true });
        }
    }

    private setupHandlers(): void {
        // When Android notifies a screenshot is available
        this.connectionManager.registerHandler(MessageType.SCREENSHOT_AVAILABLE, (message) => {
            const payload = message.payload as any;
            this.emit('screenshotAvailable', payload);

            // If screenshot data is included inline, save it directly
            if (payload.imageBase64) {
                this.saveScreenshot(payload.imageBase64, payload.fileName || `screenshot_${Date.now()}.png`);
            } else {
                // Request the screenshot data
                this.requestScreenshot(payload.screenshotId || message.messageId);
            }
        });

        // When we receive screenshot data in response to our request
        this.connectionManager.registerHandler(MessageType.SCREENSHOT_REQUEST, (message) => {
            const payload = message.payload as any;
            if (payload.imageBase64) {
                this.saveScreenshot(payload.imageBase64, payload.fileName || `screenshot_${Date.now()}.png`);
            }
        });
    }

    /** Request a specific screenshot from Android */
    requestScreenshot(screenshotId: string): void {
        this.connectionManager.send({
            type: MessageType.SCREENSHOT_REQUEST,
            payload: { screenshotId },
            timestamp: Date.now(),
            messageId: generateMessageId(),
        });
    }

    /** Save a base64-encoded screenshot to disk */
    private saveScreenshot(base64Data: string, fileName: string): void {
        try {
            const filePath = path.join(this.saveDir, fileName);
            const buffer = Buffer.from(base64Data, 'base64');
            fs.writeFileSync(filePath, buffer);
            this.emit('screenshotSaved', { filePath, fileName });
            console.log(`Screenshot saved: ${filePath}`);
        } catch (error) {
            console.error('Failed to save screenshot:', error);
            this.emit('screenshotError', { error, fileName });
        }
    }

    getSaveDirectory(): string {
        return this.saveDir;
    }

    setSaveDirectory(dir: string): void {
        this.saveDir = dir;
        this.ensureSaveDir();
    }

    destroy(): void {
        this.removeAllListeners();
    }
}
