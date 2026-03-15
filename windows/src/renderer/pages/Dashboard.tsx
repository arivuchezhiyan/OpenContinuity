import React from 'react';
import { useConnection } from '../contexts/ConnectionContext';
import { Link } from 'react-router-dom';
import {
  ClipboardDocumentIcon,
  FolderArrowDownIcon,
  BellIcon,
  ChatBubbleLeftRightIcon,
  VideoCameraIcon,
  TvIcon,
  Battery100Icon,
  LockOpenIcon,
  DevicePhoneMobileIcon,
  CursorArrowRaysIcon,
  PhotoIcon,
  QrCodeIcon,
  WifiIcon,
  ExclamationCircleIcon
} from '@heroicons/react/24/outline';

const features = [
  { id: 'clipboard', label: 'Clipboard Sync', icon: ClipboardDocumentIcon, color: 'bg-blue-500' },
  { id: 'files', label: 'File Transfer', icon: FolderArrowDownIcon, color: 'bg-green-500', link: '/files' },
  { id: 'notifications', label: 'Notifications', icon: BellIcon, color: 'bg-yellow-500', link: '/notifications' },
  { id: 'sms', label: 'SMS', icon: ChatBubbleLeftRightIcon, color: 'bg-purple-500', link: '/sms' },
  { id: 'camera', label: 'Camera Stream', icon: VideoCameraIcon, color: 'bg-pink-500', link: '/screen-mirror' },
  { id: 'screen', label: 'Screen Mirror', icon: TvIcon, color: 'bg-indigo-500', link: '/screen-mirror' },
  { id: 'battery', label: 'Battery Monitor', icon: Battery100Icon, color: 'bg-orange-500' },
  { id: 'unlock', label: 'PC Unlock', icon: LockOpenIcon, color: 'bg-red-500' },
  { id: 'remote', label: 'Remote Control', icon: DevicePhoneMobileIcon, color: 'bg-cyan-500' },
  { id: 'touchpad', label: 'Touchpad', icon: CursorArrowRaysIcon, color: 'bg-teal-500', link: '/touchpad' },
  { id: 'screenshot', label: 'Screenshot Sync', icon: PhotoIcon, color: 'bg-lime-500', link: '/screenshots' },
  { id: 'pairing', label: 'Device Pairing', icon: QrCodeIcon, color: 'bg-violet-500', link: '/pairing' }
];

function Dashboard() {
  const { connectionState, batteryStatus, disconnect, discoveredDevices, connect } = useConnection();

  const isConnected = connectionState.status === 'connected';

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Dashboard</h1>
        <p className="text-gray-500 dark:text-gray-400 mt-1">
          Manage your connected devices and features
        </p>
      </div>

      {/* Connection Status Card */}
      <div className={`p-6 rounded-xl border-2 transition-all ${
        isConnected 
          ? 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800'
          : 'bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700'
      }`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className={`p-3 rounded-full ${
              isConnected ? 'bg-green-500' : 'bg-gray-400'
            }`}>
              <WifiIcon className="w-6 h-6 text-white" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-gray-900 dark:text-white">
                {isConnected ? 'Connected' : 'Not Connected'}
              </h2>
              {isConnected ? (
                <p className="text-gray-600 dark:text-gray-300">
                  {connectionState.deviceName}
                </p>
              ) : (
                <p className="text-gray-500 dark:text-gray-400">
                  {discoveredDevices.length > 0 
                    ? `${discoveredDevices.length} device(s) found`
                    : 'Searching for devices...'}
                </p>
              )}
            </div>
          </div>

          {isConnected ? (
            <div className="flex items-center gap-4">
              {batteryStatus && (
                <div className="flex items-center gap-2 px-4 py-2 bg-white dark:bg-gray-700 rounded-lg">
                  <Battery100Icon className="w-5 h-5 text-green-500" />
                  <span className="font-medium text-gray-700 dark:text-gray-200">
                    {batteryStatus.level}%
                  </span>
                  {batteryStatus.isCharging && (
                    <span className="text-yellow-500">⚡</span>
                  )}
                </div>
              )}
              <button
                onClick={disconnect}
                className="px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors"
              >
                Disconnect
              </button>
            </div>
          ) : connectionState.status === 'error' ? (
            <div className="flex items-center gap-2 text-red-500">
              <ExclamationCircleIcon className="w-5 h-5" />
              <span>{connectionState.error}</span>
            </div>
          ) : (
            <Link
              to="/pairing"
              className="px-4 py-2 bg-primary-500 hover:bg-primary-600 text-white rounded-lg transition-colors"
            >
              Connect Device
            </Link>
          )}
        </div>

        {/* Quick connect to discovered devices */}
        {!isConnected && discoveredDevices.length > 0 && (
          <div className="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-2">
              Available devices:
            </p>
            <div className="flex flex-wrap gap-2">
              {discoveredDevices.map((device, index) => (
                <button
                  key={index}
                  onClick={() => connect(device.host, device.port)}
                  className="px-3 py-1.5 bg-white dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm hover:border-primary-500 transition-colors"
                >
                  {device.name}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Features Grid */}
      <div>
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Features
        </h3>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {features.map(feature => {
            const content = (
              <div
                className={`p-4 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 hover:border-primary-500 dark:hover:border-primary-500 transition-all cursor-pointer ${
                  !isConnected && feature.id !== 'pairing' ? 'opacity-50' : ''
                }`}
              >
                <div className={`w-10 h-10 ${feature.color} rounded-lg flex items-center justify-center mb-3`}>
                  <feature.icon className="w-5 h-5 text-white" />
                </div>
                <h4 className="font-medium text-gray-900 dark:text-white text-sm">
                  {feature.label}
                </h4>
              </div>
            );

            if (feature.link) {
              return (
                <Link key={feature.id} to={feature.link}>
                  {content}
                </Link>
              );
            }

            return <div key={feature.id}>{content}</div>;
          })}
        </div>
      </div>

      {/* Recent Activity */}
      {isConnected && (
        <div>
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Recent Activity
          </h3>
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4">
            <p className="text-gray-500 dark:text-gray-400 text-center py-8">
              No recent activity
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

export default Dashboard;
