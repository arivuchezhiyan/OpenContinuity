import React, { useEffect, useState } from 'react';
import { PhotoIcon, FolderOpenIcon } from '@heroicons/react/24/outline';
import { useConnection } from '../contexts/ConnectionContext';

interface SavedScreenshot {
  filePath: string;
  fileName: string;
  savedAt: number;
}

function Screenshots() {
  const { connectionState } = useConnection();
  const [items, setItems] = useState<SavedScreenshot[]>([]);
  const isConnected = connectionState.status === 'connected';

  useEffect(() => {
    const remove = window.api.onScreenshotSaved?.((info: { filePath: string; fileName: string }) => {
      setItems((prev) => [
        { filePath: info.filePath, fileName: info.fileName, savedAt: Date.now() },
        ...prev,
      ].slice(0, 20));
    });
    return () => remove?.();
  }, []);

  const openFolder = async () => {
    await window.api.file?.openDownloadFolder?.();
  };

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Screenshots</h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1">
            Screenshots taken on your phone appear here automatically
          </p>
        </div>
        <button
          onClick={openFolder}
          className="flex items-center gap-2 px-4 py-2 bg-primary-500 hover:bg-primary-600 text-white rounded-lg"
        >
          <FolderOpenIcon className="w-5 h-5" />
          Open folder
        </button>
      </div>

      {!isConnected && (
        <p className="text-amber-600 dark:text-amber-400 text-sm">Connect to your phone to receive screenshots.</p>
      )}

      {items.length === 0 ? (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-12 text-center">
          <PhotoIcon className="w-12 h-12 mx-auto text-gray-400 mb-4" />
          <p className="text-gray-500 dark:text-gray-400">No screenshots yet</p>
        </div>
      ) : (
        <ul className="space-y-2">
          {items.map((item) => (
            <li
              key={`${item.filePath}-${item.savedAt}`}
              className="flex items-center justify-between p-4 bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700"
            >
              <div>
                <p className="font-medium text-gray-900 dark:text-white">{item.fileName}</p>
                <p className="text-sm text-gray-500 dark:text-gray-400">{item.filePath}</p>
              </div>
              <span className="text-xs text-gray-400">
                {new Date(item.savedAt).toLocaleTimeString()}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default Screenshots;
