import React, { useState, useEffect, useCallback } from 'react';
import { useConnection } from '../contexts/ConnectionContext';
import {
  FolderOpenIcon,
  ArrowUpTrayIcon,
  ArrowDownTrayIcon,
  DocumentIcon,
  PhotoIcon,
  FilmIcon,
  MusicalNoteIcon,
  ArchiveBoxIcon,
  XMarkIcon,
  FolderIcon
} from '@heroicons/react/24/outline';

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

    if (imageExts.includes(ext || '')) return PhotoIcon;
    if (videoExts.includes(ext || '')) return FilmIcon;
    if (audioExts.includes(ext || '')) return MusicalNoteIcon;
    if (archiveExts.includes(ext || '')) return ArchiveBoxIcon;
    return DocumentIcon;
  };

  const formatFileSize = (bytes: number): string => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  };

  return (
    <div className="space-y-6 animate-fade-in">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">File Transfer</h1>
        <p className="text-gray-500 dark:text-gray-400 mt-1">
          Send and receive files between your devices
        </p>
      </div>

      {!isConnected ? (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-xl p-6 text-center">
          <p className="text-yellow-800 dark:text-yellow-200">
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
            className={`border-2 border-dashed rounded-xl p-12 text-center transition-all ${
              isDragging
                ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                : 'border-gray-300 dark:border-gray-600 hover:border-primary-400'
            }`}
          >
            <ArrowUpTrayIcon className={`w-12 h-12 mx-auto mb-4 ${
              isDragging ? 'text-primary-500' : 'text-gray-400'
            }`} />
            <p className="text-gray-600 dark:text-gray-300 mb-2">
              Drag and drop files here
            </p>
            <p className="text-sm text-gray-400 dark:text-gray-500 mb-4">
              or
            </p>
            <button
              onClick={handleSelectFiles}
              className="px-6 py-2 bg-primary-500 hover:bg-primary-600 text-white rounded-lg transition-colors"
            >
              <FolderOpenIcon className="w-5 h-5 inline mr-2" />
              Browse Files
            </button>
          </div>

          {/* Transfers List */}
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                Transfers
              </h2>
              <button
                onClick={() => window.api.file.openDownloadFolder?.()}
                className="text-sm text-primary-500 hover:text-primary-600"
              >
                <FolderIcon className="w-4 h-4 inline mr-1" />
                Open Download Folder
              </button>
            </div>

            {transfers.length === 0 ? (
              <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-8 text-center">
                <DocumentIcon className="w-12 h-12 mx-auto text-gray-400 mb-4" />
                <p className="text-gray-500 dark:text-gray-400">
                  No transfers yet
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {transfers.map(transfer => {
                  const FileIcon = getFileIcon(transfer.fileName);
                  
                  return (
                    <div
                      key={transfer.id}
                      className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4 shadow-sm hover:shadow-md transition-shadow"
                    >
                      <div className="flex items-center gap-4">
                        <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${
                          transfer.direction === 'send'
                            ? 'bg-blue-100 dark:bg-blue-900/30'
                            : 'bg-green-100 dark:bg-green-900/30'
                        }`}>
                          <FileIcon className={`w-6 h-6 ${
                            transfer.direction === 'send'
                              ? 'text-blue-500'
                              : 'text-green-500'
                          }`} />
                        </div>
 
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <h3 className="font-semibold text-gray-900 dark:text-white truncate text-base">
                              {transfer.fileName}
                            </h3>
                            {transfer.direction === 'send' ? (
                              <ArrowUpTrayIcon className="w-4 h-4 text-blue-500 flex-shrink-0" />
                            ) : (
                              <ArrowDownTrayIcon className="w-4 h-4 text-green-500 flex-shrink-0" />
                            )}
                          </div>
                          <p className="text-sm text-gray-500 dark:text-gray-400">
                            {formatFileSize(transfer.fileSize)} • {transfer.direction === 'send' ? 'Sent' : 'Received'}
                          </p>
                        </div>
 
                        <div className="flex items-center gap-3">
                          {transfer.status === 'completed' && (
                            <div className="flex items-center gap-2">
                              <button
                                onClick={() => window.api.file.open(transfer.id)}
                                className="px-3 py-1.5 bg-primary-500 hover:bg-primary-600 text-white rounded-lg text-sm font-medium transition-colors"
                              >
                                Open
                              </button>
                              <button
                                onClick={() => window.api.file.showInFolder(transfer.id)}
                                title="Show in Folder"
                                className="p-1.5 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 rounded-lg transition-colors"
                              >
                                <FolderIcon className="w-5 h-5 text-gray-600 dark:text-gray-300" />
                              </button>
                            </div>
                          )}
                          
                          {transfer.status === 'transferring' && (
                            <span className="text-sm font-bold text-primary-500">
                              {transfer.progress}%
                            </span>
                          )}
                          {transfer.status === 'failed' && (
                            <span className="text-sm font-medium text-red-500">
                              Failed
                            </span>
                          )}
                          {transfer.status === 'pending' && (
                            <span className="text-sm font-medium text-gray-400">
                              Waiting...
                            </span>
                          )}
                        </div>
                      </div>
 
                      {/* Progress bar */}
                      {transfer.status === 'transferring' && (
                        <div className="mt-4 h-2 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary-500 transition-all duration-300 shadow-[0_0_8px_rgba(var(--color-primary-500),0.5)]"
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
