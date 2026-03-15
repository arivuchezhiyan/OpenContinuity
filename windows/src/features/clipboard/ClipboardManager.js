"use strict";
/**
 * Clipboard Manager - handles clipboard synchronization
 */
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.ClipboardManager = void 0;
const electron_1 = require("electron");
const events_1 = require("events");
const crypto_1 = __importDefault(require("crypto"));
class ClipboardManager extends events_1.EventEmitter {
    connectionManager;
    lastHash = '';
    pollInterval = null;
    isEnabled = true;
    ignoreNextChange = false;
    constructor(connectionManager) {
        super();
        this.connectionManager = connectionManager;
        // Listen for clipboard sync from phone
        connectionManager.on('message:clipboard_sync', (message) => {
            this.handleIncomingClipboard(message.payload);
        });
    }
    start() {
        if (this.pollInterval)
            return;
        // Poll clipboard for changes every 500ms
        this.pollInterval = setInterval(() => {
            this.checkClipboard();
        }, 500);
        console.log('Clipboard sync started');
    }
    stop() {
        if (this.pollInterval) {
            clearInterval(this.pollInterval);
            this.pollInterval = null;
        }
        console.log('Clipboard sync stopped');
    }
    setEnabled(enabled) {
        this.isEnabled = enabled;
        if (!enabled) {
            this.stop();
        }
    }
    checkClipboard() {
        if (!this.isEnabled || !this.connectionManager.isConnected())
            return;
        if (this.ignoreNextChange) {
            this.ignoreNextChange = false;
            return;
        }
        try {
            const content = this.getCurrentContent();
            const hash = this.hashContent(content);
            if (hash !== this.lastHash) {
                this.lastHash = hash;
                this.sendClipboardToPhone(content);
            }
        }
        catch (error) {
            console.error('Error checking clipboard:', error);
        }
    }
    getCurrentContent() {
        const text = electron_1.clipboard.readText();
        const html = electron_1.clipboard.readHTML();
        const image = electron_1.clipboard.readImage();
        // Check for image first
        if (!image.isEmpty()) {
            return {
                type: 'image',
                imageBase64: image.toPNG().toString('base64')
            };
        }
        // Check for HTML
        if (html && html !== text) {
            return {
                type: 'html',
                html,
                text
            };
        }
        // Default to text
        return {
            type: 'text',
            text
        };
    }
    hashContent(content) {
        const str = JSON.stringify(content);
        return crypto_1.default.createHash('md5').update(str).digest('hex');
    }
    sendClipboardToPhone(content) {
        console.log('Sending clipboard to phone:', content.type);
        this.connectionManager.send({
            type: 'clipboard_sync',
            payload: content,
            timestamp: Date.now(),
            messageId: `msg-${Date.now()}`
        });
        this.emit('clipboardSent', content);
    }
    handleIncomingClipboard(payload) {
        if (!this.isEnabled)
            return;
        console.log('Received clipboard from phone:', payload.type);
        try {
            // Set flag to ignore next change detection
            this.ignoreNextChange = true;
            if (payload.type === 'image' && payload.imageBase64) {
                const image = electron_1.nativeImage.createFromBuffer(Buffer.from(payload.imageBase64, 'base64'));
                electron_1.clipboard.writeImage(image);
            }
            else if (payload.type === 'html' && payload.html) {
                electron_1.clipboard.writeHTML(payload.html);
            }
            else if (payload.text) {
                electron_1.clipboard.writeText(payload.text);
            }
            // Update hash to prevent echo
            const content = this.getCurrentContent();
            this.lastHash = this.hashContent(content);
            this.emit('clipboardReceived', payload);
            // Notify renderer
            electron_1.BrowserWindow.getAllWindows().forEach(window => {
                window.webContents.send('clipboard:updated', payload);
            });
        }
        catch (error) {
            console.error('Error setting clipboard:', error);
        }
    }
    // Manual paste (for testing)
    paste(content) {
        this.ignoreNextChange = true;
        if (content.type === 'image' && content.imageBase64) {
            const image = electron_1.nativeImage.createFromBuffer(Buffer.from(content.imageBase64, 'base64'));
            electron_1.clipboard.writeImage(image);
        }
        else if (content.type === 'html' && content.html) {
            electron_1.clipboard.writeHTML(content.html);
        }
        else if (content.text) {
            electron_1.clipboard.writeText(content.text);
        }
        this.lastHash = this.hashContent(content);
    }
}
exports.ClipboardManager = ClipboardManager;
//# sourceMappingURL=ClipboardManager.js.map