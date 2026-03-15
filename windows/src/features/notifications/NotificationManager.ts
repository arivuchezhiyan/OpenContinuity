/**
 * Notification Manager - handles notification synchronization
 */

import { Notification, nativeImage, BrowserWindow } from 'electron';
import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { MessageType, generateMessageId } from '../../shared/protocol';

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

export class NotificationManager extends EventEmitter {
  private connectionManager: ConnectionManager;
  private notifications: Map<string, PhoneNotification> = new Map();
  private isEnabled: boolean = true;
  private maxNotifications: number = 50;

  constructor(connectionManager: ConnectionManager) {
    super();
    this.connectionManager = connectionManager;

    // Listen for notifications from phone (using updated protocol type names)
    connectionManager.on('message:notification_post', (message) => {
      this.handleNotificationPosted(message.payload);
    });

    connectionManager.on('message:notification_dismiss', (message) => {
      this.handleNotificationRemoved(message.payload);
    });
  }

  setEnabled(enabled: boolean): void {
    this.isEnabled = enabled;
  }

  getNotifications(): PhoneNotification[] {
    return Array.from(this.notifications.values())
      .sort((a, b) => b.timestamp - a.timestamp);
  }

  getNotification(id: string): PhoneNotification | undefined {
    return this.notifications.get(id);
  }

  // Public methods for external use
  addNotification(payload: any): void {
    this.handleNotificationPosted(payload);
  }

  removeNotification(id: string): void {
    this.handleNotificationRemoved({ notificationId: id });
  }

  private handleNotificationPosted(payload: any): void {
    if (!this.isEnabled) return;

    const notification: PhoneNotification = {
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
    BrowserWindow.getAllWindows().forEach(window => {
      window.webContents.send('notification:received', notification);
    });

    this.emit('notificationReceived', notification);
  }

  private handleNotificationRemoved(payload: any): void {
    const id = payload.notificationId || payload.id;
    if (id && this.notifications.has(id)) {
      this.notifications.delete(id);

      // Notify renderer
      BrowserWindow.getAllWindows().forEach(window => {
        window.webContents.send('notification:removed', { id });
      });

      this.emit('notificationRemoved', id);
    }
  }

  private showDesktopNotification(notification: PhoneNotification): void {
    try {
      let icon: Electron.NativeImage | undefined;
      
      if (notification.iconBase64) {
        try {
          icon = nativeImage.createFromBuffer(
            Buffer.from(notification.iconBase64, 'base64')
          );
        } catch {
          // Ignore icon errors
        }
      }

      const desktopNotification = new Notification({
        title: `${notification.appName}: ${notification.title}`,
        body: notification.text,
        icon: icon,
        silent: false
      });

      // Handle notification click
      desktopNotification.on('click', () => {
        this.emit('notificationClicked', notification);
        
        // Show main window
        BrowserWindow.getAllWindows().forEach(window => {
          if (window.isMinimized()) window.restore();
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
    } catch (error) {
      console.error('Error showing desktop notification:', error);
    }
  }

  dismissNotification(notificationId: string): void {
    // Send dismiss command to phone
    this.connectionManager.send({
      type: MessageType.NOTIFICATION_ACTION,
      payload: {
        notificationId,
        action: 'dismiss'
      },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    // Remove locally
    this.notifications.delete(notificationId);
  }

  performAction(notificationId: string, actionId: string): void {
    this.connectionManager.send({
      type: MessageType.NOTIFICATION_ACTION,
      payload: {
        notificationId,
        actionId
      },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });
  }

  private pruneOldNotifications(): void {
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

  clearAll(): void {
    this.notifications.clear();
    
    BrowserWindow.getAllWindows().forEach(window => {
      window.webContents.send('notification:cleared');
    });
  }
}
