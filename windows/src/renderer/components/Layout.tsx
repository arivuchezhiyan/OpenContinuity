import React from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useConnection } from '../contexts/ConnectionContext';
import {
  HomeIcon,
  QrCodeIcon,
  FolderIcon,
  ChatBubbleLeftIcon,
  BellIcon,
  TvIcon,
  Cog6ToothIcon,
  WifiIcon,
  Battery100Icon,
  MinusIcon,
  Square2StackIcon,
  XMarkIcon,
  PencilSquareIcon
} from '@heroicons/react/24/outline';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: HomeIcon },
  { path: '/pairing', label: 'Pairing', icon: QrCodeIcon },
  { path: '/files', label: 'Files', icon: FolderIcon },
  { path: '/sms', label: 'SMS', icon: ChatBubbleLeftIcon },
  { path: '/notifications', label: 'Notifications', icon: BellIcon },
  { path: '/notes', label: 'Note Maker', icon: PencilSquareIcon },
  { path: '/screen-mirror', label: 'Screen Mirror', icon: TvIcon },
  { path: '/settings', label: 'Settings', icon: Cog6ToothIcon }
];

function Layout() {
  const { connectionState, batteryStatus } = useConnection();
  const navigate = useNavigate();

  React.useEffect(() => {
    const unlisten = window.api.onNoteSync?.(() => {
      // Auto-navigate to Note Maker when a draw event occurs
      navigate('/notes');
      window.api.window.maximize();
    });
    return () => unlisten?.();
  }, [navigate]);

  const handleMinimize = () => window.api.window.minimize();
  const handleMaximize = () => window.api.window.maximize();
  const handleClose = () => window.api.window.close();

  const getStatusColor = () => {
    switch (connectionState.status) {
      case 'connected': return 'bg-green-500';
      case 'connecting': return 'bg-yellow-500 animate-pulse';
      case 'error': return 'bg-red-500';
      default: return 'bg-gray-400';
    }
  };

  return (
    <div className="flex flex-col h-screen bg-gray-100 dark:bg-gray-900">
      {/* Title Bar */}
      <div className="titlebar-drag-region flex items-center justify-between h-10 bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 px-4">
        <div className="flex items-center gap-3">
          <div className="text-lg font-semibold text-primary-600 dark:text-primary-400">
            OpenContinuity
          </div>
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${getStatusColor()}`} />
            <span className="text-xs text-gray-500 dark:text-gray-400">
              {connectionState.status === 'connected' 
                ? connectionState.deviceName 
                : connectionState.status}
            </span>
          </div>
        </div>
        
        <div className="flex items-center gap-4">
          {batteryStatus && connectionState.status === 'connected' && (
            <div className="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400">
              <Battery100Icon className="w-4 h-4" />
              <span>{batteryStatus.level}%</span>
              {batteryStatus.isCharging && <span>⚡</span>}
            </div>
          )}
          
          <div className="titlebar-no-drag flex items-center">
            <button
              onClick={handleMinimize}
              className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            >
              <MinusIcon className="w-4 h-4 text-gray-500" />
            </button>
            <button
              onClick={handleMaximize}
              className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            >
              <Square2StackIcon className="w-4 h-4 text-gray-500" />
            </button>
            <button
              onClick={handleClose}
              className="p-2 hover:bg-red-500 hover:text-white transition-colors"
            >
              <XMarkIcon className="w-4 h-4 text-gray-500 hover:text-white" />
            </button>
          </div>
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden">
        {/* Sidebar */}
        <nav className="w-56 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex flex-col">
          <div className="flex-1 py-4">
            {navItems.map(item => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-4 py-3 mx-2 rounded-lg transition-colors ${
                    isActive
                      ? 'bg-primary-100 dark:bg-primary-900 text-primary-600 dark:text-primary-400'
                      : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                  }`
                }
              >
                <item.icon className="w-5 h-5" />
                <span className="text-sm font-medium">{item.label}</span>
              </NavLink>
            ))}
          </div>

          {/* Connection Status Card */}
          <div className="p-4 border-t border-gray-200 dark:border-gray-700">
            <div className="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
              <div className="flex items-center gap-2 mb-2">
                <WifiIcon className={`w-4 h-4 ${
                  connectionState.status === 'connected' 
                    ? 'text-green-500' 
                    : 'text-gray-400'
                }`} />
                <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
                  {connectionState.status === 'connected' ? 'Connected' : 'Not Connected'}
                </span>
              </div>
              {connectionState.deviceName && (
                <p className="text-xs text-gray-500 dark:text-gray-400 truncate">
                  {connectionState.deviceName}
                </p>
              )}
            </div>
          </div>
        </nav>

        {/* Main Content */}
        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default Layout;
