/**
 * Shared Protocol Types for OpenContinuity
 * These types define the message structure between Android and Windows apps
 * Keep in sync with android/app/.../core/protocol/Protocol.kt
 * Keep in sync with /shared/protocol.ts (root)
 */

export enum MessageType {
  // Connection & Pairing
  HANDSHAKE = 'handshake',
  HANDSHAKE_RESPONSE = 'handshake_response',
  PAIRING_REQUEST = 'pairing_request',
  PAIRING_RESPONSE = 'pairing_response',
  PAIRING_COMPLETE = 'pairing_complete',
  HEARTBEAT = 'heartbeat',
  HEARTBEAT_ACK = 'heartbeat_ack',
  DISCONNECT = 'disconnect',

  // Clipboard
  CLIPBOARD_SYNC = 'clipboard_sync',
  CLIPBOARD_ACK = 'clipboard_ack',

  // File Transfer
  FILE_TRANSFER_REQUEST = 'file_transfer_request',
  FILE_TRANSFER_ACCEPT = 'file_transfer_accept',
  FILE_TRANSFER_REJECT = 'file_transfer_reject',
  FILE_TRANSFER_PROGRESS = 'file_transfer_progress',
  FILE_TRANSFER_COMPLETE = 'file_transfer_complete',
  FILE_TRANSFER_ERROR = 'file_transfer_error',

  // Notifications
  NOTIFICATION_POST = 'notification_post',
  NOTIFICATION_DISMISS = 'notification_dismiss',
  NOTIFICATION_ACTION = 'notification_action',

  // SMS
  SMS_CONVERSATIONS = 'sms_conversations',
  SMS_MESSAGES = 'sms_messages',
  SMS_SEND = 'sms_send',
  SMS_SEND_RESULT = 'sms_send_result',
  SMS_RECEIVED = 'sms_received',

  // Camera / Screen Mirror
  STREAM_START = 'stream_start',
  STREAM_STOP = 'stream_stop',
  STREAM_OFFER = 'stream_offer',
  STREAM_ANSWER = 'stream_answer',
  STREAM_ICE_CANDIDATE = 'stream_ice_candidate',

  // Battery & Status
  DEVICE_STATUS = 'device_status',
  BATTERY_STATUS = 'battery_status',

  // Input Control
  INPUT_EVENT = 'input_event',
  TOUCHPAD_EVENT = 'touchpad_event',

  // File Chunk Transfer (AirDrop-style chunked data)
  FILE_CHUNK = 'file_chunk',

  // Cross-Device Drag and Drop
  DRAG_FILE_START = 'drag_file_start',
  DRAG_FILE_CANCEL = 'drag_file_cancel',
  DRAG_FILE_DROP = 'drag_file_drop',

  // Session
  SESSION_RESTORE = 'session_restore',
  SESSION_RESTORE_ACK = 'session_restore_ack',

  // Screenshot Sync
  SCREENSHOT_AVAILABLE = 'screenshot_available',
  SCREENSHOT_REQUEST = 'screenshot_request',

  // Note Maker Sync
  NOTE_SYNC = 'note_sync',

  // Error
  ERROR = 'error'
}

export interface ProtocolMessage<T = any> {
  type: MessageType;
  payload: T;
  timestamp: number;
  messageId: string;
  signature?: string;
}

// Alias for compatibility
export type Message<T = any> = ProtocolMessage<T>;

// ============== Pairing Messages ==============

export type DeviceType = 'android' | 'windows' | 'unknown';

export interface DeviceCapabilities {
  clipboard: boolean;
  input: boolean;
  fileTransfer: boolean;
  dragDrop: boolean;
  notifications: boolean;
  sms: boolean;
  camera: boolean;
  screenMirror: boolean;
  battery: boolean;
}

export interface HandshakePayload {
  deviceName: string;
  deviceType: DeviceType;
  protocolVersion: string;
  publicKey: string;
  features: string[];
  capabilities?: DeviceCapabilities;
  deviceId?: string;
}

export interface HandshakeResponsePayload {
  accepted: boolean;
  deviceName: string;
  deviceType: DeviceType;
  protocolVersion: string;
  publicKey: string;
  sessionToken?: string;
  features: string[];
}

export interface PairingRequestPayload {
  pairingCode: string;
  deviceName: string;
  publicKey: string;
}

export interface PairingResponsePayload {
  success: boolean;
  sessionToken?: string;
  errorMessage?: string;
}

// ============== Clipboard Messages ==============

export type ClipboardContentType = 'text' | 'html' | 'image';

