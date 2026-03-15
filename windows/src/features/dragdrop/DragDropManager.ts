/**
 * DragDrop Manager (Windows)
 *
 * Implements cross-device drag-and-drop.
 * Workflow (Laptop → Phone):
 *   1. Renderer detects a drag event within EDGE_THRESHOLD pixels of a screen edge.
 *   2. Renderer sends 'dragdrop:startEdgeDrag' IPC with file metadata + edge position.
 *   3. This manager sends DRAG_FILE_START to the phone over the control channel.
 *   4. Phone shows a drop zone overlay (DragDropManager.kt).
 *   5. Phone accepts by sending DRAG_FILE_DROP — we start the file transfer.
 *
 * Workflow (Phone → Laptop):
 *   1. Phone sends DRAG_FILE_START.
 *   2. We emit 'dragReceived' so the renderer can show an accept UI.
 *   3. User accepts — we send DRAG_FILE_DROP and receive the file via FileTransferManager.
 */

import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { MessageType, generateMessageId, DragFileStartPayload, DragFileDropPayload } from '../../shared/protocol';
import crypto from 'crypto';

export interface PendingDrag {
  dragId: string;
  sourceDeviceId: string;
  fileName: string;
  fileSize: number;
  mimeType: string;
  direction: 'outgoing' | 'incoming';
  localFilePath?: string;
  startedAt: number;
}

export class DragDropManager extends EventEmitter {
  private connectionManager: ConnectionManager;
  private localDeviceId: string;
  private pendingDrags = new Map<string, PendingDrag>();

  /** Pixels from screen edge that trigger a cross-device drag */
  static readonly EDGE_THRESHOLD = 24;

  constructor(connectionManager: ConnectionManager, localDeviceId: string) {
    super();
    this.connectionManager = connectionManager;
    this.localDeviceId = localDeviceId;

    // Incoming drag from phone
    connectionManager.on(`message:${MessageType.DRAG_FILE_START}`, (message) => {
      this.handleIncomingDragStart(message.payload as DragFileStartPayload);
    });

    // Phone accepted / rejected our drag
    connectionManager.on(`message:${MessageType.DRAG_FILE_DROP}`, (message) => {
      this.handleDragDropAck(message.payload as DragFileDropPayload);
    });

    // Phone cancelled
    connectionManager.on(`message:${MessageType.DRAG_FILE_CANCEL}`, (message) => {
      const { dragId } = message.payload as { dragId: string };
      this.cancelDrag(dragId);
    });
  }

  // ─────────────────────── outgoing (laptop → phone) ──────────────────────

