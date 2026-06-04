import React, { useState, useEffect } from 'react';
import { useTheme } from '../contexts/ThemeContext';

interface SettingsState {
  autoStart: boolean;
  minimizeToTray: boolean;
  clipboardSync: boolean;
  notificationSync: boolean;
  smsSync: boolean;
  screenshotSync: boolean;
  downloadPath: string;
}

function Settings() {
  const { theme, setTheme, isDark } = useTheme();
  const [settings, setSettings] = useState<SettingsState>({
    autoStart: false,
    minimizeToTray: true,
    clipboardSync: true,
    notificationSync: true,
    smsSync: true,
    screenshotSync: true,
    downloadPath: ''
  });

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const savedSettings = await window.api.settings.get();
      if (savedSettings) {
        setSettings(prev => ({ ...prev, ...savedSettings }));
      }
    } catch (error) {
      console.error('Failed to load settings:', error);
    }
  };

  const updateSetting = async (key: keyof SettingsState, value: any) => {
    const newSettings = { ...settings, [key]: value };
    setSettings(newSettings);
    await window.api.settings.set(newSettings);
  };

  const selectDownloadPath = async () => {
    // This would open a folder dialog
    // For now, just show current path
  };

  return (
    <div className="space-y-8 animate-fade-in max-w-3xl font-inter">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-headline-xl text-on-surface tracking-tight mb-1">Settings</h1>
          <p className="text-body-md text-on-surface-variant">
            Manage your OpenContinuity preferences and configurations.
          </p>
        </div>
        <button className="btn-primary px-6 py-2.5 text-label-md">
          Save Changes
        </button>
      </div>

      <div className="space-y-6">
        {/* Section 1: Appearance */}
        <section className="glass-panel p-6 flex flex-col gap-4 relative overflow-hidden group">
          {/* Decorative hover glow */}
          <div className="absolute -top-10 -right-10 w-32 h-32 bg-primary/10 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-700" />
          
          <div className="flex items-center gap-3 border-b border-white/10 pb-3">
            <div className="w-10 h-10 rounded-lg bg-white/5 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined">palette</span>
            </div>
            <h2 className="text-headline-md text-on-surface">Appearance</h2>
          </div>

          <div>
            <label className="block text-label-md text-on-surface-variant mb-3">
              System Theme
            </label>
            <div className="glass-panel !rounded-xl p-1 flex relative">
              {/* Selected background pill */}
              <div 
                className="absolute inset-y-1 bg-primary/20 rounded-lg border border-primary/30 z-0 transition-all duration-300"
                style={{
                  width: 'calc(33.33% - 4px)',
                  left: theme === 'dark' ? '4px' : theme === 'light' ? 'calc(33.33% + 2px)' : 'calc(66.66%)',
                }}
              />
              <button
                onClick={() => setTheme('dark')}
                className={`flex-1 py-2.5 text-label-md z-10 text-center flex flex-col items-center gap-1 transition-colors ${
                  theme === 'dark' ? 'text-primary' : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-xl">dark_mode</span>
                Dark
              </button>
              <button
                onClick={() => setTheme('light')}
                className={`flex-1 py-2.5 text-label-md z-10 text-center flex flex-col items-center gap-1 transition-colors ${
                  theme === 'light' ? 'text-primary' : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-xl">light_mode</span>
                Light
              </button>
              <button
                onClick={() => setTheme('system')}
                className={`flex-1 py-2.5 text-label-md z-10 text-center flex flex-col items-center gap-1 transition-colors ${
                  theme === 'system' ? 'text-primary' : 'text-on-surface-variant hover:text-on-surface'
                }`}
              >
                <span className="material-symbols-outlined text-xl">settings_suggest</span>
                Auto
              </button>
            </div>
          </div>
        </section>

        {/* Section 2: General */}
        <section className="glass-panel p-6 flex flex-col gap-4 relative overflow-hidden group">
          <div className="absolute -top-10 -right-10 w-32 h-32 bg-secondary/10 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-700" />
          
          <div className="flex items-center gap-3 border-b border-white/10 pb-3">
            <div className="w-10 h-10 rounded-lg bg-white/5 flex items-center justify-center text-secondary">
              <span className="material-symbols-outlined">tune</span>
            </div>
            <h2 className="text-headline-md text-on-surface">General Configuration</h2>
          </div>

          <div className="space-y-1">
            <ToggleSetting
              label="Start on Windows login"
              description="Automatically start OpenContinuity when you sign in"
              checked={settings.autoStart}
              onChange={(checked) => updateSetting('autoStart', checked)}
            />
            <ToggleSetting
              label="Minimize to system tray"
              description="Keep running in the background when you close the window"
              checked={settings.minimizeToTray}
              onChange={(checked) => updateSetting('minimizeToTray', checked)}
            />
          </div>
        </section>

        {/* Section 3: Sync Features */}
        <section className="glass-panel p-6 flex flex-col gap-4 relative overflow-hidden group">
          <div className="absolute -top-10 -right-10 w-32 h-32 bg-tertiary/10 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-700" />
          
          <div className="flex items-center gap-3 border-b border-white/10 pb-3">
            <div className="w-10 h-10 rounded-lg bg-white/5 flex items-center justify-center text-tertiary">
              <span className="material-symbols-outlined">sync_lock</span>
            </div>
            <h2 className="text-headline-md text-on-surface">Sync & Security</h2>
          </div>

          {/* Sync status card */}
          <div className="flex items-center justify-between p-3 rounded-xl bg-white/5 border border-white/10">
            <div className="flex items-start gap-3">
              <span className="material-symbols-outlined text-tertiary mt-0.5">cloud_sync</span>
              <div>
                <h3 className="text-label-md text-on-surface">Real-time Node Synchronization</h3>
                <p className="text-body-md text-on-surface-variant text-sm mt-0.5">Maintain continuous connection with enterprise grid.</p>
                <div className="flex items-center gap-2 mt-2">
                  <span className="w-2 h-2 rounded-full bg-primary animate-pulse" />
                  <span className="text-[11px] text-primary font-medium tracking-wide uppercase">Active Connection</span>
                </div>
              </div>
            </div>
          </div>

          <div className="space-y-1">
            <ToggleSetting
              label="Clipboard Sync"
              description="Automatically sync clipboard content between devices"
              icon="content_copy"
              checked={settings.clipboardSync}
              onChange={(checked) => updateSetting('clipboardSync', checked)}
            />
            <ToggleSetting
              label="Notification Sync"
              description="Show phone notifications on this PC"
              icon="notifications"
              checked={settings.notificationSync}
              onChange={(checked) => updateSetting('notificationSync', checked)}
            />
            <ToggleSetting
              label="SMS Sync"
              description="Send and receive SMS from this PC"
              icon="chat"
              checked={settings.smsSync}
              onChange={(checked) => updateSetting('smsSync', checked)}
            />
            <ToggleSetting
              label="Screenshot Sync"
              description="Automatically receive screenshots from your phone"
              icon="screenshot_monitor"
              checked={settings.screenshotSync}
              onChange={(checked) => updateSetting('screenshotSync', checked)}
            />
          </div>
        </section>

        {/* Section 4: File Transfer */}
        <section className="glass-panel p-6 flex flex-col gap-4 relative overflow-hidden group">
          <div className="absolute -top-10 -right-10 w-32 h-32 bg-primary/10 rounded-full blur-3xl opacity-0 group-hover:opacity-100 transition-opacity duration-700" />
          
          <div className="flex items-center gap-3 border-b border-white/10 pb-3">
            <div className="w-10 h-10 rounded-lg bg-white/5 flex items-center justify-center text-primary">
              <span className="material-symbols-outlined">folder</span>
            </div>
            <h2 className="text-headline-md text-on-surface">File Transfer</h2>
          </div>

          <div>
            <label className="block text-label-md text-on-surface-variant mb-2">
              Download Location
            </label>
            <div className="flex gap-3">
              <input
                type="text"
                value={settings.downloadPath || 'Downloads/OpenContinuity'}
                readOnly
                className="flex-1 px-4 py-3 glass-input text-body-md cursor-default"
              />
              <button
                onClick={selectDownloadPath}
                className="btn-secondary px-4 py-2.5 text-label-md"
              >
                Browse...
              </button>
            </div>
          </div>
        </section>

        {/* Section 5: About */}
        <section className="glass-panel p-6 flex flex-col gap-4">
          <div className="flex items-center gap-3 border-b border-white/10 pb-3">
            <div className="w-10 h-10 rounded-lg bg-white/5 flex items-center justify-center text-on-surface-variant">
              <span className="material-symbols-outlined">info</span>
            </div>
            <h2 className="text-headline-md text-on-surface">About</h2>
          </div>

          <div className="space-y-2 text-sm">
            <p className="text-on-surface"><span className="font-semibold text-primary">OpenContinuity</span> v1.0.0</p>
            <p className="text-on-surface-variant">Windows ↔ Android Ecosystem Integration</p>
            <p className="text-on-surface-variant/50">© 2024 OpenContinuity</p>
          </div>
        </section>
      </div>
    </div>
  );
}

interface ToggleSettingProps {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  icon?: string;
}

function ToggleSetting({ label, description, checked, onChange, icon }: ToggleSettingProps) {
  return (
    <div className="flex items-center justify-between p-3 rounded-xl hover:bg-white/5 transition-colors duration-200 group">
      <div className="flex items-center gap-3">
        {icon && (
          <span className="material-symbols-outlined text-on-surface-variant text-xl">{icon}</span>
        )}
        <div>
          <p className="text-label-md text-on-surface group-hover:text-primary transition-colors">{label}</p>
          <p className="text-label-sm text-on-surface-variant font-normal mt-0.5">{description}</p>
        </div>
      </div>
      <button
        onClick={() => onChange(!checked)}
        className={`relative w-11 h-6 rounded-full transition-all duration-300 ${
          checked 
            ? 'bg-primary-container shadow-[0_0_10px_rgba(16,185,129,0.3)]' 
            : 'bg-surface-container border border-white/10'
        }`}
      >
        <span
          className={`absolute top-1 left-1 w-4 h-4 rounded-full transition-all duration-300 ${
            checked 
              ? 'translate-x-5 bg-white' 
              : 'bg-on-surface-variant/50'
          }`}
        />
      </button>
    </div>
  );
}

export default Settings;
