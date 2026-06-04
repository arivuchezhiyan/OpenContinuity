import React, { useState, useEffect, useCallback } from 'react';
import { useConnection } from '../contexts/ConnectionContext';

interface FileTransfer {
  id: string;
  fileName: string;
  fileSize: number;
  direction: 'send' | 'receive';
  status: 'pending' | 'transferring' | 'completed' | 'failed';
  progress: number;
  localPath?: string;
}

function FileTransfer() {
  const { connectionState } = useConnection();
  const [transfers, setTransfers] = useState<FileTransfer[]>([]);
  const [isDragging, setIsDragging] = useState(false);

  const isConnected = connectionState.status === 'connected';

  useEffect(() => {
    // Listen for transfer updates
    const removeListener = window.api.onFileTransferUpdate((transfer: FileTransfer) => {
      setTransfers(prev => {
        const index = prev.findIndex(t => t.id === transfer.id);
        if (index >= 0) {
          const updated = [...prev];
          updated[index] = transfer;
          return updated;
        }
        return [transfer, ...prev];
      });
    });

    // Get initial transfers
    window.api.file.getTransfers().then(setTransfers);

    return removeListener;
  }, []);

  const handleSelectFiles = async () => {
    const files = await window.api.file.select();
    if (files) {
      for (const file of files) {
        await window.api.file.send(file);
      }
    }
  };

  const handleDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);

    if (!isConnected) return;

    const files = Array.from(e.dataTransfer.files);
    for (const file of files) {
      // In Electron, we need to get the path
      const path = (file as any).path;
      if (path) {
        await window.api.file.send(path);
      }
    }
  }, [isConnected]);

  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault();
    if (isConnected) {
      setIsDragging(true);
    }
  }, [isConnected]);

  const handleDragLeave = useCallback(() => {
    setIsDragging(false);
  }, []);

  const getFileIcon = (fileName: string) => {
    const ext = fileName.split('.').pop()?.toLowerCase();
    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'];
    const videoExts = ['mp4', 'webm', 'avi', 'mkv', 'mov'];
    const audioExts = ['mp3', 'wav', 'ogg', 'flac'];
    const archiveExts = ['zip', 'rar', '7z', 'tar', 'gz'];

    if (imageExts.includes(ext || '')) return 'image';
    if (videoExts.includes(ext || '')) return 'movie';
    if (audioExts.includes(ext || '')) return 'music_note';
    if (archiveExts.includes(ext || '')) return 'folder_zip';
    return 'description';
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  };

  return (
    <div className="space-y-8 animate-fade-in font-inter">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-headline-xl text-on-surface mb-2">File Transfer</h1>
          <p className="text-body-lg text-on-surface-variant">
            Secure, peer-to-peer file sharing with end-to-end encryption.
          </p>
        </div>
      </div>

      {!isConnected ? (
        <div className="glass-panel p-8 text-center">
          <span className="material-symbols-outlined text-primary/50 text-5xl mb-4 block">cloud_off</span>
          <p className="text-body-lg text-on-surface-variant">
            Please connect to a device first to transfer files
          </p>
        </div>
      ) : (
        <>
          {/* Drop Zone */}
          <div
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            className={`glass-panel p-12 text-center transition-all duration-300 cursor-pointer ${
              isDragging
                ? 'glass-active border-primary/50'
                : 'hover:border-white/20'
            }`}
          >
            <div className={`w-16 h-16 mx-auto mb-4 rounded-2xl flex items-center justify-center transition-all ${
              isDragging ? 'bg-primary/20 scale-110' : 'bg-white/5'
            }`}>
              <span className={`material-symbols-outlined text-4xl ${
                isDragging ? 'text-primary' : 'text-on-surface-variant'
              }`}>cloud_upload</span>
            </div>
            <p className="text-body-lg text-on-surface mb-2">
              Drag and drop files here
            </p>
            <p className="text-label-sm text-on-surface-variant mb-4">
              or
            </p>
            <button
              onClick={handleSelectFiles}
              className="btn-primary px-6 py-2.5 text-label-md inline-flex items-center gap-2"
            >
              <span className="material-symbols-outlined text-lg">folder_open</span>
              Browse Files
            </button>
          </div>

          {/* Transfers List */}
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-headline-md text-on-surface">Transfers</h2>
              <button
                onClick={() => window.api.file.openDownloadFolder?.()}
                className="text-label-md text-primary hover:underline flex items-center gap-1"
              >
                <span className="material-symbols-outlined text-lg">folder_open</span>
                Open Download Folder
              </button>
            </div>

            {transfers.length === 0 ? (
              <div className="glass-panel p-10 text-center">
                <span className="material-symbols-outlined text-on-surface-variant/30 text-5xl mb-4 block">description</span>
                <p className="text-body-md text-on-surface-variant">
                  No transfers yet
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {transfers.map(transfer => {
                  const fileIcon = getFileIcon(transfer.fileName);

                  return (
                    <div
                      key={transfer.id}
                      className="glass-card p-4 hover:translate-y-0"
                    >
                      <div className="flex items-center gap-4">
                        <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                          transfer.direction === 'send'
                            ? 'bg-primary/10 border border-primary/20'
                            : 'bg-secondary/10 border border-secondary/20'
                        }`}>
                          <span className={`material-symbols-outlined text-2xl ${
                            transfer.direction === 'send' ? 'text-primary' : 'text-secondary'
                          }`}>{fileIcon}</span>
                        </div>

                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <h3 className="text-label-md text-on-surface font-semibold truncate">
                              {transfer.fileName}
                            </h3>
                            <span className={`material-symbols-outlined text-sm ${
                              transfer.direction === 'send' ? 'text-primary' : 'text-secondary'
                            }`}>
                              {transfer.direction === 'send' ? 'arrow_upward' : 'arrow_downward'}
                            </span>
                          </div>
                          <p className="text-label-sm text-on-surface-variant">
                            {formatFileSize(transfer.fileSize)} • {transfer.direction === 'send' ? 'Sent' : 'Received'}
                          </p>
                        </div>

                        <div className="flex items-center gap-3">
                          {transfer.status === 'completed' && (
                            <div className="flex items-center gap-2">
                              <button
                                onClick={() => window.api.file.open(transfer.id)}
                                className="btn-primary px-4 py-1.5 text-label-sm"
                              >
                                Open
                              </button>
                              {transfer.direction === 'receive' && (
                                <button
                                  onClick={() => window.api.file.saveAs(transfer.id)}
                                  className="btn-secondary px-4 py-1.5 text-label-sm"
                                >
                                  Save As...
                                </button>
                              )}
                              <button
                                onClick={() => window.api.file.showInFolder(transfer.id)}
                                title="Show in Folder"
                                className="btn-secondary p-1.5"
                              >
                                <span className="material-symbols-outlined text-lg">folder</span>
                              </button>
                            </div>
                          )}

                          {transfer.status === 'transferring' && (
                            <span className="text-label-md font-bold text-primary">
                              {transfer.progress}%
                            </span>
                          )}
                          {transfer.status === 'failed' && (
                            <span className="text-label-md font-medium text-error">
                              Failed
                            </span>
                          )}
                          {transfer.status === 'pending' && (
                            <span className="text-label-md font-medium text-on-surface-variant">
                              Waiting...
                            </span>
                          )}
                        </div>
                      </div>

                      {/* Progress bar */}
                      {transfer.status === 'transferring' && (
                        <div className="mt-4 h-1.5 bg-surface-container rounded-full overflow-hidden">
                          <div
                            className="h-full bg-gradient-to-r from-secondary to-primary rounded-full shadow-[0_0_8px_#10b981] transition-all duration-300"
                            style={{ width: `${transfer.progress}%` }}
                          />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}

export default FileTransfer;
