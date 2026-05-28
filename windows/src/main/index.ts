import { app, BrowserWindow, ipcMain, Tray, Menu, nativeImage, shell, Event } from 'electron';
import path from 'path';
import fs from 'fs';
import { ConnectionManager } from './connection/ConnectionManager';
import { DiscoveryManager } from './discovery/DiscoveryManager';
import { SecurityManager } from './security/SecurityManager';
import { ClipboardManager } from '../features/clipboard/ClipboardManager';
import { NotificationManager } from '../features/notifications/NotificationManager';
import { BatteryManager } from '../features/battery/BatteryManager';
import { SmsManager } from '../features/sms/SmsManager';
import { TouchpadManager } from '../features/touchpad/TouchpadManager';
import { InputControlManager } from '../features/input/InputControlManager';
import { WebcamManager } from '../features/webcam/WebcamManager';
import { ScreenMirrorManager } from '../features/screenmirror/ScreenMirrorManager';
import { ScreenshotSyncManager } from '../features/screenmirror/ScreenshotSyncManager';
import { TrayManager } from './tray/TrayManager';
import { SessionManager } from './session/SessionManager';
import { DragDropManager } from '../features/dragdrop/DragDropManager';
import { ProximityUnlockManager } from '../features/unlock/ProximityUnlockManager';
import { ActivityLogManager } from './activity/ActivityLogManager';
import { setupIPC } from './ipc/ipcHandlers';
import os from 'os';

// Keep a global reference to prevent garbage collection
let mainWindow: BrowserWindow | null = null;
let tray: Tray | null = null;
let isQuitting = false;

// Managers
let connectionManager: ConnectionManager;
let discoveryManager: DiscoveryManager;
let securityManager: SecurityManager;
let clipboardManager: ClipboardManager;
let notificationManager: NotificationManager;
let batteryManager: BatteryManager;
let smsManager: SmsManager;
let inputControlManager: InputControlManager;
let touchpadManager: TouchpadManager;
let webcamManager: WebcamManager;
let screenMirrorManager: ScreenMirrorManager;
let screenshotSyncManager: ScreenshotSyncManager;
let sessionManager: SessionManager;
let dragDropManager: DragDropManager;
let activityLogManager: ActivityLogManager;
let proximityUnlockManager: ProximityUnlockManager;
let trayManager: TrayManager;

const isDev = process.env.NODE_ENV === 'development';

async function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 800,
    minWidth: 800,
    minHeight: 600,
    title: 'OpenContinuity',
    icon: path.join(__dirname, '../../../../resources/icon.png'),
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      preload: path.join(__dirname, 'preload.js'),
    },
    show: false,
    frame: true,
    backgroundColor: '#1a1a2e',
  });

  // Load the app
  const rendererPath = path.join(__dirname, '../../renderer/index.html');
  const useDevServer = process.env.NODE_ENV === 'development' && !fs.existsSync(rendererPath);

  if (useDevServer) {
    mainWindow.loadURL('http://localhost:3000');
    mainWindow.webContents.openDevTools();
  } else {
    mainWindow.loadFile(rendererPath);
  }

  // Show window when ready
  mainWindow.once('ready-to-show', () => {
    mainWindow?.show();
  });

  // Handle window close
  mainWindow.on('close', (event: any) => {
    if (mainWindow && !isQuitting) {
      event.preventDefault();
      mainWindow.hide();
    }
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

async function initializeManagers() {
  securityManager = new SecurityManager();
  await securityManager.initialize();

  connectionManager = new ConnectionManager(securityManager);
  discoveryManager = new DiscoveryManager();

  sessionManager = new SessionManager();
  sessionManager.start();

  connectionManager.setHeartbeatAckHandler(() => {
    const active = sessionManager.getAllSessions()[0];
    if (active) sessionManager.touchHeartbeat(active.deviceId);
  });

  connectionManager.setHandshakeCompleteHandler((payload) => {
    const deviceId = payload.deviceId || payload.deviceName;
    const paired = securityManager.getPairedDevices().find((d) => d.deviceId === deviceId);

    if (paired) {
      sessionManager.restoreSession(deviceId, payload.deviceName);
      connectionManager.trySessionRestore({
        sessionToken: paired.sessionToken,
        deviceId: securityManager.getDeviceId(),
        deviceName: os.hostname()
      });
    } else {
      sessionManager.createSession(deviceId, payload.deviceName, payload.features || []);
    }

    if (payload.sessionToken) {
      securityManager.addPairedDevice({
        deviceId,
        deviceName: payload.deviceName,
        publicKey: payload.publicKey,
        sessionToken: payload.sessionToken,
        pairedAt: Date.now(),
        lastConnected: Date.now()
      });
    } else {
      securityManager.updateLastConnected(deviceId);
    }
  });

  sessionManager.on('sessionExpired', (session) => {
    console.warn(`Session expired for ${session.deviceName} — reconnecting`);
    const state = connectionManager.getState();
    if (state.host && state.port && !connectionManager.isConnected()) {
      connectionManager.connect(state.host, state.port);
    }
  });

  dragDropManager = new DragDropManager(connectionManager, securityManager.getDeviceId());
  activityLogManager = new ActivityLogManager();
  proximityUnlockManager = new ProximityUnlockManager(connectionManager);

  clipboardManager = new ClipboardManager(connectionManager);
  clipboardManager.start();

  notificationManager = new NotificationManager(connectionManager);
  batteryManager = new BatteryManager(connectionManager);
  smsManager = new SmsManager(connectionManager);

  inputControlManager = new InputControlManager();
  touchpadManager = new TouchpadManager(connectionManager, inputControlManager);

  webcamManager = new WebcamManager(connectionManager);
  screenMirrorManager = new ScreenMirrorManager(connectionManager);
  screenshotSyncManager = new ScreenshotSyncManager(connectionManager);

  trayManager = new TrayManager(mainWindow!, connectionManager, () => { isQuitting = true; });
  tray = trayManager.getTray();

  setupIPC({
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
    dragDropManager,
    activityLogManager,
    proximityUnlockManager,
  });

  discoveryManager.startDiscovery();
  discoveryManager.on('deviceFound', (device: any) => {
    if (!connectionManager.isConnected()) {
      connectionManager.connect(device.host, device.port);
    }
  });
}

// Wrap in a function to ensure app is accessed when needed if top-level import is flaky
const startApp = () => {
  const gotTheLock = app.requestSingleInstanceLock();

  if (!gotTheLock) {
    app.quit();
  } else {
    app.on('second-instance', () => {
      if (mainWindow) {
        if (mainWindow.isMinimized()) mainWindow.restore();
        mainWindow.show();
        mainWindow.focus();
      }
    });

    app.whenReady().then(async () => {
      await createWindow();
      await initializeManagers();
    });
  }
};

startApp();

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow();
  }
});

app.on('before-quit', () => {
  isQuitting = true;
  connectionManager?.disconnect();
  discoveryManager?.stopDiscovery();
  clipboardManager?.stop();
  batteryManager?.destroy();
  smsManager?.destroy();
  webcamManager?.destroy();
  screenMirrorManager?.destroy();
  screenshotSyncManager?.destroy();
  sessionManager?.stop();
});
