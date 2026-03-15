/**
 * File Transfer Manager - handles file sending and receiving
 */

import { EventEmitter } from 'events';
import { BrowserWindow, dialog, shell, Notification } from 'electron';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { MessageType, generateMessageId } from '../../shared/protocol';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';

export interface FileTransfer {
  id: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  direction: 'send' | 'receive';
  status: 'pending' | 'transferring' | 'completed' | 'failed' | 'cancelled';
  progress: number; // 0-100
  bytesTransferred: number;
  localPath?: string;
  error?: string;
  startTime: number;
  endTime?: number;
}

export class FileTransferManager extends EventEmitter {
  private connectionManager: ConnectionManager;
  private transfers: Map<string, FileTransfer> = new Map();
  private downloadPath: string;
  private chunkSize: number = 64 * 1024; // 64KB chunks

  constructor(connectionManager: ConnectionManager, downloadPath?: string) {
    super();
    this.connectionManager = connectionManager;
    this.downloadPath = downloadPath || path.join(process.env.USERPROFILE || '', 'Downloads', 'OpenContinuity');

    // Ensure download directory exists
    if (!fs.existsSync(this.downloadPath)) {
      fs.mkdirSync(this.downloadPath, { recursive: true });
    }

    // ── Outgoing: phone accepted our offer → start sending chunks ──────────
    connectionManager.on(`message:${MessageType.FILE_TRANSFER_ACCEPT}`, (message) => {
      this.startSendingChunks(message.payload.transferId).catch(err =>
        console.error('[FileTransfer] sendChunks error:', err)
      );
    });

    // ── Incoming: phone is sending us a file offer ─────────────────────────
    connectionManager.on(`message:${MessageType.FILE_TRANSFER_REQUEST}`, (message) => {
      this.handleTransferOffer(message.payload);
    });

    // ── Incoming: receive a chunk from the phone ───────────────────────────
    connectionManager.on(`message:${MessageType.FILE_CHUNK}`, (message) => {
      this.handleTransferChunk(message.payload);
    });

    connectionManager.on(`message:${MessageType.FILE_TRANSFER_COMPLETE}`, (message) => {
      this.handleTransferComplete(message.payload);
    });

    connectionManager.on(`message:${MessageType.FILE_TRANSFER_ERROR}`, (message) => {
      this.handleTransferError(message.payload);
    });
  }

  setDownloadPath(downloadPath: string): void {
    this.downloadPath = downloadPath;
    if (!fs.existsSync(this.downloadPath)) {
      fs.mkdirSync(this.downloadPath, { recursive: true });
    }
  }

  getTransfers(): FileTransfer[] {
    return Array.from(this.transfers.values())
      .sort((a, b) => b.startTime - a.startTime);
  }

  getActiveTransfers(): FileTransfer[] {
    return this.getTransfers().filter(t => 
      t.status === 'pending' || t.status === 'transferring'
    );
  }

