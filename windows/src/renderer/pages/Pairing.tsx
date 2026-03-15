import React, { useState, useEffect } from 'react';
import { useConnection } from '../contexts/ConnectionContext';
import { QrCodeIcon, WifiIcon, ComputerDesktopIcon } from '@heroicons/react/24/outline';

function Pairing() {
  const { connectionState, discoveredDevices, connect, startDiscovery, stopDiscovery } = useConnection();
  const [qrCode, setQrCode] = useState<string | null>(null);
  const [manualHost, setManualHost] = useState('');
  const [manualPort, setManualPort] = useState('8765');
  const [activeTab, setActiveTab] = useState<'scan' | 'discover' | 'manual'>('discover');

  useEffect(() => {
    startDiscovery();
    generateQR();
    
    return () => {
      stopDiscovery();
    };
  }, []);

  const generateQR = async () => {
    try {
      const qr = await window.api.pairing.generateQR();
      setQrCode(qr);
    } catch (error) {
      console.error('Failed to generate QR code:', error);
    }
  };

  const handleManualConnect = () => {
    if (manualHost && manualPort) {
      connect(manualHost, parseInt(manualPort, 10));
    }
  };

  const isConnecting = connectionState.status === 'connecting';

  return (
    <div className="space-y-6 animate-fade-in">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Device Pairing</h1>
        <p className="text-gray-500 dark:text-gray-400 mt-1">
          Connect your Android device to this PC
        </p>
      </div>

      {/* Tab Buttons */}
      <div className="flex gap-2">
        <button
          onClick={() => setActiveTab('discover')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            activeTab === 'discover'
              ? 'bg-primary-500 text-white'
              : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
          }`}
        >
          <WifiIcon className="w-5 h-5 inline mr-2" />
          Auto Discover
        </button>
        <button
          onClick={() => setActiveTab('scan')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            activeTab === 'scan'
              ? 'bg-primary-500 text-white'
              : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
          }`}
        >
          <QrCodeIcon className="w-5 h-5 inline mr-2" />
          QR Code
        </button>
        <button
          onClick={() => setActiveTab('manual')}
          className={`px-4 py-2 rounded-lg font-medium transition-colors ${
            activeTab === 'manual'
              ? 'bg-primary-500 text-white'
              : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
          }`}
        >
          <ComputerDesktopIcon className="w-5 h-5 inline mr-2" />
          Manual
        </button>
      </div>

      {/* Auto Discover */}
      {activeTab === 'discover' && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Discovered Devices
          </h2>
          
          {discoveredDevices.length === 0 ? (
            <div className="text-center py-12">
              <WifiIcon className="w-12 h-12 mx-auto text-gray-400 mb-4 animate-pulse" />
              <p className="text-gray-500 dark:text-gray-400">
                Searching for devices on your network...
              </p>
              <p className="text-sm text-gray-400 dark:text-gray-500 mt-2">
                Make sure the OpenContinuity app is running on your Android device
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {discoveredDevices.map((device, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between p-4 bg-gray-50 dark:bg-gray-700/50 rounded-lg"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-primary-100 dark:bg-primary-900 rounded-full flex items-center justify-center">
                      <WifiIcon className="w-5 h-5 text-primary-600 dark:text-primary-400" />
                    </div>
                    <div>
                      <h3 className="font-medium text-gray-900 dark:text-white">
                        {device.name}
                      </h3>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        {device.host}:{device.port}
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={() => connect(device.host, device.port)}
                    disabled={isConnecting}
                    className="px-4 py-2 bg-primary-500 hover:bg-primary-600 disabled:bg-gray-400 text-white rounded-lg transition-colors"
                  >
                    {isConnecting ? 'Connecting...' : 'Connect'}
                  </button>
                </div>
              ))}
            </div>
          )}

          <button
            onClick={startDiscovery}
            className="mt-4 w-full py-2 border border-gray-200 dark:border-gray-700 rounded-lg text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
          >
            Refresh
          </button>
        </div>
      )}

      {/* QR Code */}
      {activeTab === 'scan' && (
        <div className="grid md:grid-cols-2 gap-6">
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Scan from Phone
            </h2>
            <p className="text-gray-500 dark:text-gray-400 mb-4">
              Open the OpenContinuity app on your phone and scan this QR code
            </p>
            
            {qrCode ? (
              <div className="flex justify-center">
                <img
                  src={qrCode}
                  alt="QR Code"
                  className="w-64 h-64 rounded-lg"
                />
              </div>
            ) : (
              <div className="w-64 h-64 mx-auto bg-gray-100 dark:bg-gray-700 rounded-lg flex items-center justify-center">
                <p className="text-gray-400">Generating...</p>
              </div>
            )}

            <button
              onClick={generateQR}
              className="mt-4 w-full py-2 border border-gray-200 dark:border-gray-700 rounded-lg text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
            >
              Regenerate QR Code
            </button>
          </div>

          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Instructions
            </h2>
            <ol className="space-y-4 text-gray-600 dark:text-gray-300">
              <li className="flex gap-3">
                <span className="flex-shrink-0 w-6 h-6 bg-primary-100 dark:bg-primary-900 text-primary-600 dark:text-primary-400 rounded-full flex items-center justify-center text-sm font-medium">
                  1
                </span>
                <span>Open the OpenContinuity app on your Android device</span>
              </li>
              <li className="flex gap-3">
                <span className="flex-shrink-0 w-6 h-6 bg-primary-100 dark:bg-primary-900 text-primary-600 dark:text-primary-400 rounded-full flex items-center justify-center text-sm font-medium">
                  2
                </span>
                <span>Tap on "Scan QR Code" or go to Pairing settings</span>
              </li>
              <li className="flex gap-3">
                <span className="flex-shrink-0 w-6 h-6 bg-primary-100 dark:bg-primary-900 text-primary-600 dark:text-primary-400 rounded-full flex items-center justify-center text-sm font-medium">
                  3
                </span>
                <span>Point your camera at the QR code on this screen</span>
              </li>
              <li className="flex gap-3">
                <span className="flex-shrink-0 w-6 h-6 bg-primary-100 dark:bg-primary-900 text-primary-600 dark:text-primary-400 rounded-full flex items-center justify-center text-sm font-medium">
                  4
                </span>
                <span>Confirm the pairing on both devices</span>
              </li>
            </ol>
          </div>
        </div>
      )}

      {/* Manual Connection */}
      {activeTab === 'manual' && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6 max-w-md">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Manual Connection
          </h2>
          <p className="text-gray-500 dark:text-gray-400 mb-4">
            Enter the IP address and port shown on your Android device
          </p>

          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                IP Address
              </label>
              <input
                type="text"
                value={manualHost}
                onChange={(e) => setManualHost(e.target.value)}
                placeholder="192.168.1.100"
                className="w-full px-3 py-2 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent outline-none"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                Port
              </label>
              <input
                type="text"
                value={manualPort}
                onChange={(e) => setManualPort(e.target.value)}
                placeholder="8765"
                className="w-full px-3 py-2 border border-gray-200 dark:border-gray-700 rounded-lg bg-white dark:bg-gray-900 text-gray-900 dark:text-white focus:ring-2 focus:ring-primary-500 focus:border-transparent outline-none"
              />
            </div>
            <button
              onClick={handleManualConnect}
              disabled={!manualHost || isConnecting}
              className="w-full py-2 bg-primary-500 hover:bg-primary-600 disabled:bg-gray-400 text-white rounded-lg transition-colors"
            >
              {isConnecting ? 'Connecting...' : 'Connect'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default Pairing;