export interface ClipboardSyncPayload {
  contentType: ClipboardContentType;
  textContent?: string;
  htmlContent?: string;
  imageBase64?: string;
  imageMimeType?: string;
  deviceId?: string;
  contentHash?: string;
}

// ============== File Transfer Messages ==============

export interface FileTransferRequestPayload {
  transferId: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  checksum?: string;
}

export interface FileTransferProgressPayload {
  transferId: string;
  bytesTransferred: number;
  totalBytes: number;
  progress: number;
}

export interface FileTransferCompletePayload {
  transferId: string;
  success: boolean;
  filePath?: string;
  errorMessage?: string;
}

export interface FileChunkPayload {
  transferId: string;
  sequence: number;
  totalChunks: number;
  data: string; // base64
  checksum: string;
}

export interface DragFileStartPayload {
  dragId: string;
  sourceDeviceId: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  edgeX: number;
  edgeY: number;
}

export interface DragFileDropPayload {
  dragId: string;
  targetDeviceId: string;
  accepted: boolean;
  errorMessage?: string;
}

export interface SessionRestorePayload {
  sessionToken: string;
  deviceId: string;
  deviceName: string;
}

// ============== Notification Messages ==============

export interface NotificationAction {
  actionId: string;
  title: string;
}

export interface NotificationPayload {
  notificationId: string;
  packageName: string;
  appName: string;
  title?: string;
  text?: string;
  subText?: string;
  iconBase64?: string;
  timestamp: number;
  actions: NotificationAction[];
}

// ============== SMS Messages ==============

export interface SmsConversation {
  threadId: number;
  address: string;
  contactName?: string;
  snippet?: string;
  timestamp: number;
  unreadCount: number;
}

export interface SmsMessage {
  id: number;
  threadId: number;
  address: string;
  body: string;
  timestamp: number;
  isIncoming: boolean;
  isRead: boolean;
}

export interface SmsSendPayload {
  address: string;
  body: string;
  requestId: string;
}

export interface SmsSendResultPayload {
  requestId: string;
  success: boolean;
  errorMessage?: string;
}

// ============== Device Status Messages ==============

export interface BatteryStatusPayload {
  level: number;
  isCharging: boolean;
  chargeType: string;
  temperature: number;
  health: string;
}

export interface DeviceStatusPayload {
  deviceName: string;
  battery: BatteryStatusPayload;
  storageUsedBytes: number;
  storageTotalBytes: number;
  wifiConnected: boolean;
  wifiSsid?: string;
}

// ============== Input Control Messages ==============

export type InputEventType = 'tap' | 'long_press' | 'swipe' | 'scroll' | 'key' | 'text';

export interface InputEventPayload {
  eventType: InputEventType;
  x?: number;
  y?: number;
  keyCode?: number;
  text?: string;
  scrollDelta?: number;
}

export type TouchpadEventType = 'move' | 'click' | 'right_click' | 'scroll' | 'drag_start' | 'drag_end';

export interface TouchpadEventPayload {
  eventType: TouchpadEventType;
  deltaX?: number;
  deltaY?: number;
  fingers: number;
  scrollDelta?: number;
}

// ============== Stream Messages ==============

export interface StreamStartPayload {
  streamType: 'screen' | 'camera';
  width?: number;
  height?: number;
  fps?: number;
  bitrate?: number;
}

export interface StreamStopPayload {
  streamType: 'screen' | 'camera';
}

// ============== Note Maker Sync ==============

export type NoteTool = 'pen' | 'eraser' | 'cursor';
export type NoteSyncAction = 'stroke' | 'clear' | 'pan' | 'zoom';

export interface NotePoint {
  x: number;
  y: number;
}

export interface NoteSyncPayload {
  action: NoteSyncAction;
  tool: NoteTool;
  color: string;
  thickness: number;
  points: NotePoint[];
  panX?: number;
  panY?: number;
  zoom?: number;
}

// ============== Error Messages ==============

export interface ErrorPayload {
  code: string;
  message: string;
  details?: string;
}

// ============== Utility ==============

export const PROTOCOL_VERSION = '2.0.0';

export function generateMessageId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

export function createMessage<T>(type: MessageType, payload: T): ProtocolMessage<T> {
  return {
    type,
    payload,
    timestamp: Date.now(),
    messageId: generateMessageId()
  };
}

export const protocolJson = {
  parse: (text: string) => JSON.parse(text) as ProtocolMessage,
  stringify: (message: ProtocolMessage) => JSON.stringify(message)
};
