import React, { useState, useEffect } from 'react';
import { useConnection } from '../contexts/ConnectionContext';

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
    <div className="space-y-8 animate-fade-in font-inter">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-headline-xl text-on-surface mb-2">Device Pairing</h1>
          <p className="text-body-lg text-on-surface-variant max-w-2xl">
            Initialize secure handshake protocols to seamlessly integrate new hardware into the Continuity network.
          </p>
        </div>
      </div>

      {/* Tab Buttons */}
      <div className="flex gap-2">
        <button
          onClick={() => setActiveTab('discover')}
          className={`px-5 py-2.5 rounded-glass-btn text-label-md transition-all duration-300 flex items-center gap-2 ${
            activeTab === 'discover'
              ? 'btn-primary'
              : 'btn-secondary'
          }`}
        >
          <span className="material-symbols-outlined text-lg">wifi_tethering</span>
          Auto Discover
        </button>
        <button
          onClick={() => setActiveTab('scan')}
          className={`px-5 py-2.5 rounded-glass-btn text-label-md transition-all duration-300 flex items-center gap-2 ${
            activeTab === 'scan'
              ? 'btn-primary'
              : 'btn-secondary'
          }`}
        >
          <span className="material-symbols-outlined text-lg">qr_code_scanner</span>
          QR Code
        </button>
        <button
          onClick={() => setActiveTab('manual')}
          className={`px-5 py-2.5 rounded-glass-btn text-label-md transition-all duration-300 flex items-center gap-2 ${
            activeTab === 'manual'
              ? 'btn-primary'
              : 'btn-secondary'
          }`}
        >
          <span className="material-symbols-outlined text-lg">terminal</span>
          Manual
        </button>
      </div>

      {/* Auto Discover */}
      {activeTab === 'discover' && (
        <div className="grid grid-cols-12 gap-gutter">
          {/* Proximity Radar Panel */}
          <div className="col-span-12 lg:col-span-8 glass-panel p-8 relative overflow-hidden min-h-[400px] flex flex-col">
            <div className="flex justify-between items-center z-10 mb-6">
              <h3 className="text-headline-md text-on-surface">Proximity Radar</h3>
              <div className="bg-surface-variant/50 border border-white/10 rounded-full px-4 py-1.5 flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-sm">wifi_tethering</span>
                <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">Scanning Network</span>
              </div>
            </div>
            
            {discoveredDevices.length === 0 ? (
              <div className="flex-1 flex items-center justify-center relative">
                {/* Radar rings */}
                <div className="absolute w-48 h-48 rounded-full border border-primary/20 animate-pulse" />
                <div className="absolute w-72 h-72 rounded-full border border-primary/10" />
                <div className="absolute w-96 h-96 rounded-full border border-primary/5" />
                
                {/* Center node */}
                <div className="relative w-20 h-20 bg-primary/10 rounded-full flex items-center justify-center z-20 backdrop-blur-md border border-primary/30 shadow-[0_0_40px_rgba(16,185,129,0.3)]">
                  <div className="w-12 h-12 bg-primary rounded-full flex items-center justify-center shadow-inner">
                    <span className="material-symbols-outlined text-on-primary">dns</span>
                  </div>
                </div>
                
                <p className="absolute bottom-8 text-on-surface-variant text-body-md">
                  Searching for devices on your network...
                </p>
              </div>
            ) : (
              <div className="flex-1 space-y-3 overflow-y-auto">
                {discoveredDevices.map((device, index) => (
                  <div
                    key={index}
                    className="flex items-center justify-between p-4 rounded-xl bg-white/[0.05] border border-white/[0.08] hover:border-primary/50 transition-all duration-300 group"
                  >
                    <div className="flex items-center gap-4">
                      <div className="w-12 h-12 rounded-xl bg-primary/10 border border-primary/20 flex items-center justify-center group-hover:shadow-[0_0_20px_rgba(16,185,129,0.2)] transition-all">
                        <span className="material-symbols-outlined text-primary">smartphone</span>
                      </div>
                      <div>
                        <h3 className="text-label-md text-on-surface font-semibold">{device.name}</h3>
                        <p className="text-label-sm text-on-surface-variant">{device.host}:{device.port}</p>
                      </div>
                    </div>
                    <button
                      onClick={() => connect(device.host, device.port)}
                      disabled={isConnecting}
                      className="btn-primary px-5 py-2 text-label-md disabled:opacity-50"
                    >
                      {isConnecting ? 'Connecting...' : 'Connect'}
                    </button>
                  </div>
                ))}
              </div>
            )}

            {/* Legend */}
            <div className="mt-auto border-t border-white/5 pt-4 flex items-center justify-between z-10">
              <div className="flex gap-6">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-primary shadow-[0_0_10px_rgba(16,185,129,0.5)]" />
                  <span className="text-label-sm text-on-surface-variant">Ready to Connect</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-white/20" />
                  <span className="text-label-sm text-on-surface-variant">Analyzing Signal</span>
                </div>
              </div>
              <button 
                onClick={startDiscovery}
                className="text-primary text-label-md hover:underline flex items-center gap-1"
              >
                <span className="material-symbols-outlined text-lg">refresh</span>
                Refresh Scope
              </button>
            </div>
          </div>

          {/* Instructions Card */}
          <div className="col-span-12 lg:col-span-4 glass-card p-6">
            <h3 className="text-headline-md text-on-surface mb-4">Quick Start</h3>
            <div className="space-y-4">
              {[
                { step: '1', text: 'Ensure both devices are on the same network' },
                { step: '2', text: 'Open the OpenContinuity app on your Android device' },
                { step: '3', text: 'Devices will automatically appear in the radar' },
                { step: '4', text: 'Click "Connect" to establish secure link' }
              ].map((item) => (
                <div key={item.step} className="flex gap-3 items-start">
                  <span className="flex-shrink-0 w-7 h-7 bg-primary/10 border border-primary/20 text-primary rounded-full flex items-center justify-center text-label-sm font-bold">
                    {item.step}
                  </span>
                  <span className="text-body-md text-on-surface-variant pt-0.5">{item.text}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* QR Code */}
      {activeTab === 'scan' && (
        <div className="grid grid-cols-12 gap-gutter">
          {/* QR Panel */}
          <div className="col-span-12 lg:col-span-5 glass-panel p-8 flex flex-col items-center relative overflow-hidden group">
            {/* Corner accents */}
            <div className="absolute top-0 left-0 w-8 h-8 border-t-2 border-l-2 border-primary/30 rounded-tl-glass opacity-0 group-hover:opacity-100 transition-opacity" />
            <div className="absolute bottom-0 right-0 w-8 h-8 border-b-2 border-r-2 border-primary/30 rounded-br-glass opacity-0 group-hover:opacity-100 transition-opacity" />
            
            <div className="text-center mb-6">
              <h3 className="text-headline-md text-on-surface mb-2">Scan Identity Matrix</h3>
              <p className="text-body-md text-on-surface-variant">
                Position external device camera to read the encrypted optical sequence.
              </p>
            </div>

            {qrCode ? (
              <div className="relative w-64 h-64 bg-surface-container-highest rounded-2xl border border-white/10 p-4 shadow-inner flex items-center justify-center mb-6">
                <img
                  src={qrCode}
                  alt="QR Code"
                  className="w-full h-full rounded-xl"
                />
                {/* Center Logo Overlay */}
                <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                  <div className="w-14 h-14 bg-surface-dim/90 backdrop-blur-md rounded-xl border border-white/20 flex items-center justify-center shadow-2xl">
                    <span className="material-symbols-outlined filled text-primary text-2xl">hub</span>
                  </div>
                </div>
              </div>
            ) : (
              <div className="w-64 h-64 rounded-2xl bg-surface-container-highest border border-white/10 flex items-center justify-center mb-6">
                <span className="material-symbols-outlined text-on-surface-variant text-4xl animate-pulse">qr_code_2</span>
              </div>
            )}

            {/* Broadcasting status */}
            <div className="bg-white/[0.08] backdrop-blur-xl border border-primary/40 shadow-[0_0_20px_rgba(16,185,129,0.2)] rounded-full px-6 py-2.5 flex items-center gap-3">
              <span className="relative flex h-3 w-3">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-primary opacity-75" />
                <span className="relative inline-flex rounded-full h-3 w-3 bg-primary" />
              </span>
              <span className="text-label-md text-primary tracking-wide uppercase">Broadcasting Matrix...</span>
            </div>

            <button
              onClick={generateQR}
              className="mt-4 btn-secondary px-6 py-2 text-label-md w-full"
            >
              Regenerate QR Code
            </button>
          </div>

          {/* Instructions */}
          <div className="col-span-12 lg:col-span-7 glass-card p-8">
            <h3 className="text-headline-md text-on-surface mb-6">Instructions</h3>
            <div className="space-y-5">
              {[
                'Open the OpenContinuity app on your Android device',
                'Tap on "Scan QR Code" or go to Pairing settings',
                'Point your camera at the QR code on this screen',
                'Confirm the pairing on both devices'
              ].map((text, i) => (
                <div key={i} className="flex gap-4 items-start">
                  <span className="flex-shrink-0 w-8 h-8 bg-primary/10 border border-primary/20 text-primary rounded-full flex items-center justify-center text-label-md font-bold">
                    {i + 1}
                  </span>
                  <span className="text-body-lg text-on-surface-variant pt-1">{text}</span>
                </div>
              ))}
            </div>

            {/* Manual entry option */}
            <div className="mt-8 p-4 rounded-xl bg-white/[0.05] border border-white/[0.08] flex items-center gap-4">
              <div className="w-12 h-12 rounded-full bg-surface-variant flex items-center justify-center border border-white/5 shrink-0">
                <span className="material-symbols-outlined text-on-surface-variant">dialpad</span>
              </div>
              <div className="flex-1">
                <h4 className="text-label-md text-on-surface mb-0.5">Manual Entry Option</h4>
                <p className="text-body-md text-on-surface-variant text-sm">Unable to scan? Use numeric pin.</p>
              </div>
              <button
                onClick={() => setActiveTab('manual')}
                className="btn-secondary px-4 py-2 text-label-md"
              >
                View PIN
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Manual Connection */}
      {activeTab === 'manual' && (
        <div className="glass-panel p-8 max-w-md">
          <h2 className="text-headline-md text-on-surface mb-2">Manual Connection</h2>
          <p className="text-body-md text-on-surface-variant mb-6">
            Enter the IP address and port shown on your Android device
          </p>

          <div className="space-y-4">
            <div>
              <label className="block text-label-md text-on-surface-variant mb-2">
                IP Address
              </label>
              <input
                type="text"
                value={manualHost}
                onChange={(e) => setManualHost(e.target.value)}
                placeholder="192.168.1.100"
                className="w-full px-4 py-3 glass-input text-body-md placeholder:text-on-surface-variant/40"
              />
            </div>
            <div>
              <label className="block text-label-md text-on-surface-variant mb-2">
                Port
              </label>
              <input
                type="text"
                value={manualPort}
                onChange={(e) => setManualPort(e.target.value)}
                placeholder="8765"
                className="w-full px-4 py-3 glass-input text-body-md placeholder:text-on-surface-variant/40"
              />
            </div>
            <button
              onClick={handleManualConnect}
              disabled={!manualHost || isConnecting}
              className="w-full btn-primary py-3 text-label-md"
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
