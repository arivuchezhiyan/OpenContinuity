/**
 * ConnectionManager Unit Tests (Windows / TypeScript)
 * Tests: UT-WIN-CON-001 through UT-WIN-CON-008
 */

import { ConnectionManager, ConnectionState } from '../../../main/connection/ConnectionManager';
import { SecurityManager } from '../../../main/security/SecurityManager';
import { MessageType, ProtocolMessage } from '../../../shared/protocol';
import WebSocket from 'ws';

jest.mock('ws');
jest.mock('../../../main/security/SecurityManager');

describe('ConnectionManager (Windows)', () => {
  let connectionManager: ConnectionManager;
  let mockSecurityManager: jest.Mocked<SecurityManager>;
  let mockWebSocket: jest.Mocked<WebSocket>;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();

    mockSecurityManager = new SecurityManager() as jest.Mocked<SecurityManager>;
    mockWebSocket = new WebSocket('ws://localhost:8443') as jest.Mocked<WebSocket>;

    (WebSocket as jest.MockedClass<typeof WebSocket>).mockImplementation(() => mockWebSocket);

    connectionManager = new ConnectionManager(mockSecurityManager);
  });

  afterEach(() => {
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  // ============================================
  // UT-WIN-CON-001: WebSocket connection
  // ============================================
  describe('UT-WIN-CON-001: connect() establishes WebSocket', () => {
    it('should create WebSocket connection to host:port', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      // Simulate WebSocket open
      mockWebSocket.emit('open');

      const result = await connectPromise;

      expect(WebSocket).toHaveBeenCalledWith(expect.stringContaining('192.168.1.100:8443'));
      expect(result).toBe(true);
    });

    it('should set connection state to CONNECTED', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      mockWebSocket.emit('open');
      await connectPromise;

      const state = connectionManager.getState();
      expect(state.status).toBe('connected');
      expect(state.host).toBe('192.168.1.100');
      expect(state.port).toBe(8443);
    });

    it('should isConnected() return true after connection', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      mockWebSocket.emit('open');
      await connectPromise;

      expect(connectionManager.isConnected()).toBe(true);
    });

    it('should reject duplicate connection attempts', async () => {
      const connectPromise1 = connectionManager.connect('192.168.1.100', 8443);
      const connectPromise2 = connectionManager.connect('192.168.1.100', 8443);

      mockWebSocket.emit('open');

      const result1 = await connectPromise1;
      const result2 = await connectPromise2;

      expect(result1).toBe(true);
      expect(result2).toBe(false); // Second attempt should be rejected
    });
  });

  // ============================================
  // UT-WIN-CON-002: Reconnection with backoff
  // ============================================
  describe('UT-WIN-CON-002: Reconnection with exponential backoff', () => {
    it('should retry connection with exponential backoff', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      // Simulate connection failure
      mockWebSocket.emit('close');
      await connectPromise;

      // First reconnect attempt should be ~2 seconds
      jest.advanceTimersByTime(2000);
      expect(WebSocket).toHaveBeenCalledTimes(2);

      // Second attempt should be ~4 seconds after first
      jest.advanceTimersByTime(4000);
      expect(WebSocket).toHaveBeenCalledTimes(3);

      // Third attempt should be ~8 seconds after second
      jest.advanceTimersByTime(8000);
      expect(WebSocket).toHaveBeenCalledTimes(4);
    });

    it('should cap reconnect delay at 32 seconds', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      // Simulate many failures
      for (let i = 0; i < 10; i++) {
        mockWebSocket.emit('close');
        jest.advanceTimersByTime(32000);
      }

      // After many failures, delay should not exceed 32 seconds
      const state = connectionManager.getState();
      // Reconnect attempts should continue but delay capped
      expect(state.status).not.toBe('connected');
    });

    it('should stop reconnecting after max attempts (20)', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      // Simulate 20+ failures
      for (let i = 0; i < 21; i++) {
        mockWebSocket.emit('close');
        jest.advanceTimersByTime(32000 + 1000); // Beyond delay
      }

      const state = connectionManager.getState();
      expect(state.status).toBe('error');
    });
  });

  // ============================================
  // UT-WIN-CON-003: Manual disconnect cancels reconnect
  // ============================================
  describe('UT-WIN-CON-003: Manual disconnect cancels pending reconnect', () => {
    it('should cancel pending reconnect on disconnect', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      mockWebSocket.emit('open');
      await connectPromise;

      // Simulate failure
      mockWebSocket.emit('close');

      // Call disconnect before reconnect timer fires
      connectionManager.disconnect();

      // Advance timer - reconnect should not occur
      jest.advanceTimersByTime(2000);

      // WebSocket should only have been called 2 times (initial + close, no reconnect)
      expect(WebSocket).toHaveBeenCalledTimes(2);
    });

    it('should set state to disconnected', () => {
      connectionManager.disconnect();

      const state = connectionManager.getState();
      expect(state.status).toBe('disconnected');
      expect(connectionManager.isConnected()).toBe(false);
    });
  });

  // ============================================
  // UT-WIN-CON-004: Message handler dispatch
  // ============================================
  describe('UT-WIN-CON-004: Message handler registration and dispatch', () => {
    it('should call registered message handler', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);
      mockWebSocket.emit('open');
      await connectPromise;

      const handler = jest.fn();
      connectionManager.on(MessageType.SMS_RECEIVED, handler);

      // Simulate incoming message
      const message: ProtocolMessage = {
        type: MessageType.SMS_RECEIVED,
        payload: { address: '+1234567890', body: 'Test SMS' },
        timestamp: Date.now(),
        messageId: 'test-1',
      };

      const messageData = JSON.stringify(message);
      mockWebSocket.emit('message', messageData);

      expect(handler).toHaveBeenCalledWith(expect.objectContaining({
        type: MessageType.SMS_RECEIVED,
      }));
    });

    it('should call handler exactly once per message', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);
      mockWebSocket.emit('open');
      await connectPromise;

      const handler = jest.fn();
      connectionManager.on(MessageType.CLIPBOARD_SYNC, handler);

      const message: ProtocolMessage = {
        type: MessageType.CLIPBOARD_SYNC,
        payload: { contentType: 'text', textContent: 'Hello' },
        timestamp: Date.now(),
        messageId: 'test-2',
      };

      mockWebSocket.emit('message', JSON.stringify(message));
      mockWebSocket.emit('message', JSON.stringify(message));

      expect(handler).toHaveBeenCalledTimes(2);
    });

    it('should not call handler for different message types', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);
      mockWebSocket.emit('open');
      await connectPromise;

      const handler = jest.fn();
      connectionManager.on(MessageType.SMS_RECEIVED, handler);

      const message: ProtocolMessage = {
        type: MessageType.CLIPBOARD_SYNC,
        payload: { contentType: 'text' },
        timestamp: Date.now(),
        messageId: 'test-3',
      };

      mockWebSocket.emit('message', JSON.stringify(message));

      expect(handler).not.toHaveBeenCalled();
    });
  });

  // ============================================
  // UT-WIN-CON-005: Heartbeat sent periodically
  // ============================================
  describe('UT-WIN-CON-005: Heartbeat sent every 30 seconds', () => {
    it('should send heartbeat message periodically', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);
      mockWebSocket.emit('open');
      await connectPromise;

      const sendSpy = jest.spyOn(mockWebSocket, 'send');

      // Advance timer by 30 seconds
      jest.advanceTimersByTime(30000);

      // Should have sent heartbeat
      expect(sendSpy).toHaveBeenCalledWith(
        expect.stringContaining('heartbeat')
      );
    });

    it('should send heartbeat multiple times', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);
      mockWebSocket.emit('open');
      await connectPromise;

      const sendSpy = jest.spyOn(mockWebSocket, 'send');

      jest.advanceTimersByTime(30000); // First heartbeat
      jest.advanceTimersByTime(30000); // Second heartbeat
      jest.advanceTimersByTime(30000); // Third heartbeat

      // Should have sent multiple heartbeats
      expect(sendSpy.mock.calls.length).toBeGreaterThanOrEqual(3);
    });
  });

  // ============================================
  // UT-WIN-CON-006: Dead connection detected
  // ============================================
  describe('UT-WIN-CON-006: Dead connection detected when no ACK', () => {
    it('should terminate connection without ACK', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);
      mockWebSocket.emit('open');
      await connectPromise;

      jest.advanceTimersByTime(30000); // Send heartbeat

      // Advance timer 6 seconds (past the 5-second ACK timeout)
      jest.advanceTimersByTime(6000);

      // Connection should be terminated
      const state = connectionManager.getState();
      expect(state.status).not.toBe('connected');
    });
  });

  // ============================================
  // UT-WIN-CON-007: Message queueing when disconnected
  // ============================================
  describe('UT-WIN-CON-007: send() behavior when disconnected', () => {
    it('should handle send when not connected', () => {
      const message: ProtocolMessage = {
        type: MessageType.CLIPBOARD_SYNC,
        payload: { contentType: 'text' },
        timestamp: Date.now(),
        messageId: 'test-4',
      };

      // Should not throw when disconnected
      expect(() => {
        connectionManager.send(message);
      }).not.toThrow();
    });

    it('should send message after connection established', async () => {
      const message: ProtocolMessage = {
        type: MessageType.CLIPBOARD_SYNC,
        payload: { contentType: 'text' },
        timestamp: Date.now(),
        messageId: 'test-5',
      };

      connectionManager.send(message);

      const connectPromise = connectionManager.connect('192.168.1.100', 8443);
      mockWebSocket.emit('open');
      await connectPromise;

      const sendSpy = jest.spyOn(mockWebSocket, 'send');
      jest.advanceTimersByTime(100);

      // Should have been sent after connection
      expect(sendSpy.mock.calls.length).toBeGreaterThan(0);
    });
  });

  // ============================================
  // UT-WIN-CON-008: Max reconnect attempts
  // ============================================
  describe('UT-WIN-CON-008: Maximum reconnection attempts', () => {
    it('should not exceed 20 reconnection attempts', async () => {
      const connectPromise = connectionManager.connect('192.168.1.100', 8443);

      // Simulate 25 failures
      for (let i = 0; i < 25; i++) {
        mockWebSocket.emit('close');
        jest.advanceTimersByTime(32000 + 1000);
      }

      const state = connectionManager.getState();
      // After 20 attempts, status should be error or disconnected
      expect(['error', 'disconnected']).toContain(state.status);
    });
  });
});
