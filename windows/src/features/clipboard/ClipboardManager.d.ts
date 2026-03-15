/**
 * Clipboard Manager - handles clipboard synchronization
 */
import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
export interface ClipboardContent {
    type: 'text' | 'html' | 'image';
    text?: string;
    html?: string;
    imageBase64?: string;
}
export declare class ClipboardManager extends EventEmitter {
    private connectionManager;
    private lastHash;
    private pollInterval;
    private isEnabled;
    private ignoreNextChange;
    constructor(connectionManager: ConnectionManager);
    start(): void;
    stop(): void;
    setEnabled(enabled: boolean): void;
    private checkClipboard;
    private getCurrentContent;
    private hashContent;
    private sendClipboardToPhone;
    private handleIncomingClipboard;
    paste(content: ClipboardContent): void;
}
//# sourceMappingURL=ClipboardManager.d.ts.map