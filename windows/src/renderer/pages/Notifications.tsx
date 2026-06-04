import React, { useState, useEffect } from 'react';
import { useConnection } from '../contexts/ConnectionContext';

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
    <div className="space-y-8 animate-fade-in font-inter">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-headline-xl text-on-surface mb-2">Notifications</h1>
          <p className="text-body-lg text-on-surface-variant">
            View notifications from your phone
          </p>
        </div>
        {notifications.length > 0 && (
          <button
            onClick={handleClearAll}
            className="flex items-center gap-2 px-4 py-2 rounded-glass-btn text-error hover:bg-error/10 border border-error/20 transition-all duration-300 text-label-md"
          >
            <span className="material-symbols-outlined text-lg">delete_sweep</span>
            Clear All
          </button>
        )}
      </div>

      {!isConnected ? (
        <div className="glass-panel p-8 text-center">
          <span className="material-symbols-outlined text-primary/50 text-5xl mb-4 block">notifications_off</span>
          <p className="text-body-lg text-on-surface-variant">
            Please connect to a device first to view notifications
          </p>
        </div>
      ) : notifications.length === 0 ? (
        <div className="glass-panel p-12 text-center">
          <span className="material-symbols-outlined text-on-surface-variant/20 text-6xl mb-4 block">notifications</span>
          <h3 className="text-headline-md text-on-surface mb-2">
            No notifications
          </h3>
          <p className="text-body-md text-on-surface-variant">
            Notifications from your phone will appear here
          </p>
        </div>
      ) : (
        <div className="space-y-6">
          {Object.entries(groupedNotifications).map(([date, notifs]) => (
            <div key={date}>
              <h3 className="text-label-sm text-on-surface-variant mb-3 uppercase tracking-wider">
                {date === new Date().toDateString() ? 'Today' : date}
              </h3>
              <div className="space-y-3">
                {notifs.map(notification => (
                  <div
                    key={notification.id}
                    className="glass-card p-4 hover:translate-y-0 hover:border-primary/30 group"
                  >
                    <div className="flex items-start gap-4">
                      {/* App Icon */}
                      <div className="w-10 h-10 rounded-lg bg-surface-variant border border-white/10 flex items-center justify-center flex-shrink-0 overflow-hidden">
                        {notification.iconBase64 ? (
                          <img
                            src={`data:image/png;base64,${notification.iconBase64}`}
                            alt=""
                            className="w-full h-full object-cover"
                          />
                        ) : (
                          <span className="material-symbols-outlined text-on-surface-variant text-lg">notifications</span>
                        )}
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-label-sm text-primary uppercase tracking-wider">
                            {notification.appName}
                          </span>
                          <span className="text-[11px] text-on-surface-variant">
                            {formatTime(notification.timestamp)}
                          </span>
                        </div>
                        <h4 className="text-label-md text-on-surface font-semibold">
                          {notification.title}
                        </h4>
                        <p className="text-body-md text-on-surface-variant text-sm mt-1 line-clamp-2">
                          {notification.text}
                        </p>
                      </div>

                      {/* Dismiss Button */}
                      <button
                        onClick={() => handleDismiss(notification.id)}
                        className="opacity-0 group-hover:opacity-100 p-2 hover:bg-white/5 rounded-lg transition-all duration-200"
                      >
                        <span className="material-symbols-outlined text-on-surface-variant text-lg">close</span>
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
