import React from 'react';
import { Outlet, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useConnection } from '../contexts/ConnectionContext';

const navItems = [
  { path: '/dashboard', label: 'Dashboard', icon: 'grid_view' },
  { path: '/pairing', label: 'Pairing', icon: 'hub' },
  { path: '/files', label: 'Files', icon: 'folder_shared' },
  { path: '/sms', label: 'SMS', icon: 'chat' },
  { path: '/notifications', label: 'Notifications', icon: 'notifications' },
  { path: '/notes', label: 'Note Maker', icon: 'edit_note' },
  { path: '/screen-mirror', label: 'Screen Mirror', icon: 'cast' },
  { path: '/settings', label: 'Settings', icon: 'settings' }
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

  const getStatusColor = () => {
    switch (connectionState.status) {
      case 'connected': return 'status-connected';
      case 'connecting': return 'status-connecting';
      case 'error': return 'status-error';
      default: return 'status-disconnected';
    }
  };

  return (
    <div className="flex flex-col h-screen bg-[#06141B] overflow-hidden">
      {/* Ambient Background Orbs */}
      <div className="ambient-orb orb-1" />
      <div className="ambient-orb orb-2" />

      {/* Title Bar */}
      <div className="titlebar-drag-region relative z-50 flex items-center justify-between h-10 bg-[#06141B]/80 backdrop-blur-xl border-b border-white/5 px-4">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined filled text-primary text-lg">hub</span>
            <span className="text-sm font-semibold text-primary font-inter">OpenContinuity</span>
          </div>
          <div className="flex items-center gap-2">
            <div className={`w-2 h-2 rounded-full ${getStatusColor()}`} />
            <span className="text-xs text-on-surface-variant font-inter">
              {connectionState.status === 'connected'
                ? connectionState.deviceName
                : connectionState.status}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {batteryStatus && connectionState.status === 'connected' && (
            <div className="flex items-center gap-1.5 text-xs text-on-surface-variant font-inter">
              <span className="material-symbols-outlined text-sm text-primary">battery_full</span>
              <span>{batteryStatus.level}%</span>
              {batteryStatus.isCharging && <span className="text-primary">⚡</span>}
            </div>
          )}
        </div>
      </div>

      <div className="flex flex-1 overflow-hidden relative z-10">
        {/* Sidebar — Glassmorphism floating panel */}
        <nav className="w-60 flex flex-col my-3 ml-3 bg-white/[0.08] backdrop-blur-[40px] saturate-[180%] border border-white/[0.12] rounded-glass-sidebar shadow-[0_0_20px_rgba(16,185,129,0.1)]">
          {/* Logo */}
          <div className="p-5 flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-primary-container/20 flex items-center justify-center border border-primary/30">
              <span className="material-symbols-outlined filled text-primary">hub</span>
            </div>
            <div>
              <h1 className="text-headline-md text-primary font-bold leading-tight font-inter text-lg">
                OpenContinuity
              </h1>
              <p className="text-[11px] text-on-surface-variant opacity-70 uppercase tracking-wider font-semibold font-inter">
                Enterprise Node
              </p>
            </div>
          </div>

          {/* Navigation */}
          <div className="flex-1 px-2 py-1 space-y-[4px] overflow-y-auto">
            {navItems.map(item => (
              <NavLink
                key={item.path}
                to={item.path}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-4 py-2.5 rounded-lg transition-all duration-200 font-inter text-sm font-medium ${
                    isActive
                      ? 'text-primary bg-primary/10 border-r-4 border-primary shadow-[0_0_20px_rgba(16,185,129,0.15)]'
                      : 'text-on-surface-variant opacity-70 hover:bg-white/5 hover:opacity-100 hover:translate-x-1'
                  }`
                }
              >
                <span className="material-symbols-outlined text-[20px]">{item.icon}</span>
                <span>{item.label}</span>
              </NavLink>
            ))}
          </div>

          {/* Connection Status Card */}
          <div className="p-3 border-t border-white/5">
            <div className="p-3 rounded-xl bg-white/[0.05] border border-white/[0.08]">
              <div className="flex items-center gap-2 mb-1.5">
                <span className={`material-symbols-outlined text-sm ${
                  connectionState.status === 'connected'
                    ? 'text-primary'
                    : 'text-on-surface-variant opacity-50'
                }`}>
                  wifi
                </span>
                <span className="text-sm font-medium text-on-surface font-inter">
                  {connectionState.status === 'connected' ? 'Connected' : 'Not Connected'}
                </span>
              </div>
              {connectionState.deviceName && (
                <p className="text-xs text-on-surface-variant truncate font-inter pl-6">
                  {connectionState.deviceName}
                </p>
              )}
            </div>
          </div>
        </nav>

        {/* Main Content */}
        <main className="flex-1 overflow-auto p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export default Layout;
