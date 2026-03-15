/**
 * Security Manager - handles cryptographic operations
 */

import crypto from 'crypto';
import keytar from 'keytar';
import { v4 as uuidv4 } from 'uuid';
import Store from 'electron-store';

const SERVICE_NAME = 'OpenContinuity';
const ACCOUNT_DEVICE_ID = 'device_id';
const ACCOUNT_PRIVATE_KEY = 'private_key';

interface StoredSettings {
  deviceId?: string;
  pairedDevices?: PairedDevice[];
}

export interface PairedDevice {
  deviceId: string;
  deviceName: string;
  publicKey: string;
  sessionToken: string;
  pairedAt: number;
  lastConnected?: number;
}

export class SecurityManager {
  private store: Store<StoredSettings>;
  private privateKey: crypto.KeyObject | null = null;
  private publicKey: crypto.KeyObject | null = null;
  private deviceId: string = '';

  constructor() {
    this.store = new Store<StoredSettings>({
      name: 'security',
      encryptionKey: 'opencontinuity-local-storage-key'
    });
  }

  async initialize(): Promise<void> {
    // Get or create device ID
    this.deviceId = this.store.get('deviceId') || '';
    if (!this.deviceId) {
      this.deviceId = uuidv4();
      this.store.set('deviceId', this.deviceId);
    }

    // Load or generate key pair
    await this.loadOrGenerateKeys();
  }

  private async loadOrGenerateKeys(): Promise<void> {
    try {
      // Try to load existing private key from secure storage
      const storedPrivateKey = await keytar.getPassword(SERVICE_NAME, ACCOUNT_PRIVATE_KEY);
      
      if (storedPrivateKey) {
        this.privateKey = crypto.createPrivateKey({
          key: Buffer.from(storedPrivateKey, 'base64'),
          format: 'der',
          type: 'pkcs8'
        });
        this.publicKey = crypto.createPublicKey(this.privateKey);
        console.log('Loaded existing key pair');
      } else {
        // Generate new key pair
        await this.generateKeyPair();
      }
    } catch (error) {
      console.error('Failed to load keys, generating new ones:', error);
      await this.generateKeyPair();
    }
  }

  private async generateKeyPair(): Promise<void> {
    const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
      namedCurve: 'prime256v1'  // Also known as secp256r1/P-256
    });

    this.privateKey = privateKey;
    this.publicKey = publicKey;

    // Store private key securely
    const privateKeyDer = privateKey.export({ type: 'pkcs8', format: 'der' });
    await keytar.setPassword(SERVICE_NAME, ACCOUNT_PRIVATE_KEY, privateKeyDer.toString('base64'));
    
    console.log('Generated new key pair');
  }

  getDeviceId(): string {
    return this.deviceId;
  }

  getPublicKeyBase64(): string {
    if (!this.publicKey) {
      throw new Error('Keys not initialized');
    }
    const publicKeyDer = this.publicKey.export({ type: 'spki', format: 'der' });
    return publicKeyDer.toString('base64');
  }

  parsePublicKey(base64Key: string): crypto.KeyObject {
    const keyDer = Buffer.from(base64Key, 'base64');
    return crypto.createPublicKey({
      key: keyDer,
      format: 'der',
      type: 'spki'
    });
  }

  generatePairingCode(): string {
    return Math.floor(Math.random() * 1000000).toString().padStart(6, '0');
  }

  generateSessionToken(): string {
    return crypto.randomBytes(32).toString('base64');
  }

  deriveSharedSecret(peerPublicKey: crypto.KeyObject): Buffer {
    if (!this.privateKey) {
      throw new Error('Keys not initialized');
    }

    const rawSecret = crypto.diffieHellman({
      privateKey: this.privateKey,
      publicKey: peerPublicKey
    });

    // HKDF (RFC 5869) — expand raw ECDH secret to 256-bit AES key.
    // Using SHA-256 as the hash, a fixed application salt, and
    // an info string that binds the key to this specific purpose.
    return Buffer.from(
      crypto.hkdfSync(
        'sha256',
        rawSecret,
        Buffer.from('opencontinuity-v2', 'utf8'),  // salt
        Buffer.from('aes-256-gcm encryption key', 'utf8'), // info
        32   // 32 bytes = 256-bit AES key
      )
    );
  }

  encrypt(data: Buffer, secretKey: Buffer): { iv: string; cipherText: string } {
    const iv = crypto.randomBytes(12);
    const cipher = crypto.createCipheriv('aes-256-gcm', secretKey, iv);
    
    const encrypted = Buffer.concat([cipher.update(data), cipher.final()]);
    const authTag = cipher.getAuthTag();
    
    return {
      iv: iv.toString('base64'),
      cipherText: Buffer.concat([encrypted, authTag]).toString('base64')
    };
  }

  decrypt(encryptedData: { iv: string; cipherText: string }, secretKey: Buffer): Buffer {
    const iv = Buffer.from(encryptedData.iv, 'base64');
    const cipherText = Buffer.from(encryptedData.cipherText, 'base64');
    
    // Last 16 bytes are the auth tag
    const authTag = cipherText.slice(-16);
    const encrypted = cipherText.slice(0, -16);
    
    const decipher = crypto.createDecipheriv('aes-256-gcm', secretKey, iv);
    decipher.setAuthTag(authTag);
    
    return Buffer.concat([decipher.update(encrypted), decipher.final()]);
  }

  sign(data: Buffer): string {
    if (!this.privateKey) {
      throw new Error('Keys not initialized');
    }

    const sign = crypto.createSign('SHA256');
    sign.update(data);
    return sign.sign(this.privateKey, 'base64');
  }

  verify(data: Buffer, signature: string, publicKey: crypto.KeyObject): boolean {
    const verify = crypto.createVerify('SHA256');
    verify.update(data);
    return verify.verify(publicKey, signature, 'base64');
  }

  hash(data: Buffer): string {
    return crypto.createHash('sha256').update(data).digest('base64');
  }

  // Paired devices management
  getPairedDevices(): PairedDevice[] {
    return this.store.get('pairedDevices') || [];
  }

  addPairedDevice(device: PairedDevice): void {
    const devices = this.getPairedDevices();
    const existingIndex = devices.findIndex(d => d.deviceId === device.deviceId);
    
    if (existingIndex >= 0) {
      devices[existingIndex] = device;
    } else {
      devices.push(device);
    }
    
    this.store.set('pairedDevices', devices);
  }

  removePairedDevice(deviceId: string): void {
    const devices = this.getPairedDevices().filter(d => d.deviceId !== deviceId);
    this.store.set('pairedDevices', devices);
  }

  updateLastConnected(deviceId: string): void {
    const devices = this.getPairedDevices();
    const device = devices.find(d => d.deviceId === deviceId);
    
    if (device) {
      device.lastConnected = Date.now();
      this.store.set('pairedDevices', devices);
    }
  }
}