  /**
   * Called by the renderer when the user drags a file to the screen edge.
   * @param localFilePath  Path on the local filesystem.
   * @param edgeX          Normalized (0–1) horizontal position on the screen edge.
   * @param edgeY          Normalized (0–1) vertical position on the screen edge.
   */
  startEdgeDrag(localFilePath: string, edgeX: number, edgeY: number): string {
    const fs = require('fs') as typeof import('fs');
    const path = require('path') as typeof import('path');

    if (!fs.existsSync(localFilePath)) {
      throw new Error(`File not found: ${localFilePath}`);
    }

    const stat = fs.statSync(localFilePath);
    const dragId = `drag-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`;

    const drag: PendingDrag = {
      dragId,
      sourceDeviceId: this.localDeviceId,
      fileName: path.basename(localFilePath),
      fileSize: stat.size,
      mimeType: this.guessMime(localFilePath),
      direction: 'outgoing',
      localFilePath,
      startedAt: Date.now()
    };
    this.pendingDrags.set(dragId, drag);

    const payload: DragFileStartPayload = {
      dragId,
      sourceDeviceId: this.localDeviceId,
      fileName: drag.fileName,
      fileSize: drag.fileSize,
      mimeType: drag.mimeType,
      edgeX,
      edgeY
    };

    this.connectionManager.send({
      type: MessageType.DRAG_FILE_START,
      payload,
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    console.log(`[DragDrop] Outgoing drag started: ${drag.fileName} (${dragId})`);
    this.emit('dragStarted', drag);
    return dragId;
  }

  cancelDrag(dragId: string): void {
    const drag = this.pendingDrags.get(dragId);
    if (!drag) return;

    this.pendingDrags.delete(dragId);

    this.connectionManager.send({
      type: MessageType.DRAG_FILE_CANCEL,
      payload: { dragId },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    console.log(`[DragDrop] Drag cancelled: ${dragId}`);
    this.emit('dragCancelled', drag);
  }

  // ─────────────────────── incoming (phone → laptop) ──────────────────────

  private handleIncomingDragStart(payload: DragFileStartPayload): void {
    const drag: PendingDrag = {
      dragId: payload.dragId,
      sourceDeviceId: payload.sourceDeviceId,
      fileName: payload.fileName,
      fileSize: payload.fileSize,
      mimeType: payload.mimeType,
      direction: 'incoming',
      startedAt: Date.now()
    };
    this.pendingDrags.set(payload.dragId, drag);
    console.log(`[DragDrop] Incoming drag from phone: ${payload.fileName}`);
    // Renderer will call acceptDrop() or rejectDrop() after showing UI
    this.emit('dragReceived', drag);
  }

  acceptDrop(dragId: string): void {
    const drag = this.pendingDrags.get(dragId);
    if (!drag) return;

    const ack: DragFileDropPayload = {
      dragId,
      targetDeviceId: this.localDeviceId,
      accepted: true
    };

    this.connectionManager.send({
      type: MessageType.DRAG_FILE_DROP,
      payload: ack,
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    console.log(`[DragDrop] Drop accepted: ${drag.fileName}`);
    this.emit('dropAccepted', drag);
    // FileTransferManager handles the actual data transfer
  }

  rejectDrop(dragId: string): void {
    const drag = this.pendingDrags.get(dragId);
    if (!drag) return;

    this.pendingDrags.delete(dragId);

    this.connectionManager.send({
      type: MessageType.DRAG_FILE_DROP,
      payload: { dragId, targetDeviceId: this.localDeviceId, accepted: false },
      timestamp: Date.now(),
      messageId: generateMessageId()
    });

    this.emit('dropRejected', drag);
  }

  // ─────────────────────────────── ack from phone ─────────────────────────

  private handleDragDropAck(payload: DragFileDropPayload): void {
    const drag = this.pendingDrags.get(payload.dragId);
    if (!drag) return;

    if (payload.accepted) {
      console.log(`[DragDrop] Phone accepted the drop — triggering file transfer`);
      this.emit('dragAcceptedByPeer', drag);
      // FileTransferManager picks this up and starts the chunked send
    } else {
      this.pendingDrags.delete(payload.dragId);
      console.log(`[DragDrop] Phone rejected the drop`);
      this.emit('dragRejectedByPeer', drag);
    }
  }

  getPendingDrags(): PendingDrag[] {
    return Array.from(this.pendingDrags.values());
  }

  // ─────────────────────────────── utils ──────────────────────────────────

  private guessMime(filePath: string): string {
    const ext = filePath.split('.').pop()?.toLowerCase() ?? '';
    const mimeMap: Record<string, string> = {
      jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', gif: 'image/gif',
      webp: 'image/webp', svg: 'image/svg+xml', bmp: 'image/bmp',
      pdf: 'application/pdf',
      mp4: 'video/mp4', mov: 'video/quicktime', avi: 'video/x-msvideo',
      mp3: 'audio/mpeg', wav: 'audio/wav', flac: 'audio/flac',
      zip: 'application/zip', rar: 'application/x-rar-compressed',
      txt: 'text/plain', md: 'text/markdown', html: 'text/html',
      json: 'application/json',
      docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
    };
    return mimeMap[ext] ?? 'application/octet-stream';
  }
}
