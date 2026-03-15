/**
 * Tray Manager - handles system tray icon and menu
 */

import { Tray, Menu, nativeImage, BrowserWindow, app } from 'electron';
import path from 'path';
import { ConnectionManager, ConnectionState } from '../connection/ConnectionManager';

export class TrayManager {
  private tray: Tray;
  private mainWindow: BrowserWindow;
  private connectionManager: ConnectionManager;
  private onQuit?: () => void;

  constructor(mainWindow: BrowserWindow, connectionManager: ConnectionManager, onQuit?: () => void) {
    this.mainWindow = mainWindow;
    this.connectionManager = connectionManager;
    this.onQuit = onQuit;

    // Create tray icon
    const iconPath = path.join(__dirname, '../../../../resources/icon.png');
    let icon: Electron.NativeImage;
    
    try {
      icon = nativeImage.createFromPath(iconPath);
    } catch {
      // Fallback: create a simple icon
      icon = nativeImage.createEmpty();
    }

    this.tray = new Tray(icon);
    this.tray.setToolTip('OpenContinuity');

    // Set up context menu
    this.updateMenu();

    // Handle double-click on tray icon
    this.tray.on('double-click', () => {
      this.showWindow();
    });

    // Update menu when connection state changes
    connectionManager.on('stateChanged', () => {
      this.updateMenu();
    });
  }

  getTray(): Tray {
    return this.tray;
  }

  private updateMenu(): void {
    const state = this.connectionManager.getState();
    const isConnected = state.status === 'connected';

    const contextMenu = Menu.buildFromTemplate([
      {
        label: 'OpenContinuity',
        enabled: false
      },
      { type: 'separator' },
      {
        label: isConnected ? `Connected to ${state.deviceName}` : 'Not Connected',
        enabled: false,
        icon: this.getStatusIcon(state.status)
      },
      { type: 'separator' },
      {
        label: 'Open Dashboard',
        click: () => this.showWindow()
      },
      {
        label: isConnected ? 'Disconnect' : 'Connect...',
        click: () => {
          if (isConnected) {
            this.connectionManager.disconnect();
          } else {
            this.showWindow();
          }
        }
      },
      { type: 'separator' },
      {
        label: 'Settings',
        click: () => {
          this.showWindow();
          this.mainWindow.webContents.send('navigate', '/settings');
        }
      },
      { type: 'separator' },
      {
        label: 'Quit',
        click: () => {
          if (this.onQuit) this.onQuit();
          app.quit();
        }
      }
    ]);

    this.tray.setContextMenu(contextMenu);
  }

  private showWindow(): void {
    if (this.mainWindow.isMinimized()) {
      this.mainWindow.restore();
    }
    this.mainWindow.show();
    this.mainWindow.focus();
  }

  private getStatusIcon(status: string): Electron.NativeImage | undefined {
    // Could return different icons based on status
    // For now, return undefined (no icon)
    return undefined;
  }
}