  async sendFile(filePath: string): Promise<string> {
    const stats = fs.statSync(filePath);
    const fileName = path.basename(filePath);
    const transferId = `transfer-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

    const transfer: FileTransfer = {
      id: transferId,
      fileName,
      fileSize: stats.size,
      mimeType: this.getMimeType(filePath),
      direction: 'send',
      status: 'pending',
      progress: 0,
      bytesTransferred: 0,
      localPath: filePath,
      startTime: Date.now()
    };

    this.transfers.set(transferId, transfer);
    this.notifyTransferUpdate(transfer);

    // Send offer — chunks are sent after FILE_TRANSFER_ACCEPT is received
    this.connectionManager.send({
      type: MessageType.FILE_TRANSFER_REQUEST,
      payload: {
        transferId,
        fileName,
        fileSize: stats.size,
        mimeType: transfer.mimeType,
        checksum: await this.calculateChecksum(filePath)
      },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    return transferId;
  }

  /**
   * AirDrop-style chunked send.
   * Called after the receiving device sends FILE_TRANSFER_ACCEPT.
   * Each chunk carries a SHA-256 checksum for per-chunk integrity.
   */
  private async startSendingChunks(transferId: string): Promise<void> {
    const transfer = this.transfers.get(transferId);
    if (!transfer || !transfer.localPath || transfer.direction !== 'send') return;

    transfer.status = 'transferring';
    this.notifyTransferUpdate(transfer);

    const fileSize = transfer.fileSize;
    const totalChunks = Math.ceil(fileSize / this.chunkSize);
    let sequence = 0;
    let bytesSent = 0;

    const stream = fs.createReadStream(transfer.localPath, { highWaterMark: this.chunkSize });

    for await (const rawChunk of stream) {
      const chunk = Buffer.isBuffer(rawChunk) ? rawChunk : Buffer.from(rawChunk);
      const checksum = crypto.createHash('sha256').update(chunk).digest('hex');

      this.connectionManager.send({
        type: MessageType.FILE_CHUNK,
        payload: {
          transferId,
          sequence,
          totalChunks,
          data: chunk.toString('base64'),
          checksum
        },
        timestamp: Date.now(),
        messageId: generateMessageId()
      });

      bytesSent += chunk.length;
      transfer.bytesTransferred = bytesSent;
      transfer.progress = Math.round((bytesSent / fileSize) * 100);
      this.notifyTransferUpdate(transfer);
      sequence++;
    }

    // Notify completion + send SHA-256 of entire file for final integrity check
    const fullChecksum = await this.calculateChecksum(transfer.localPath);
    this.connectionManager.send({
      type: MessageType.FILE_TRANSFER_COMPLETE,
      payload: { transferId, success: true, checksum: fullChecksum },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    transfer.status = 'completed';
    transfer.progress = 100;
    transfer.endTime = Date.now();
    this.notifyTransferUpdate(transfer);
    console.log(`[FileTransfer] Sent ${sequence} chunks for ${transfer.fileName}`);
  }

  async sendFiles(filePaths: string[]): Promise<string[]> {
    const transferIds: string[] = [];
    for (const filePath of filePaths) {
      const id = await this.sendFile(filePath);
      transferIds.push(id);
    }
    return transferIds;
  }

  private async handleTransferOffer(payload: any): Promise<void> {
    const transfer: FileTransfer = {
      id: payload.transferId,
      fileName: payload.fileName,
      fileSize: payload.fileSize,
      mimeType: payload.mimeType || 'application/octet-stream',
      direction: 'receive',
      status: 'pending',
      progress: 0,
      bytesTransferred: 0,
      startTime: Date.now()
    };

    this.transfers.set(transfer.id, transfer);
    this.notifyTransferUpdate(transfer);

    // Auto-accept the transfer (could prompt user instead)
    this.acceptTransfer(transfer.id);
  }

  private acceptTransfer(transferId: string): void {
    const transfer = this.transfers.get(transferId);
    if (!transfer || transfer.direction !== 'receive') return;

    transfer.status = 'transferring';
    transfer.localPath = path.join(this.downloadPath, transfer.fileName);

    // Handle duplicate file names
    let counter = 1;
    while (fs.existsSync(transfer.localPath!)) {
      const ext = path.extname(transfer.fileName);
      const base = path.basename(transfer.fileName, ext);
      transfer.localPath = path.join(this.downloadPath, `${base} (${counter})${ext}`);
      counter++;
    }

    this.notifyTransferUpdate(transfer);

    // Send acceptance
    this.connectionManager.send({
      type: MessageType.FILE_TRANSFER_ACCEPT,
      payload: { transferId },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });
  }

  private handleTransferChunk(payload: any): void {
    const transfer = this.transfers.get(payload.transferId);
    if (!transfer || !transfer.localPath) return;

    try {
      const chunk = Buffer.from(payload.data, 'base64');

      // Per-chunk integrity check
      if (payload.checksum) {
        const actual = crypto.createHash('sha256').update(chunk).digest('hex');
        if (actual !== payload.checksum) {
          console.error(`[FileTransfer] Checksum mismatch on chunk ${payload.sequence} — aborting`);
          transfer.status = 'failed';
          transfer.error = `Chunk ${payload.sequence} integrity check failed`;
          this.notifyTransferUpdate(transfer);
          return;
        }
      }

      // Append chunk to destination file
      fs.appendFileSync(transfer.localPath, chunk);

      transfer.bytesTransferred += chunk.length;
      transfer.progress = Math.floor((transfer.bytesTransferred / transfer.fileSize) * 100);
      transfer.status = 'transferring';

      this.notifyTransferUpdate(transfer);
    } catch (error) {
      console.error('Error writing chunk:', error);
      transfer.status = 'failed';
      transfer.error = 'Failed to write file chunk';
      this.notifyTransferUpdate(transfer);
    }
  }

  private async handleTransferComplete(payload: any): Promise<void> {
    const transfer = this.transfers.get(payload.transferId);
    if (!transfer) return;

    transfer.status = 'completed';
    transfer.progress = 100;
    transfer.bytesTransferred = transfer.fileSize;
    transfer.endTime = Date.now();

    // Verify checksum if provided
    if (payload.checksum && transfer.localPath) {
      const localChecksum = await this.calculateChecksum(transfer.localPath);
      if (localChecksum !== payload.checksum) {
        transfer.status = 'failed';
        transfer.error = 'Checksum mismatch';
      }
    }

    this.notifyTransferUpdate(transfer);

    // Show notification
    if (transfer.status === 'completed' && transfer.direction === 'receive') {
      const { Notification } = require('electron');
      const notification = new Notification({
        title: 'File Received',
        body: `${transfer.fileName} has been downloaded`
      });
      
      notification.on('click', () => {
        if (transfer.localPath) {
          shell.showItemInFolder(transfer.localPath);
        }
      });
      
      notification.show();
    }
  }

  private handleTransferError(payload: any): void {
    const transfer = this.transfers.get(payload.transferId);
    if (!transfer) return;

    transfer.status = 'failed';
    transfer.error = payload.error || 'Unknown error';
    transfer.endTime = Date.now();

    this.notifyTransferUpdate(transfer);
  }

  cancelTransfer(transferId: string): void {
    const transfer = this.transfers.get(transferId);
    if (!transfer) return;

    transfer.status = 'cancelled';
    transfer.endTime = Date.now();

    this.connectionManager.send({
      type: MessageType.FILE_TRANSFER_ERROR,
      payload: { transferId },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    this.notifyTransferUpdate(transfer);
  }

  private notifyTransferUpdate(transfer: FileTransfer): void {
    this.emit('transferUpdate', transfer);

    BrowserWindow.getAllWindows().forEach(window => {
      window.webContents.send('file:transferUpdate', transfer);
    });
  }

  private async calculateChecksum(filePath: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const hash = crypto.createHash('sha256');
      const stream = fs.createReadStream(filePath);
      
      stream.on('data', (data) => hash.update(data));
      stream.on('end', () => resolve(hash.digest('hex')));
      stream.on('error', reject);
    });
  }

  private getMimeType(filePath: string): string {
    const ext = path.extname(filePath).toLowerCase();
    const mimeTypes: Record<string, string> = {
      '.txt': 'text/plain',
      '.html': 'text/html',
      '.css': 'text/css',
      '.js': 'application/javascript',
      '.json': 'application/json',
      '.png': 'image/png',
      '.jpg': 'image/jpeg',
      '.jpeg': 'image/jpeg',
      '.gif': 'image/gif',
      '.webp': 'image/webp',
      '.svg': 'image/svg+xml',
      '.mp3': 'audio/mpeg',
      '.wav': 'audio/wav',
      '.mp4': 'video/mp4',
      '.webm': 'video/webm',
      '.pdf': 'application/pdf',
      '.doc': 'application/msword',
      '.docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      '.xls': 'application/vnd.ms-excel',
      '.xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      '.zip': 'application/zip',
      '.rar': 'application/x-rar-compressed',
      '.apk': 'application/vnd.android.package-archive'
    };

    return mimeTypes[ext] || 'application/octet-stream';
  }

  openDownloadFolder(): void {
    shell.openPath(this.downloadPath);
  }

  openFile(transferId: string): void {
    const transfer = this.transfers.get(transferId);
    if (transfer?.localPath && fs.existsSync(transfer.localPath)) {
      shell.openPath(transfer.localPath);
    }
  }

  showInFolder(transferId: string): void {
    const transfer = this.transfers.get(transferId);
    if (transfer?.localPath && fs.existsSync(transfer.localPath)) {
      shell.showItemInFolder(transfer.localPath);
    }
  }
}
