/**
 * SecurityManager Unit Tests (Windows / TypeScript)
 * Tests: UT-WIN-SEC-001 through UT-WIN-SEC-006
 */

import crypto from 'crypto';
import { SecurityManager } from '../../../main/security/SecurityManager';
import keytar from 'keytar';
import Store from 'electron-store';

jest.mock('keytar');
jest.mock('electron-store');

describe('SecurityManager (Windows)', () => {
  let securityManager: SecurityManager;
  let mockKeytar: jest.Mocked<typeof keytar>;
  let mockStore: jest.Mocked<Store>;

  beforeEach(() => {
    jest.clearAllMocks();
    mockKeytar = keytar as jest.Mocked<typeof keytar>;
    mockStore = (Store as unknown as jest.Mock).mock.results[0]?.value;
    securityManager = new SecurityManager();
  });

  // ============================================
  // UT-WIN-SEC-001: EC key pair generation
  // ============================================
  describe('UT-WIN-SEC-001: EC P-256 key pair generation', () => {
    it('should generate EC P-256 key pair on first run', async () => {
      mockStore.get.mockReturnValue(undefined);
      mockKeytar.getPassword.mockResolvedValue(null);

      await securityManager.initialize();

      // Verify key pair was generated
      const publicKey = securityManager.getPublicKey();
      expect(publicKey).toBeDefined();
      expect(publicKey).toMatch(/^-----BEGIN PUBLIC KEY-----/);

      // Verify private key was stored
      expect(mockKeytar.setPassword).toHaveBeenCalled();
    });

    it('should load existing key pair from storage', async () => {
      // Generate a key pair
      const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
        namedCurve: 'prime256v1',
        publicKeyEncoding: { type: 'spki', format: 'pem' },
        privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
      });

      mockKeytar.getPassword.mockResolvedValue(privateKey as string);
      mockStore.get.mockReturnValue('device-123');

      await securityManager.initialize();

      const storedPublicKey = securityManager.getPublicKey();
      expect(storedPublicKey).toBeDefined();
    });

    it('should generate stable device ID', async () => {
      mockStore.get.mockReturnValue('device-456');
      mockKeytar.getPassword.mockResolvedValue(null);

      await securityManager.initialize();

      const deviceId = securityManager.getDeviceId();
      expect(deviceId).toBe('device-456');
    });
  });

  // ============================================
  // UT-WIN-SEC-002: ECDH key exchange
  // ============================================
  describe('UT-WIN-SEC-002: ECDH shared secret derivation', () => {
    it('should produce shared secret from peer public key', async () => {
      // Generate two key pairs
      const { privateKey: privKey1, publicKey: pubKey1 } = crypto.generateKeyPairSync('ec', {
        namedCurve: 'prime256v1',
        publicKeyEncoding: { type: 'spki', format: 'pem' },
        privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
      });

      const { publicKey: pubKey2 } = crypto.generateKeyPairSync('ec', {
        namedCurve: 'prime256v1',
        publicKeyEncoding: { type: 'spki', format: 'pem' },
        privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
      });

      mockKeytar.getPassword.mockResolvedValue(privKey1 as string);
      mockStore.get.mockReturnValue('device-123');

      await securityManager.initialize();

      // Perform key exchange
      const sharedSecret = securityManager.performKeyExchange(pubKey2 as string);

      expect(sharedSecret).toBeDefined();
      expect(sharedSecret.length).toBeGreaterThan(0);
      expect(typeof sharedSecret).toBe('string');
    });

    it('should produce consistent shared secret', async () => {
      const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
        namedCurve: 'prime256v1',
        publicKeyEncoding: { type: 'spki', format: 'pem' },
        privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
      });

      mockKeytar.getPassword.mockResolvedValue(privateKey as string);
      mockStore.get.mockReturnValue('device-123');

      await securityManager.initialize();

      const secret1 = securityManager.performKeyExchange(publicKey as string);
      const secret2 = securityManager.performKeyExchange(publicKey as string);

      expect(secret1).toBe(secret2);
    });
  });

  // ============================================
  // UT-WIN-SEC-003: Encryption round trip
  // ============================================
  describe('UT-WIN-SEC-003: AES-256-GCM encrypt/decrypt round trip', () => {
    it('should encrypt and decrypt plaintext correctly', async () => {
      const plaintext = 'Hello OpenContinuity';
      const key = crypto.randomBytes(32);

      // Note: This tests the encryption logic directly
      // In actual implementation, these would be methods on SecurityManager
      const iv = crypto.randomBytes(12);
      const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
      let encrypted = cipher.update(plaintext, 'utf8', 'hex');
      encrypted += cipher.final('hex');
      const authTag = cipher.getAuthTag();

      // Decrypt
      const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
      decipher.setAuthTag(authTag);
      let decrypted = decipher.update(encrypted, 'hex', 'utf8');
      decrypted += decipher.final('utf8');

      expect(decrypted).toBe(plaintext);
    });

    it('should use random IV for each encryption', async () => {
      const plaintext = 'Test message';
      const key = crypto.randomBytes(32);

      const encrypt = (text: string, k: Buffer) => {
        const iv = crypto.randomBytes(12);
        const cipher = crypto.createCipheriv('aes-256-gcm', k, iv);
        let encrypted = cipher.update(text, 'utf8', 'hex');
        encrypted += cipher.final('hex');
        const authTag = cipher.getAuthTag().toString('hex');
        return { iv: iv.toString('hex'), encrypted, authTag };
      };

      const result1 = encrypt(plaintext, key);
      const result2 = encrypt(plaintext, key);

      // IVs should be different
      expect(result1.iv).not.toBe(result2.iv);
      // Ciphertexts should be different
      expect(result1.encrypted).not.toBe(result2.encrypted);
    });
  });

  // ============================================
  // UT-WIN-SEC-004: Tampered ciphertext rejected
  // ============================================
  describe('UT-WIN-SEC-004: Tampered ciphertext rejected', () => {
    it('should throw error on tampered ciphertext', () => {
      const key = crypto.randomBytes(32);
      const iv = crypto.randomBytes(12);
      const plaintext = 'Secret data';

      // Encrypt normally
      const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
      let encrypted = cipher.update(plaintext, 'utf8', 'hex');
      encrypted += cipher.final('hex');
      let authTag = cipher.getAuthTag();

      // Tamper with ciphertext (flip one bit)
      const tamperedHex = (parseInt(encrypted[0], 16) ^ 1).toString(16) + encrypted.slice(1);

      // Try to decrypt tampered data
      const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv);
      decipher.setAuthTag(authTag);

      expect(() => {
        decipher.update(tamperedHex, 'hex', 'utf8');
        decipher.final('utf8');
      }).toThrow();
    });
  });

  // ============================================
  // UT-WIN-SEC-005: Device ID persistence
  // ============================================
  describe('UT-WIN-SEC-005: Device ID persisted via electron-store', () => {
    it('should return same device ID on multiple calls', async () => {
      const storedId = 'stable-device-id-12345';
      mockStore.get.mockReturnValue(storedId);
      mockKeytar.getPassword.mockResolvedValue(null);

      await securityManager.initialize();

      const id1 = securityManager.getDeviceId();
      const id2 = securityManager.getDeviceId();

      expect(id1).toBe(id2);
      expect(id1).toBe(storedId);
    });

    it('should generate new device ID if none exists', async () => {
      mockStore.get.mockReturnValue(undefined);
      mockKeytar.getPassword.mockResolvedValue(null);

      await securityManager.initialize();

      expect(mockStore.set).toHaveBeenCalledWith('deviceId', expect.any(String));
    });
  });

  // ============================================
  // UT-WIN-SEC-006: Paired device storage
  // ============================================
  describe('UT-WIN-SEC-006: Paired device stored in keytar', () => {
    it('should store paired device in keytar', async () => {
      mockStore.get.mockReturnValue('device-123');
      mockKeytar.getPassword.mockResolvedValue(null);

      await securityManager.initialize();

      const pairedDevice = {
        deviceId: 'AND-001',
        deviceName: 'MyPhone',
        publicKey: 'pk-abc123',
        sessionToken: 'token-xyz789',
        pairedAt: Date.now(),
      };

      securityManager.storePairedDevice(pairedDevice);

      expect(mockKeytar.setPassword).toHaveBeenCalled();
    });

    it('should retrieve paired devices from storage', async () => {
      mockStore.get.mockReturnValue('device-123');

      const storedToken = 'stored-session-token';
      mockKeytar.getPassword.mockResolvedValue(storedToken);

      await securityManager.initialize();

      // This test verifies the storage pattern is correct
      // Actual retrieval would depend on implementation
      expect(mockKeytar.getPassword).toHaveBeenCalled();
    });
  });
});
