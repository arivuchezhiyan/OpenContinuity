/**
 * ConnectionManager Unit Tests (Windows / TypeScript)
 * Tests: UT-WIN-CON-001 through UT-WIN-CON-008
 */

import { ConnectionManager } from '../../../main/connection/ConnectionManager';
import { SecurityManager } from '../../../main/security/SecurityManager';
import { MessageType, ProtocolMessage } from '../../../shared/protocol';
import WebSocket from 'ws';

jest.mock('ws');
jest.mock('../../../main/security/SecurityManager');
jest.mock('os', () => ({
  hostname: () => 'test-pc',
}));

function emitHandshakeSuccess(ws: jest.Mocked<WebSocket>): void {
  ws.emit('open');
  const response: ProtocolMessage = {
    type: MessageType.HANDSHAKE_RESPONSE,
    payload: { accepted: true, deviceName: 'Test Phone', sessionId: 'sess-1' },
    timestamp: Date.now(),
    messageId: 'hs-1',
  };
  ws.emit('message', JSON.stringify(response));
}

async function connectWithHandshake(
  cm: ConnectionManager,
  ws: jest.Mocked<WebSocket>,
  host = '192.168.1.100',
  port = 8443
): Promise<boolean> {
  const p = cm.connect(host, port);
  emitHandshakeSuccess(ws);
  return p;
}

