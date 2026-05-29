/**
 * Connection Manager - handles WebSocket connection to Android device
 */

import WebSocket from 'ws';
import { EventEmitter } from 'events';
import { SecurityManager } from '../security/SecurityManager';
import { MessageType, ProtocolMessage, HandshakePayload, HandshakeResponsePayload, generateMessageId, PROTOCOL_VERSION } from '../../shared/protocol';

export interface ConnectionState {
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  deviceName?: string;
  host?: string;
  port?: number;
  error?: string;
}

export class ConnectionManager extends EventEmitter {
  private ws: WebSocket | null = null;
  private securityManager: SecurityManager;
  private state: ConnectionState = { status: 'disconnected' };
  private reconnectAttempts = 0;
  private readonly maxReconnectAttempts = 20;
  private readonly reconnectBaseDelay = 2000;
  private heartbeatInterval: NodeJS.Timeout | null = null;
  private heartbeatMsgInterval: NodeJS.Timeout | null = null;
  private reconnectTimeout: NodeJS.Timeout | null = null;
  private connectInProgress = false;
  private lastHost: string | null = null;
  private lastPort: number | null = null;
  private messageHandlers: Map<MessageType, ((message: ProtocolMessage) => void)[]> = new Map();

  constructor(securityManager: SecurityManager) {
    super();
    this.securityManager = securityManager;
  }

  getState(): ConnectionState {
    return { ...this.state };
  }

  async connect(host: string, port: number): Promise<boolean> {
    // Prevent parallel connection attempts
    if (this.connectInProgress) {
      console.log('Connection already in progress — ignoring duplicate call');
      return false;
    }
    if (this.ws && this.ws.readyState === WebSocket.OPEN && this.state.status === 'connected') {
      console.log('Already connected');
      return true;
    }

    // Cancel any pending auto-reconnect since caller is taking over
    this.cancelReconnect();
    this.destroySocket();

    this.connectInProgress = true;
    this.lastHost = host;
    this.lastPort = port;
    this.state = { status: 'connecting', host, port };
    this.emit('stateChanged', this.state);

    return new Promise((resolve) => {
      let settled = false;
      const settle = (ok: boolean) => {
        if (settled) return;
        settled = true;
        this.connectInProgress = false;
        resolve(ok);
      };

      try {
        const hostPart = host.includes(':') ? `[${host}]` : host;
        const url = `ws://${hostPart}:${port}/connect`;
        console.log(`Connecting to ${url}`);

        this.ws = new WebSocket(url, { handshakeTimeout: 8000 });

        this.ws.on('open', () => {
          console.log('Socket open — sending handshake');
          this.reconnectAttempts = 0;
          this.sendHandshake();
          this.startHeartbeat();
        });

        this.ws.on('message', (data) => {
          this.handleMessage(data.toString(), settle);
        });

        this.ws.on('close', () => {
          console.log('WebSocket closed');
          settle(false);
          this.scheduleReconnect();
        });

        this.ws.on('error', (error) => {
          // 'close' always fires after 'error', so scheduleReconnect runs from there
          console.error('WebSocket error:', error.message);
          settle(false);
        });

        // Hard timeout: if handshake not received in 10s, kill and retry
        const handshakeTimeout = setTimeout(() => {
          if (this.state.status !== 'connected') {
            console.warn('Handshake timeout — destroying socket');
            settledAlreadyClosed = true;
            this.destroySocket(false); // don't call scheduleReconnect yet
            settle(false);
            this.scheduleReconnect();
          }
        }, 10000);

        let settledAlreadyClosed = false;

        this.once('connectedInternal', () => {
          clearTimeout(handshakeTimeout);
          settle(true);
        });

      } catch (error: any) {
        console.error('Connect threw:', error);
        this.state = { status: 'error', error: error.message, host, port };
        this.emit('stateChanged', this.state);
        settle(false);
        this.scheduleReconnect();
      }
    });
  }

