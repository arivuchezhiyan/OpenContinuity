"use strict";
/**
 * Notification Manager - handles notification synchronization
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.NotificationManager = void 0;
const electron_1 = require("electron");
const events_1 = require("events");
class NotificationManager extends events_1.EventEmitter {
    connectionManager;
    notifications = new Map();
    isEnabled = true;
    maxNotifications = 50;
    constructor(connectionManager) {
        super();
        this.connectionManager = connectionManager;
        // Listen for notifications from phone
        connectionManager.on('message:notification_posted', (message) => {
            this.handleNotificationPosted(message.payload);
        });
        connectionManager.on('message:notification_removed', (message) => {
            this.handleNotificationRemoved(message.payload);
        });
    }
    setEnabled(enabled) {
        this.isEnabled = enabled;
    }
    getNotifications() {
        return Array.from(this.notifications.values())
            .sort((a, b) => b.timestamp - a.timestamp);
    }
    getNotification(id) {
        return this.notifications.get(id);
    }
    handleNotificationPosted(payload) {
        if (!this.isEnabled)
            return;
        const notification = {
            id: payload.id || `notif-${Date.now()}`,
            packageName: payload.packageName || 'unknown',
            appName: payload.appName || 'Unknown App',
            title: payload.title || '',
            text: payload.text || '',
            timestamp: payload.timestamp || Date.now(),
            iconBase64: payload.iconBase64,
            actions: payload.actions
        };
        // Store notification
        this.notifications.set(notification.id, notification);
        // Clean up old notifications if needed
        this.pruneOldNotifications();
        // Show desktop notification
        this.showDesktopNotification(notification);
        // Notify renderer
        electron_1.BrowserWindow.getAllWindows().forEach(window => {
            window.webContents.send('notification:received', notification);
        });
        this.emit('notificationReceived', notification);
    }
    handleNotificationRemoved(payload) {
        const id = payload.id;
        if (id && this.notifications.has(id)) {
            this.notifications.delete(id);
            // Notify renderer
            electron_1.BrowserWindow.getAllWindows().forEach(window => {
                window.webContents.send('notification:removed', { id });
            });
            this.emit('notificationRemoved', id);
        }
    }
    showDesktopNotification(notification) {
        try {
            let icon;
            if (notification.iconBase64) {
                try {
                    icon = electron_1.nativeImage.createFromBuffer(Buffer.from(notification.iconBase64, 'base64'));
                }
                catch {
                    // Ignore icon errors
                }
            }
            const desktopNotification = new electron_1.Notification({
                title: `${notification.appName}: ${notification.title}`,
                body: notification.text,
                icon: icon,
                silent: false
            });
            // Handle notification click
            desktopNotification.on('click', () => {
                this.emit('notificationClicked', notification);
                // Show main window
                electron_1.BrowserWindow.getAllWindows().forEach(window => {
                    if (window.isMinimized())
                        window.restore();
                    window.show();
                    window.focus();
                    window.webContents.send('navigate', '/notifications');
                });
            });
            // Handle action buttons (if supported)
            desktopNotification.on('action', (event, index) => {
                if (notification.actions && notification.actions[index]) {
                    this.performAction(notification.id, notification.actions[index].id);
                }
            });
            desktopNotification.show();
        }
        catch (error) {
            console.error('Error showing desktop notification:', error);
        }
    }
    dismissNotification(notificationId) {
        // Send dismiss command to phone
        this.connectionManager.send({
            type: 'notification_action',
            payload: {
                notificationId,
                action: 'dismiss'
            },
            timestamp: Date.now(),
            messageId: `msg-${Date.now()}`
        });
        // Remove locally
        this.notifications.delete(notificationId);
    }
    performAction(notificationId, actionId) {
        this.connectionManager.send({
            type: 'notification_action',
            payload: {
                notificationId,
                actionId
            },
            timestamp: Date.now(),
            messageId: `msg-${Date.now()}`
        });
    }
    pruneOldNotifications() {
        if (this.notifications.size > this.maxNotifications) {
            // Get notifications sorted by timestamp
            const sorted = Array.from(this.notifications.entries())
                .sort((a, b) => b[1].timestamp - a[1].timestamp);
            // Keep only the newest ones
            this.notifications.clear();
            sorted.slice(0, this.maxNotifications).forEach(([id, notif]) => {
                this.notifications.set(id, notif);
            });
        }
    }
    clearAll() {
        this.notifications.clear();
        electron_1.BrowserWindow.getAllWindows().forEach(window => {
            window.webContents.send('notification:cleared');
        });
    }
}
exports.NotificationManager = NotificationManager;
//# sourceMappingURL=NotificationManager.js.map