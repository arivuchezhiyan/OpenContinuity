import React from 'react';
import { useConnection } from '../contexts/ConnectionContext';
import { Link } from 'react-router-dom';

const features = [
  { id: 'clipboard', label: 'Clipboard Sync', icon: 'content_copy', description: 'Universal clipboard across devices' },
  { id: 'files', label: 'File Transfer', icon: 'folder_shared', description: 'Secure peer-to-peer sharing', link: '/files' },
  { id: 'notifications', label: 'Notifications', icon: 'notifications', description: 'Phone alerts on your PC', link: '/notifications' },
  { id: 'sms', label: 'SMS', icon: 'chat', description: 'Send and receive messages', link: '/sms' },
  { id: 'camera', label: 'Camera Stream', icon: 'videocam', description: 'Phone as webcam', link: '/screen-mirror' },
  { id: 'screen', label: 'Screen Mirror', icon: 'cast', description: 'Low-latency mirroring', link: '/screen-mirror' },
  { id: 'battery', label: 'Battery Monitor', icon: 'battery_full', description: 'Real-time battery status' },
  { id: 'unlock', label: 'PC Unlock', icon: 'lock_open', description: 'Proximity-based unlock' },
  { id: 'remote', label: 'Remote Control', icon: 'smartphone', description: 'Control connected nodes' },
  { id: 'touchpad', label: 'Touchpad', icon: 'touch_app', description: 'Use phone as touchpad', link: '/touchpad' },
  { id: 'screenshot', label: 'Screenshot Sync', icon: 'screenshot_monitor', description: 'Auto-receive screenshots', link: '/screenshots' },
  { id: 'pairing', label: 'Device Pairing', icon: 'hub', description: 'Add new connections', link: '/pairing' }
];

