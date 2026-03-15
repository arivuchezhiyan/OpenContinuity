/**
 * Notification Manager - handles notification synchronization
 */
import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
export interface PhoneNotification {
    id: string;
    packageName: string;
    appName: string;
    title: string;
    text: string;
    timestamp: number;
    iconBase64?: string;
    actions?: NotificationAction[];
}
export interface NotificationAction {
    id: string;
    title: string;
}
export declare class NotificationManager extends EventEmitter {
    private connectionManager;
    private notifications;
    private isEnabled;
    private maxNotifications;
    constructor(connectionManager: ConnectionManager);
    setEnabled(enabled: boolean): void;
    getNotifications(): PhoneNotification[];
    getNotification(id: string): PhoneNotification | undefined;
    private handleNotificationPosted;
    private handleNotificationRemoved;
    private showDesktopNotification;
    dismissNotification(notificationId: string): void;
    performAction(notificationId: string, actionId: string): void;
    private pruneOldNotifications;
    clearAll(): void;
}
//# sourceMappingURL=NotificationManager.d.ts.map