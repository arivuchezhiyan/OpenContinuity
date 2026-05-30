/**
 * Preload script - exposes safe APIs to the renderer process
 * Matches the WindowAPI interface defined in renderer/types/window.d.ts
 */

import { contextBridge, ipcRenderer } from 'electron';

contextBridge.exposeInMainWorld('api', {
  // ==================== Connection ====================
  connection: {
    connect: (host: string, port: number) => ipcRenderer.invoke('connection:connect', host, port),
    disconnect: () => ipcRenderer.invoke('connection:disconnect'),
    getState: () => ipcRenderer.invoke('connection:getState'),
  },

  // ==================== Discovery ====================
  discovery: {
    getDevices: () => ipcRenderer.invoke('discovery:getDevices'),
    start: () => ipcRenderer.invoke('discovery:start'),
    stop: () => ipcRenderer.invoke('discovery:stop'),
  },

  // ==================== Pairing ====================
  pairing: {
    generateQR: () => ipcRenderer.invoke('pairing:generateQR'),
    scanQR: () => ipcRenderer.invoke('pairing:scanQR'),
  },

  // ==================== Clipboard ====================
  clipboard: {
    get: () => ipcRenderer.invoke('clipboard:get'),
    set: (content: any) => ipcRenderer.invoke('clipboard:set', content),
  },

  // ==================== File Transfer ====================
  file: {
    select: () => ipcRenderer.invoke('file:select'),
    send: (filePath: string) => ipcRenderer.invoke('file:send', filePath),
    getTransfers: () => ipcRenderer.invoke('file:getTransfers'),
    openDownloadFolder: () => ipcRenderer.invoke('file:openDownloadFolder'),
    open: (transferId: string) => ipcRenderer.invoke('file:open', transferId),
    showInFolder: (transferId: string) => ipcRenderer.invoke('file:showInFolder', transferId),
    saveAs: (transferId: string) => ipcRenderer.invoke('file:saveAs', transferId),
  },

  // ==================== SMS ====================
  sms: {
    getConversations: () => ipcRenderer.invoke('sms:getConversations'),
    getMessages: (threadId: number) => ipcRenderer.invoke('sms:getMessages', threadId),
    send: (address: string, body: string) => ipcRenderer.invoke('sms:send', address, body),
  },

  // ==================== Notifications ====================
  notifications: {
    get: () => ipcRenderer.invoke('notifications:get'),
    dismiss: (id: string) => ipcRenderer.invoke('notifications:dismiss', id),
  },

  // ==================== Device Status ====================
  device: {
    getStatus: () => ipcRenderer.invoke('device:getStatus'),
  },

  // ==================== Settings ====================
  settings: {
    get: () => ipcRenderer.invoke('settings:get'),
    set: (settings: any) => ipcRenderer.invoke('settings:set', settings),
  },

  // ==================== Input Control ====================
  input: {
    send: (event: any) => ipcRenderer.invoke('input:send', event),
  },

  // ==================== Streaming ====================
  streaming: {
    startCamera: () => ipcRenderer.invoke('stream:startCamera'),
    stopCamera: () => ipcRenderer.invoke('stream:stopCamera'),
    startScreen: () => ipcRenderer.invoke('stream:startScreen'),
    stopScreen: () => ipcRenderer.invoke('stream:stopScreen'),
  },

  // ==================== Window Control ====================
  window: {
    minimize: () => ipcRenderer.invoke('window:minimize'),
    maximize: () => ipcRenderer.invoke('window:maximize'),
    close: () => ipcRenderer.invoke('window:close'),
  },

  // ==================== Note Maker ====================
  note: {
    sendSync: (payload: any) => ipcRenderer.invoke('note:sendSync', payload),
  },

  // ==================== Event Listeners ====================
  onConnectionStateChanged: (callback: (state: any) => void) => {
    const listener = (_event: any, state: any) => callback(state);
    ipcRenderer.on('connection:stateChanged', listener);
    return () => ipcRenderer.removeListener('connection:stateChanged', listener);
  },

  onDeviceDiscovered: (callback: (device: any) => void) => {
    const listener = (_event: any, device: any) => callback(device);
    ipcRenderer.on('discovery:deviceFound', listener);
    return () => ipcRenderer.removeListener('discovery:deviceFound', listener);
  },

  onDevicesCleared: (callback: () => void) => {
    const listener = () => callback();
    ipcRenderer.on('discovery:devicesCleared', listener);
    return () => ipcRenderer.removeListener('discovery:devicesCleared', listener);
  },

  onBatteryUpdate: (callback: (status: any) => void) => {
    const listener = (_event: any, status: any) => callback(status);
    ipcRenderer.on('battery:update', listener);
    return () => ipcRenderer.removeListener('battery:update', listener);
  },

  onFileTransferUpdate: (callback: (transfer: any) => void) => {
    const listener = (_event: any, transfer: any) => callback(transfer);
    ipcRenderer.on('file:transferUpdate', listener);
    return () => ipcRenderer.removeListener('file:transferUpdate', listener);
  },

  onNotificationReceived: (callback: (notification: any) => void) => {
    const listener = (_event: any, notification: any) => callback(notification);
    ipcRenderer.on('notification:received', listener);
    return () => ipcRenderer.removeListener('notification:received', listener);
  },

  onNotificationRemoved: (callback: (data: { id: string }) => void) => {
    const listener = (_event: any, data: any) => callback(data);
    ipcRenderer.on('notification:removed', listener);
    return () => ipcRenderer.removeListener('notification:removed', listener);
  },

  onSmsReceived: (callback: (sms: any) => void) => {
    const listener = (_event: any, sms: any) => callback(sms);
    ipcRenderer.on('sms:received', listener);
    return () => ipcRenderer.removeListener('sms:received', listener);
  },

  onStreamFrame: (callback: (frame: any) => void) => {
    const listener = (_event: any, frame: any) => callback(frame);
    ipcRenderer.on('stream:frame', listener);
    return () => ipcRenderer.removeListener('stream:frame', listener);
  },

  onClipboardSync: (callback: (content: any) => void) => {
    const listener = (_event: any, content: any) => callback(content);
    ipcRenderer.on('clipboard:sync', listener);
    return () => ipcRenderer.removeListener('clipboard:sync', listener);
  },

  onNoteSync: (callback: (payload: any) => void) => {
    const listener = (_event: any, payload: any) => callback(payload);
    ipcRenderer.on('note:sync', listener);
    return () => ipcRenderer.removeListener('note:sync', listener);
  },
});
