import React, { useState, useEffect } from 'react';
import { useConnection } from '../contexts/ConnectionContext';
import {
  BellIcon,
  XMarkIcon,
  TrashIcon
} from '@heroicons/react/24/outline';

interface Notification {
  id: string;
  appName: string;
  title: string;
  text: string;
  timestamp: number;
  iconBase64?: string;
}

function Notifications() {
  const { connectionState } = useConnection();
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const isConnected = connectionState.status === 'connected';

  useEffect(() => {
    if (isConnected) {
      loadNotifications();
    }

    // Listen for new notifications
    const removeNotifListener = window.api.onNotificationReceived?.((notif: Notification) => {
      setNotifications(prev => [notif, ...prev]);
    });

    const removeRemovedListener = window.api.onNotificationRemoved?.((data: { id: string }) => {
      setNotifications(prev => prev.filter(n => n.id !== data.id));
    });

    return () => {
      removeNotifListener?.();
      removeRemovedListener?.();
    };
  }, [isConnected]);

  const loadNotifications = async () => {
    try {
      const notifs = await window.api.notifications.get();
      setNotifications(notifs || []);
    } catch (error) {
      console.error('Failed to load notifications:', error);
    }
  };

  const handleDismiss = async (id: string) => {
    await window.api.notifications.dismiss(id);
    setNotifications(prev => prev.filter(n => n.id !== id));
  };

  const handleClearAll = async () => {
    for (const notif of notifications) {
      await window.api.notifications.dismiss(notif.id);
    }
    setNotifications([]);
  };

  const formatTime = (timestamp: number): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();

    if (diff < 60 * 1000) {
      return 'Just now';
    } else if (diff < 60 * 60 * 1000) {
      const mins = Math.floor(diff / (60 * 1000));
      return `${mins}m ago`;
    } else if (diff < 24 * 60 * 60 * 1000) {
      const hours = Math.floor(diff / (60 * 60 * 1000));
      return `${hours}h ago`;
    } else {
      return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
    }
  };

  const groupedNotifications = notifications.reduce((acc, notif) => {
    const date = new Date(notif.timestamp).toDateString();
    if (!acc[date]) {
      acc[date] = [];
    }
    acc[date].push(notif);
    return acc;
  }, {} as Record<string, Notification[]>);

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Notifications</h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1">
            View notifications from your phone
          </p>
        </div>
        {notifications.length > 0 && (
          <button
            onClick={handleClearAll}
            className="flex items-center gap-2 px-4 py-2 text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
          >
            <TrashIcon className="w-5 h-5" />
            Clear All
          </button>
        )}
      </div>

      {!isConnected ? (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-xl p-6 text-center">
          <p className="text-yellow-800 dark:text-yellow-200">
            Please connect to a device first to view notifications
          </p>
        </div>
      ) : notifications.length === 0 ? (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-12 text-center">
          <BellIcon className="w-16 h-16 mx-auto text-gray-400 mb-4" />
          <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
            No notifications
          </h3>
          <p className="text-gray-500 dark:text-gray-400">
            Notifications from your phone will appear here
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          {Object.entries(groupedNotifications).map(([date, notifs]) => (
            <div key={date}>
              <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-3">
                {date === new Date().toDateString() ? 'Today' : date}
              </h3>
              <div className="space-y-3">
                {notifs.map(notification => (
                  <div
                    key={notification.id}
                    className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4 hover:border-primary-500 transition-all group"
                  >
                    <div className="flex items-start gap-4">
                      {/* App Icon */}
                      <div className="w-10 h-10 rounded-lg bg-gray-100 dark:bg-gray-700 flex items-center justify-center flex-shrink-0 overflow-hidden">
                        {notification.iconBase64 ? (
                          <img
                            src={`data:image/png;base64,${notification.iconBase64}`}
                            alt=""
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <BellIcon className="w-5 h-5 text-gray-400" />
                        )}
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-xs font-medium text-primary-500 uppercase">
                            {notification.appName}
                          </span>
                          <span className="text-xs text-gray-400">
                            {formatTime(notification.timestamp)}
                          </span>
                        </div>
                        <h4 className="font-medium text-gray-900 dark:text-white">
                          {notification.title}
                        </h4>
                        <p className="text-gray-600 dark:text-gray-300 text-sm mt-1 line-clamp-2">
                          {notification.text}
                        </p>
                      </div>

                      {/* Dismiss Button */}
                      <button
                        onClick={() => handleDismiss(notification.id)}
                        className="opacity-0 group-hover:opacity-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-all"
                      >
                        <XMarkIcon className="w-5 h-5 text-gray-400" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export default Notifications;
