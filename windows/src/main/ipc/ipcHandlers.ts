/**
 * IPC Handlers - handles communication between main and renderer processes
 */

import { ipcMain, dialog, BrowserWindow, clipboard, shell } from 'electron';
import { ConnectionManager } from '../connection/ConnectionManager';
import { DiscoveryManager } from '../discovery/DiscoveryManager';
import { SecurityManager } from '../security/SecurityManager';
import { ClipboardManager } from '../../features/clipboard/ClipboardManager';
import { NotificationManager } from '../../features/notifications/NotificationManager';
import { BatteryManager } from '../../features/battery/BatteryManager';
import { SmsManager } from '../../features/sms/SmsManager';
import { WebcamManager } from '../../features/webcam/WebcamManager';
import { ScreenMirrorManager } from '../../features/screenmirror/ScreenMirrorManager';
import { ScreenshotSyncManager } from '../../features/screenmirror/ScreenshotSyncManager';
import { TouchpadManager } from '../../features/touchpad/TouchpadManager';
import { FileTransferManager } from '../../features/filetransfer/FileTransferManager';
import { DragDropManager } from '../../features/dragdrop/DragDropManager';
import { InputControlManager } from '../../features/input/InputControlManager';
import { MessageType, generateMessageId } from '../../shared/protocol';
import QRCode from 'qrcode';
import os from 'os';
import Store from 'electron-store';
import path from 'path';
import fs from 'fs';

interface AppSettings {
  autoStart: boolean;
  clipboardSync: boolean;
  notificationSync: boolean;
  smsSync: boolean;
  screenshotSync: boolean;
  downloadFolder: string;
}

interface Managers {
  connectionManager: ConnectionManager;
  discoveryManager: DiscoveryManager;
  securityManager: SecurityManager;
  clipboardManager: ClipboardManager;
  notificationManager: NotificationManager;
  batteryManager: BatteryManager;
  smsManager: SmsManager;
  webcamManager: WebcamManager;
  screenMirrorManager: ScreenMirrorManager;
  screenshotSyncManager: ScreenshotSyncManager;
}