  /** Cancel a pending reconnect timer */
  private cancelReconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
  }

  /** Destroy the current WebSocket without triggering reconnect from its events */
  private destroySocket(removeListeners = true): void {
    this.stopHeartbeat();
    if (this.ws) {
      if (removeListeners) this.ws.removeAllListeners();
      if (this.ws.readyState !== WebSocket.CLOSED) {
        this.ws.terminate();
      }
      this.ws = null;
    }
  }

  /** Schedule an automatic reconnect with exponential backoff */
  private scheduleReconnect(): void {
    if (this.connectInProgress) return; // already connecting
    const host = this.lastHost;
    const port = this.lastPort;
    if (!host || !port) return;

    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log('Max reconnect attempts reached — resetting backoff and continuing');
      this.reconnectAttempts = 0;
      // Don't clear host/port — keep retrying with reset backoff
    }

    this.reconnectAttempts++;
    const delay = Math.min(this.reconnectBaseDelay * this.reconnectAttempts, 30000);
    console.log(`Scheduling reconnect ${this.reconnectAttempts}/${this.maxReconnectAttempts} in ${delay}ms`);

    this.state = { status: 'disconnected' };
    this.emit('stateChanged', this.state);
    this.emit('disconnected');

    this.reconnectTimeout = setTimeout(() => {
      this.reconnectTimeout = null;
      if (this.state.status === 'disconnected') {
        this.connect(host, port);
      }
    }, delay);
  }

  disconnect(): void {
    this.cancelReconnect();
    this.lastHost = null;
    this.lastPort = null;
    this.reconnectAttempts = 0;
    this.connectInProgress = false;
    this.destroySocket();
    this.state = { status: 'disconnected' };
    this.emit('stateChanged', this.state);
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN && this.state.status === 'connected';
  }

  private sendHandshake(): void {
    const handshake: HandshakePayload = {
      deviceName: require('os').hostname(),
      deviceType: 'windows',
      protocolVersion: PROTOCOL_VERSION,
      publicKey: this.securityManager.getPublicKeyBase64(),
      features: [
        'clipboard', 'file_transfer', 'notifications', 'sms',
        'camera', 'screen_mirror', 'battery', 'input_control', 'touchpad'
      ]
    };

    this.send({
      type: MessageType.HANDSHAKE,
      payload: handshake,
      timestamp: Date.now(),
      messageId: generateMessageId()
    });
  }

  private handleMessage(data: string, settle?: (ok: boolean) => void): void {
    try {
      const message: ProtocolMessage = JSON.parse(data);
      console.log(`Received message: ${message.type}`);

      if (message.type === MessageType.HANDSHAKE_RESPONSE) {
        const payload = message.payload as any;
        if (payload.accepted) {
          this.state = {
            status: 'connected',
            deviceName: payload.deviceName,
            host: this.lastHost!,
            port: this.lastPort!
          };
          this.emit('stateChanged', this.state);
          this.emit('connectedInternal'); // resolves the connect() promise
          this.emit('connected');         // backwards-compat for feature handlers
        }
        return;
      }

      if (message.type === MessageType.HEARTBEAT) {
        this.send({
          type: MessageType.HEARTBEAT_ACK,
          payload: {},
          timestamp: Date.now(),
          messageId: generateMessageId()
        });
        return;
      }

      const handlers = this.messageHandlers.get(message.type);
      if (handlers) {
        handlers.forEach(handler => handler(message));
      }
      this.emit('message', message);
      this.emit(`message:${message.type}`, message);

    } catch (error) {
      console.error('Failed to parse message:', error);
    }
  }

  private startHeartbeat(): void {
    // WebSocket-level ping every 10 s — keeps the radio awake on phone hotspot
    this.heartbeatInterval = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        try { (this.ws as any).ping(); } catch (_) { }
      }
    }, 10000);

    // Application-level HEARTBEAT every 20 s — reference SAVED so stopHeartbeat can clear it
    this.heartbeatMsgInterval = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.send({
          type: MessageType.HEARTBEAT,
          payload: {},
          timestamp: Date.now(),
          messageId: generateMessageId()
        });
      }
    }, 20000);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatInterval) {
      clearInterval(this.heartbeatInterval);
      this.heartbeatInterval = null;
    }
    if (this.heartbeatMsgInterval) {
      clearInterval(this.heartbeatMsgInterval);
      this.heartbeatMsgInterval = null;
    }
  }

  send(message: ProtocolMessage): void {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(message));
    } else {
      console.warn('Cannot send message: WebSocket not connected');
    }
  }

  registerHandler(type: MessageType, handler: (message: ProtocolMessage) => void): void {
    if (!this.messageHandlers.has(type)) {
      this.messageHandlers.set(type, []);
    }
    this.messageHandlers.get(type)!.push(handler);
  }

  unregisterHandler(type: MessageType, handler: (message: ProtocolMessage) => void): void {
    const handlers = this.messageHandlers.get(type);
    if (handlers) {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
      }
    }
  }

  // Auto-register all message types for event emission
  private setupMessageTypeHandlers(): void {
    // Ensure all MessageType values can be listened to with on(`message:${type}`)
    // This is handled by emitting in handleMessage(), but we validate it works
  }
}