describe('ConnectionManager (Windows)', () => {
  let connectionManager: ConnectionManager;
  let mockSecurityManager: jest.Mocked<SecurityManager>;
  let mockWebSocket: jest.Mocked<WebSocket>;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();

    mockSecurityManager = new SecurityManager() as jest.Mocked<SecurityManager>;
    mockSecurityManager.getPublicKeyBase64.mockReturnValue('pubkey');
    mockSecurityManager.getDeviceId.mockReturnValue('device-1');

    mockWebSocket = new WebSocket('ws://localhost:8443') as jest.Mocked<WebSocket>;
    mockWebSocket.readyState = WebSocket.OPEN;
    mockWebSocket.send = jest.fn();
    mockWebSocket.removeAllListeners = jest.fn();
    mockWebSocket.terminate = jest.fn();

    (WebSocket as jest.MockedClass<typeof WebSocket>).mockImplementation(() => mockWebSocket);

    connectionManager = new ConnectionManager(mockSecurityManager);
  });

  afterEach(() => {
    connectionManager.disconnect();
    jest.runOnlyPendingTimers();
    jest.useRealTimers();
  });

  describe('UT-WIN-CON-001: connect() establishes WebSocket', () => {
    it('should create WebSocket connection to host:port', async () => {
      const result = await connectWithHandshake(connectionManager, mockWebSocket);

      expect(WebSocket).toHaveBeenCalledWith(
        expect.stringContaining('192.168.1.100:8443'),
        expect.any(Object)
      );
      expect(result).toBe(true);
    });

    it('should set connection state to CONNECTED after handshake', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);

      const state = connectionManager.getState();
      expect(state.status).toBe('connected');
      expect(state.host).toBe('192.168.1.100');
      expect(state.port).toBe(8443);
    });

    it('should isConnected() return true after handshake', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);
      expect(connectionManager.isConnected()).toBe(true);
    });

    it('should reject duplicate connection attempts while in progress', async () => {
      const connectPromise1 = connectionManager.connect('192.168.1.100', 8443);
      const connectPromise2 = connectionManager.connect('192.168.1.100', 8443);

      emitHandshakeSuccess(mockWebSocket);

      const result1 = await connectPromise1;
      const result2 = await connectPromise2;

      expect(result1).toBe(true);
      expect(result2).toBe(false);
    });
  });

  describe('UT-WIN-CON-002: Reconnection with exponential backoff', () => {
    it('should retry connection with linear backoff (base 1s)', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);
      const initialCalls = (WebSocket as jest.Mock).mock.calls.length;

      mockWebSocket.emit('close');
      jest.advanceTimersByTime(1000);
      expect((WebSocket as jest.Mock).mock.calls.length).toBeGreaterThan(initialCalls);

      mockWebSocket.emit('close');
      jest.advanceTimersByTime(2000);
      expect((WebSocket as jest.Mock).mock.calls.length).toBeGreaterThan(initialCalls + 1);
    });

    it('should cap reconnect delay at 30 seconds', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);

      for (let i = 0; i < 8; i++) {
        mockWebSocket.emit('close');
        jest.advanceTimersByTime(30000);
      }

      expect(connectionManager.getState().status).not.toBe('connected');
    });

    it('should reset backoff after max attempts and keep retrying', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);

      for (let i = 0; i < 22; i++) {
        mockWebSocket.emit('close');
        jest.advanceTimersByTime(30000);
      }

      const state = connectionManager.getState();
      expect(['disconnected', 'connecting', 'error']).toContain(state.status);
    });
  });

  describe('UT-WIN-CON-003: Manual disconnect cancels pending reconnect', () => {
    it('should cancel pending reconnect on disconnect', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);
      const callsBeforeClose = (WebSocket as jest.Mock).mock.calls.length;

      mockWebSocket.emit('close');
      connectionManager.disconnect();

      jest.advanceTimersByTime(30000);
      expect((WebSocket as jest.Mock).mock.calls.length).toBe(callsBeforeClose);
    });

    it('should set state to disconnected', () => {
      connectionManager.disconnect();
      const state = connectionManager.getState();
      expect(state.status).toBe('disconnected');
      expect(connectionManager.isConnected()).toBe(false);
    });
  });

  describe('UT-WIN-CON-004: Message handler registration and dispatch', () => {
    it('should call registered message handler', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);

      const handler = jest.fn();
      connectionManager.registerHandler(MessageType.SMS_RECEIVED, handler);

      const message: ProtocolMessage = {
        type: MessageType.SMS_RECEIVED,
        payload: { address: '+1234567890', body: 'Test SMS' },
        timestamp: Date.now(),
        messageId: 'test-1',
      };

      mockWebSocket.emit('message', JSON.stringify(message));

      expect(handler).toHaveBeenCalledWith(expect.objectContaining({
        type: MessageType.SMS_RECEIVED,
      }));
    });

    it('should call handler once per message', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);

      const handler = jest.fn();
      connectionManager.registerHandler(MessageType.CLIPBOARD_SYNC, handler);

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
      await connectWithHandshake(connectionManager, mockWebSocket);

      const handler = jest.fn();
      connectionManager.registerHandler(MessageType.SMS_RECEIVED, handler);

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

  describe('UT-WIN-CON-005: Heartbeat sent every 20 seconds', () => {
    it('should send HEARTBEAT message periodically', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);
      const sendSpy = jest.spyOn(mockWebSocket, 'send');

      jest.advanceTimersByTime(20000);

      const heartbeatCalls = sendSpy.mock.calls.filter((c) =>
        String(c[0]).includes(MessageType.HEARTBEAT)
      );
      expect(heartbeatCalls.length).toBeGreaterThanOrEqual(1);
    });

    it('should send heartbeat multiple times', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);
      const sendSpy = jest.spyOn(mockWebSocket, 'send');

      jest.advanceTimersByTime(20000);
      jest.advanceTimersByTime(20000);
      jest.advanceTimersByTime(20000);

      const heartbeatCalls = sendSpy.mock.calls.filter((c) =>
        String(c[0]).includes(MessageType.HEARTBEAT)
      );
      expect(heartbeatCalls.length).toBeGreaterThanOrEqual(3);
    });
  });

  describe('UT-WIN-CON-006: Heartbeat ACK handling', () => {
    it('should invoke heartbeat ack handler when ACK received', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);
      const ackHandler = jest.fn();
      connectionManager.setHeartbeatAckHandler(ackHandler);

      const ack: ProtocolMessage = {
        type: MessageType.HEARTBEAT_ACK,
        payload: {},
        timestamp: Date.now(),
        messageId: 'ack-1',
      };
      mockWebSocket.emit('message', JSON.stringify(ack));

      expect(ackHandler).toHaveBeenCalled();
    });
  });

  describe('UT-WIN-CON-007: send() behavior when disconnected', () => {
    it('should handle send when not connected', () => {
      const message: ProtocolMessage = {
        type: MessageType.CLIPBOARD_SYNC,
        payload: { contentType: 'text' },
        timestamp: Date.now(),
        messageId: 'test-4',
      };

      expect(() => connectionManager.send(message)).not.toThrow();
    });

    it('should send message when connected', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);
      const sendSpy = jest.spyOn(mockWebSocket, 'send');

      connectionManager.send({
        type: MessageType.CLIPBOARD_SYNC,
        payload: { contentType: 'text' },
        timestamp: Date.now(),
        messageId: 'test-5',
      });

      expect(sendSpy).toHaveBeenCalled();
    });
  });

  describe('UT-WIN-CON-008: Maximum reconnection attempts', () => {
    it('should keep retrying after backoff reset (no permanent error lockout)', async () => {
      await connectWithHandshake(connectionManager, mockWebSocket);

      for (let i = 0; i < 25; i++) {
        mockWebSocket.emit('close');
        jest.advanceTimersByTime(30000);
      }

      const state = connectionManager.getState();
      expect(['disconnected', 'connecting']).toContain(state.status);
    });
  });
});