export function setupIPC(managers: Managers): void {
  const {
    connectionManager,
    discoveryManager,
    securityManager,
    clipboardManager,
    notificationManager,
    batteryManager,
    smsManager,
    webcamManager,
    screenMirrorManager,
    screenshotSyncManager,
  } = managers;

  // Additional managers
  const fileTransferManager = new FileTransferManager(connectionManager);
  const dragDropManager = new DragDropManager(connectionManager, securityManager.getDeviceId ? securityManager.getDeviceId() : 'windows-device');
  const inputControlManager = new InputControlManager();
  const touchpadManager = new TouchpadManager(connectionManager, inputControlManager);
  const settingsStore = new Store<AppSettings>({
    defaults: {
      autoStart: false,
      clipboardSync: true,
      notificationSync: true,
      smsSync: true,
      screenshotSync: true,
      downloadFolder: path.join(os.homedir(), 'Downloads', 'OpenContinuity')
    }
  });

  // Keep discovery active from main process so it does not depend on UI route lifecycle.
  discoveryManager.startDiscovery();

  // ==================== Connection ====================

  ipcMain.handle('connection:connect', async (_event, host: string, port: number) => {
    return await connectionManager.connect(host, port);
  });

  ipcMain.handle('connection:disconnect', async () => {
    connectionManager.disconnect();
  });

  ipcMain.handle('connection:getState', async () => {
    return connectionManager.getState();
  });

  // Forward connection state changes to renderer
  connectionManager.on('stateChanged', (state) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('connection:stateChanged', state);
    });
  });

  // ==================== Discovery ====================

  ipcMain.handle('discovery:getDevices', async () => {
    return discoveryManager.getDiscoveredDevices();
  });

  ipcMain.handle('discovery:start', async () => {
    discoveryManager.startDiscovery();
  });

  ipcMain.handle('discovery:stop', async () => {
    discoveryManager.stopDiscovery();
  });

  // Forward device discoveries to renderer
  discoveryManager.on('deviceFound', (device) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('discovery:deviceFound', device);
    });
  });

  // Forward device list cleared (IP change / network switch) to renderer
  discoveryManager.on('devicesCleared', () => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('discovery:devicesCleared');
    });
  });

  // ==================== Pairing ====================

  ipcMain.handle('pairing:generateQR', async () => {
    const pairingData = {
      host: getLocalIP(),
      port: 8765,
      httpPort: 8766,
      publicKey: securityManager.getPublicKeyBase64(),
      pairingCode: securityManager.generatePairingCode(),
      deviceName: os.hostname()
    };

    const qrDataUrl = await QRCode.toDataURL(JSON.stringify(pairingData));
    return qrDataUrl;
  });

  ipcMain.handle('pairing:scanQR', async () => {
    const result = await dialog.showMessageBox({
      type: 'info',
      title: 'Scan QR Code',
      message: 'Use your phone to scan the QR code displayed on this screen.',
      buttons: ['OK']
    });
    return result;
  });

  // ==================== Clipboard ====================

  ipcMain.handle('clipboard:get', async () => {
    return {
      text: clipboard.readText(),
      html: clipboard.readHTML(),
      hasImage: clipboard.availableFormats().includes('image/png')
    };
  });

  ipcMain.handle('clipboard:set', async (_event, content: any) => {
    if (content.text) {
      clipboard.writeText(content.text);
    } else if (content.html) {
      clipboard.writeHTML(content.html);
    }
  });

  // Forward clipboard sync to renderer
  connectionManager.on(`message:${MessageType.CLIPBOARD_SYNC}`, (message) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('clipboard:sync', message.payload);
    });
  });

  // ==================== File Transfer ====================

  ipcMain.handle('file:select', async () => {
    const result = await dialog.showOpenDialog({
      properties: ['openFile', 'multiSelections']
    });

    if (result.canceled) return null;
    return result.filePaths;
  });

  ipcMain.handle('file:send', async (_event, filePath: string) => {
    return await fileTransferManager.sendFile(filePath);
  });

  ipcMain.handle('file:getTransfers', async () => {
    return fileTransferManager.getActiveTransfers();
  });

  ipcMain.handle('file:openDownloadFolder', async () => {
    fileTransferManager.openDownloadFolder();
  });

  ipcMain.handle('file:open', async (_event, transferId: string) => {
    fileTransferManager.openFile(transferId);
  });

  ipcMain.handle('file:showInFolder', async (_event, transferId: string) => {
    fileTransferManager.showInFolder(transferId);
  });

  ipcMain.handle('file:saveAs', async (_event, transferId: string) => {
    return await fileTransferManager.saveAs(transferId);
  });

  // Forward file transfer updates from FileTransferManager to renderer
  // (FileTransferManager handles all the protocol messages and state management)
  fileTransferManager.on('transferUpdate', (transfer) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('file:transferUpdate', transfer);
    });
  });

  // ==================== SMS ====================

  ipcMain.handle('sms:getConversations', async () => {
    return smsManager.requestConversations();
  });

  ipcMain.handle('sms:getMessages', async (_event, threadId: number) => {
    return smsManager.requestMessages(threadId);
  });

  ipcMain.handle('sms:send', async (_event, address: string, body: string) => {
    smsManager.sendSms(address, body);
    return true;
  });

  // Forward SMS events from SmsManager to renderer
  smsManager.on('conversationsUpdated', (conversations: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('sms:conversations', conversations);
    });
  });

  smsManager.on('messagesUpdated', (messages: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('sms:messages', messages);
    });
  });

  smsManager.on('smsReceived', (payload: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('sms:received', payload);
    });
  });

  smsManager.on('sendResult', (payload: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('sms:sendResult', payload);
    });
  });

  // ==================== Notifications ====================

  ipcMain.handle('notifications:get', async () => {
    return notificationManager.getNotifications();
  });

  ipcMain.handle('notifications:dismiss', async (_event, id: string) => {
    notificationManager.dismissNotification(id);
  });

  // Forward notifications to renderer
  connectionManager.on(`message:${MessageType.NOTIFICATION_POST}`, (message) => {
    notificationManager.addNotification(message.payload);
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('notification:received', message.payload);
    });
  });

  connectionManager.on(`message:${MessageType.NOTIFICATION_DISMISS}`, (message) => {
    notificationManager.removeNotification(message.payload.notificationId || message.payload.id);
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('notifications:removed', { id: message.payload.notificationId || message.payload.id });
    });
  });

  // ==================== Device Status ====================

  ipcMain.handle('device:getStatus', async () => {
    return batteryManager.getDeviceStatus();
  });

  // Forward battery updates from BatteryManager to renderer
  batteryManager.on('batteryUpdate', (payload: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('battery:update', payload);
    });
  });

  batteryManager.on('deviceStatusUpdate', (payload: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('device:status', payload);
    });
  });

  // ==================== Settings ====================

  ipcMain.handle('settings:get', async () => {
    return settingsStore.store;
  });

  ipcMain.handle('settings:set', async (_event, settings: Partial<AppSettings>) => {
    for (const [key, value] of Object.entries(settings)) {
      settingsStore.set(key as keyof AppSettings, value as any);
    }
  });

  // ==================== Input Control ====================

  ipcMain.handle('input:send', async (_event, event: any) => {
    connectionManager.send({
      type: MessageType.INPUT_EVENT,
      payload: event,
      timestamp: Date.now(),
      messageId: generateMessageId()
    });
  });

  // Handle input events (keyboard from remote)
  // Note: Touchpad events are handled by the TouchpadManager instance
  connectionManager.on(`message:${MessageType.INPUT_EVENT}`, (message) => {
    const payload = message.payload;
    if (payload.eventType === 'key' || payload.eventType === 'text') {
      inputControlManager.handleKeyboardEvent({
        type: 'keypress',
        key: payload.text || payload.keyCode?.toString() || ''
      });
    }
  });

  // ==================== Streaming ====================

  ipcMain.handle('stream:startCamera', async () => {
    webcamManager.startStream();
  });

  ipcMain.handle('stream:stopCamera', async () => {
    webcamManager.stopStream();
  });

  ipcMain.handle('stream:startScreen', async () => {
    screenMirrorManager.startMirror();
  });

  ipcMain.handle('stream:stopScreen', async () => {
    screenMirrorManager.stopMirror();
  });

  // Forward stream frames from managers to renderer
  webcamManager.on('frame', (payload: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('stream:frame', { ...payload, streamType: 'camera' });
    });
  });

  screenMirrorManager.on('frame', (payload: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('stream:frame', { ...payload, streamType: 'screen' });
    });
  });

  // Forward screenshot events to renderer
  screenshotSyncManager.on('screenshotSaved', (info: any) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('screenshot:saved', info);
    });
  });

  // ==================== Window Control ====================

  ipcMain.handle('window:minimize', async () => {
    BrowserWindow.getFocusedWindow()?.minimize();
  });

  ipcMain.handle('window:maximize', async () => {
    const win = BrowserWindow.getAllWindows()[0];
    if (win) {
      if (win.isMinimized()) win.restore();
      if (!win.isVisible()) win.show();
      win.focus();
    }
  });

  ipcMain.handle('window:close', async () => {
    BrowserWindow.getFocusedWindow()?.close();
  });

  // ==================== Note Maker ====================

  ipcMain.handle('note:sendSync', async (_event, payload: any) => {
    connectionManager.send({
      type: MessageType.NOTE_SYNC,
      payload: payload,
      timestamp: Date.now(),
      messageId: generateMessageId()
    });
  });

  // Forward note sync events to renderer
  connectionManager.on(`message:${MessageType.NOTE_SYNC}`, (message) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('note:sync', message.payload);
    });
  });

  // ==================== Drag & Drop ====================

  ipcMain.handle('dragdrop:startEdgeDrag', async (_event, filePath: string, edgeX: number, edgeY: number) => {
    return dragDropManager.startEdgeDrag(filePath, edgeX, edgeY);
  });

  ipcMain.handle('dragdrop:acceptDrop', async (_event, dragId: string) => {
    dragDropManager.acceptDrop(dragId);
  });

  ipcMain.handle('dragdrop:rejectDrop', async (_event, dragId: string) => {
    dragDropManager.rejectDrop(dragId);
  });

  ipcMain.handle('dragdrop:getPendingDrags', async () => {
    return dragDropManager.getPendingDrags();
  });

  // Forward drag-drop events to renderer
  dragDropManager.on('dragReceived', (drag) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('dragdrop:received', drag);
    });
  });

  dragDropManager.on('dragStarted', (drag) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('dragdrop:started', drag);
    });
  });

  dragDropManager.on('dragAcceptedByPeer', (drag) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('dragdrop:acceptedByPeer', drag);
    });
  });

  dragDropManager.on('dragRejectedByPeer', (drag) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('dragdrop:rejectedByPeer', drag);
    });
  });

  dragDropManager.on('dragCancelled', (drag) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('dragdrop:cancelled', drag);
    });
  });

  dragDropManager.on('dropAccepted', (drag) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('dragdrop:dropAccepted', drag);
    });
  });

  dragDropManager.on('dropRejected', (drag) => {
    BrowserWindow.getAllWindows().forEach(win => {
      win.webContents.send('dragdrop:dropRejected', drag);
    });
  });
}

function getLocalIP(): string {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}