function Dashboard() {
  const { connectionState, batteryStatus, disconnect, discoveredDevices, connect } = useConnection();

  const isConnected = connectionState.status === 'connected';

  return (
    <div className="space-y-8 animate-fade-in font-inter">
      {/* Header */}
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-headline-xl text-on-surface mb-2">Network Overview</h1>
          <p className="text-body-lg text-on-surface-variant">
            Monitoring active nodes and synchronization status.
          </p>
        </div>
        <div className="flex items-center gap-3">
          {isConnected && (
            <div className="flex items-center gap-2 text-primary bg-primary/10 px-4 py-2 rounded-full border border-primary/20">
              <span className="material-symbols-outlined text-sm">signal_cellular_alt</span>
              <span className="text-label-md">Optimal Signal</span>
            </div>
          )}
        </div>
      </div>

      {/* Hero Connection Status Panel */}
      <div className={`glass-panel p-8 relative overflow-hidden min-h-[280px] flex flex-col justify-between ${
        isConnected ? 'glass-active' : ''
      }`}>
        {/* Decorative dot grid background */}
        <div className="absolute inset-0 opacity-10 pointer-events-none" style={{
          backgroundImage: 'radial-gradient(#10B981 1px, transparent 1px)',
          backgroundSize: '24px 24px'
        }} />

        <div className="relative z-10 flex justify-between items-start">
          <div>
            <div className="inline-flex items-center gap-2 bg-surface-dim/50 border border-white/10 rounded-full px-3 py-1.5 mb-3">
              <span className={`w-2 h-2 rounded-full ${isConnected ? 'bg-primary animate-pulse' : 'bg-on-surface-variant/50'}`} />
              <span className="text-label-sm text-on-surface-variant uppercase tracking-wider">
                {isConnected ? 'System Online' : 'System Idle'}
              </span>
            </div>
            <h2 className="text-headline-lg text-white mb-1">
              {isConnected ? 'Global Continuity Active' : 'Awaiting Connection'}
            </h2>
            <p className="text-body-md text-on-surface-variant max-w-md">
              {isConnected
                ? 'All secure nodes are maintaining stable connections. End-to-end encryption is active across all channels.'
                : discoveredDevices.length > 0
                  ? `${discoveredDevices.length} device(s) found on your network. Ready to establish secure connection.`
                  : 'Scanning network for available devices...'}
            </p>
          </div>
          {isConnected && batteryStatus && (
            <div className="text-right">
              <div className="text-headline-xl text-primary">{batteryStatus.level}%</div>
              <div className="text-label-md text-on-surface-variant">Battery</div>
            </div>
          )}
        </div>

        {/* Connection Visualization */}
        <div className="relative z-10 mt-8 flex items-center justify-center h-28">
          <div className="relative w-16 h-16 bg-surface-container rounded-full border border-primary/30 flex items-center justify-center shadow-[0_0_30px_rgba(16,185,129,0.2)] z-20">
            <span className="material-symbols-outlined text-primary text-3xl">computer</span>
            {isConnected && (
              <>
                <div className="pulse-ring" />
                <div className="pulse-ring" style={{ animationDelay: '1s' }} />
              </>
            )}
          </div>
          <div className={`h-[2px] w-24 relative ${isConnected ? 'bg-gradient-to-r from-primary/50 to-secondary/20' : 'bg-white/10'}`}>
            {isConnected && (
              <div className="absolute inset-0 bg-primary w-1/3 animate-pulse" />
            )}
          </div>
          <div className="w-12 h-12 glass-panel rounded-full flex items-center justify-center z-20">
            <span className="material-symbols-outlined text-secondary">cloud_sync</span>
          </div>
          <div className={`h-[2px] w-24 relative ${isConnected ? 'bg-gradient-to-l from-primary/50 to-secondary/20' : 'bg-white/10'}`}>
            {isConnected && (
              <div className="absolute inset-y-0 right-0 bg-primary w-1/3 animate-pulse" style={{ animationDirection: 'reverse' }} />
            )}
          </div>
          <div className="relative w-16 h-16 bg-surface-container rounded-full border border-primary/30 flex items-center justify-center shadow-[0_0_30px_rgba(16,185,129,0.2)] z-20">
            <span className="material-symbols-outlined text-primary text-3xl">smartphone</span>
            {isConnected && (
              <div className="pulse-ring" style={{ animationDelay: '0.5s' }} />
            )}
          </div>
        </div>

        {/* Action Buttons */}
        <div className="relative z-10 mt-6 flex gap-3">
          {isConnected ? (
            <button
              onClick={disconnect}
              className="px-5 py-2.5 rounded-glass-btn bg-error/10 border border-error/30 text-error hover:bg-error/20 transition-all duration-300 text-label-md"
            >
              Disconnect
            </button>
          ) : connectionState.status === 'error' ? (
            <div className="flex items-center gap-2 text-error">
              <span className="material-symbols-outlined">error</span>
              <span className="text-body-md">{connectionState.error}</span>
            </div>
          ) : (
            <Link
              to="/pairing"
              className="btn-primary px-6 py-2.5 text-label-md inline-flex items-center gap-2"
            >
              <span className="material-symbols-outlined text-lg">add</span>
              Connect Device
            </Link>
          )}
        </div>

        {/* Quick connect to discovered devices */}
        {!isConnected && discoveredDevices.length > 0 && (
          <div className="relative z-10 mt-4 pt-4 border-t border-white/5">
            <p className="text-label-sm text-on-surface-variant mb-2 uppercase tracking-wider">
              Available devices
            </p>
            <div className="flex flex-wrap gap-2">
              {discoveredDevices.map((device, index) => (
                <button
                  key={index}
                  onClick={() => connect(device.host, device.port)}
                  className="btn-secondary px-4 py-2 text-label-md inline-flex items-center gap-2"
                >
                  <span className="material-symbols-outlined text-sm text-primary">smartphone</span>
                  {device.name}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Features Grid */}
      <div>
        <h3 className="text-headline-md text-on-surface mb-4 font-inter">Features</h3>
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-gutter">
          {features.map(feature => {
            const content = (
              <div
                className={`glass-card p-5 group cursor-pointer ${
                  !isConnected && feature.id !== 'pairing' ? 'opacity-40 pointer-events-none' : ''
                }`}
              >
                <div className="flex justify-between items-start mb-4">
                  <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-surface-dim to-surface-container border border-white/10 flex items-center justify-center group-hover:scale-110 transition-transform duration-300">
                    <span className="material-symbols-outlined text-white text-2xl">{feature.icon}</span>
                  </div>
                  <span className="material-symbols-outlined text-on-surface-variant opacity-0 group-hover:opacity-100 transition-opacity">
                    arrow_forward
                  </span>
                </div>
                <h4 className="text-label-md text-white mb-1 font-inter font-semibold">{feature.label}</h4>
                <p className="text-xs text-on-surface-variant font-inter">{feature.description}</p>
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
          <h3 className="text-headline-md text-on-surface mb-4 font-inter">Recent Activity</h3>
          <div className="glass-panel p-6">
            <p className="text-on-surface-variant text-center py-8 text-body-md">
              No recent activity
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

export default Dashboard;
