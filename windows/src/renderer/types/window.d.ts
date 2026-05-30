/**
 * Window API type definitions for renderer process
 */

export interface ConnectionState {
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  deviceId?: string;
  deviceName?: string;
  error?: string;
}

export interface DiscoveredDevice {
  name: string;
  host: string;
  port: number;
  platform: string;
}

export interface FileTransfer {
  id: string;
  fileName: string;
  fileSize: number;
  direction: 'send' | 'receive';
  status: 'pending' | 'transferring' | 'completed' | 'failed';
  progress: number;
  localPath?: string;
}

export interface Notification {
  id: string;
  appName: string;
  title: string;
  text: string;
  timestamp: number;
  iconBase64?: string;
}

export interface BatteryStatus {
  level: number;
  isCharging: boolean;
  temperature?: number;
}

export interface SmsConversation {
  threadId: number;
  address: string;
  contactName: string;
  lastMessage: string;
  lastMessageTime: number;
  unreadCount: number;
}

export interface SmsMessage {
  id: number;
  address: string;
  body: string;
  date: number;
  type: 'incoming' | 'outgoing';
}

export interface WindowAPI {
  connection: {
    connect: (host: string, port: number) => Promise<boolean>;
    disconnect: () => void;
    getState: () => Promise<ConnectionState>;
  };

  discovery: {
    getDevices: () => Promise<DiscoveredDevice[]>;
    start: () => void;
    stop: () => void;
  };

  pairing: {
    generateQR: () => Promise<string>;
    scanQR: () => Promise<any>;
  };

  clipboard: {
    get: () => Promise<{ text: string; html: string; hasImage: boolean }>;
    set: (content: any) => Promise<void>;
  };

  file: {
    select: () => Promise<string[] | null>;
    send: (filePath: string) => Promise<string>;
    getTransfers: () => Promise<FileTransfer[]>;
    openDownloadFolder?: () => void;
    open: (transferId: string) => Promise<void>;
    showInFolder: (transferId: string) => Promise<void>;
    saveAs: (transferId: string) => Promise<boolean>;
  };

  sms: {
    getConversations: () => Promise<SmsConversation[]>;
    getMessages: (threadId: number) => Promise<SmsMessage[]>;
    send: (address: string, body: string) => Promise<boolean>;
  };

  notifications: {
    get: () => Promise<Notification[]>;
    dismiss: (id: string) => Promise<void>;
  };

  device: {
    getStatus: () => Promise<any>;
  };

  settings: {
    get: () => Promise<any>;
    set: (settings: any) => Promise<void>;
  };

  input: {
    send: (event: any) => void;
  };

  streaming: {
    startCamera: () => Promise<void>;
    stopCamera: () => Promise<void>;
    startScreen: () => Promise<void>;
    stopScreen: () => Promise<void>;
  };

  note: {
    sendSync: (payload: any) => Promise<void>;
  };

  window: {
    minimize: () => void;
    maximize: () => void;
    close: () => void;
  };

  // Event listeners
  onConnectionStateChanged: (callback: (state: ConnectionState) => void) => () => void;
  onDeviceDiscovered: (callback: (device: DiscoveredDevice) => void) => () => void;
  onDevicesCleared: (callback: () => void) => () => void;
  onBatteryUpdate: (callback: (status: BatteryStatus) => void) => () => void;
  onFileTransferUpdate: (callback: (transfer: FileTransfer) => void) => () => void;
  onNotificationReceived?: (callback: (notification: Notification) => void) => () => void;
  onNotificationRemoved?: (callback: (data: { id: string }) => void) => () => void;
  onSmsReceived?: (callback: (sms: any) => void) => () => void;
  onStreamFrame?: (callback: (frame: any) => void) => () => void;
  onClipboardSync?: (callback: (content: any) => void) => () => void;
  onNoteSync?: (callback: (payload: any) => void) => () => void;
}

declare global {
  interface Window {
    api: WindowAPI;
  }
}

export {};
