import React, { useState, useEffect } from 'react';
import { useTheme } from '../contexts/ThemeContext';
import {
  Cog6ToothIcon,
  MoonIcon,
  SunIcon,
  ComputerDesktopIcon,
  ClipboardDocumentIcon,
  BellIcon,
  ChatBubbleLeftRightIcon,
  PhotoIcon,
  FolderIcon,
  ShieldCheckIcon,
  InformationCircleIcon
} from '@heroicons/react/24/outline';

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
    <div className="space-y-6 animate-fade-in max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Settings</h1>
        <p className="text-gray-500 dark:text-gray-400 mt-1">
          Configure OpenContinuity preferences
        </p>
      </div>

      {/* Appearance */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-white mb-4">
          <SunIcon className="w-5 h-5" />
          Appearance
        </h2>

        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
            Theme
          </label>
          <div className="flex gap-3">
            <button
              onClick={() => setTheme('light')}
              className={`flex-1 flex items-center justify-center gap-2 p-3 rounded-lg border-2 transition-all ${
                theme === 'light'
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
              }`}
            >
              <SunIcon className="w-5 h-5" />
              <span className="text-sm font-medium">Light</span>
            </button>
            <button
              onClick={() => setTheme('dark')}
              className={`flex-1 flex items-center justify-center gap-2 p-3 rounded-lg border-2 transition-all ${
                theme === 'dark'
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
              }`}
            >
              <MoonIcon className="w-5 h-5" />
              <span className="text-sm font-medium">Dark</span>
            </button>
            <button
              onClick={() => setTheme('system')}
              className={`flex-1 flex items-center justify-center gap-2 p-3 rounded-lg border-2 transition-all ${
                theme === 'system'
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                  : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
              }`}
            >
              <ComputerDesktopIcon className="w-5 h-5" />
              <span className="text-sm font-medium">System</span>
            </button>
          </div>
        </div>
      </div>

      {/* General */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-white mb-4">
          <Cog6ToothIcon className="w-5 h-5" />
          General
        </h2>

        <div className="space-y-4">
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
      </div>

      {/* Sync Features */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-white mb-4">
          <ShieldCheckIcon className="w-5 h-5" />
          Sync Features
        </h2>

        <div className="space-y-4">
          <ToggleSetting
            label="Clipboard Sync"
            description="Automatically sync clipboard content between devices"
            icon={ClipboardDocumentIcon}
            checked={settings.clipboardSync}
            onChange={(checked) => updateSetting('clipboardSync', checked)}
          />
          <ToggleSetting
            label="Notification Sync"
            description="Show phone notifications on this PC"
            icon={BellIcon}
            checked={settings.notificationSync}
            onChange={(checked) => updateSetting('notificationSync', checked)}
          />
          <ToggleSetting
            label="SMS Sync"
            description="Send and receive SMS from this PC"
            icon={ChatBubbleLeftRightIcon}
            checked={settings.smsSync}
            onChange={(checked) => updateSetting('smsSync', checked)}
          />
          <ToggleSetting
            label="Screenshot Sync"
            description="Automatically receive screenshots from your phone"
            icon={PhotoIcon}
            checked={settings.screenshotSync}
            onChange={(checked) => updateSetting('screenshotSync', checked)}
          />
        </div>
      </div>

      {/* File Transfer */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-white mb-4">
          <FolderIcon className="w-5 h-5" />
          File Transfer
        </h2>

        <div>
          <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            Download Location
          </label>
          <div className="flex gap-3">
            <input
              type="text"
              value={settings.downloadPath || 'Downloads/OpenContinuity'}
              readOnly
              className="flex-1 px-3 py-2 bg-gray-100 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-gray-700 dark:text-gray-300"
            />
            <button
              onClick={selectDownloadPath}
              className="px-4 py-2 bg-gray-100 dark:bg-gray-700 hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-700 dark:text-gray-300 rounded-lg transition-colors"
            >
              Browse...
            </button>
          </div>
        </div>
      </div>

      {/* About */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-white mb-4">
          <InformationCircleIcon className="w-5 h-5" />
          About
        </h2>

        <div className="space-y-2 text-sm text-gray-600 dark:text-gray-400">
          <p><span className="font-medium">OpenContinuity</span> v1.0.0</p>
          <p>Windows ↔ Android Ecosystem Integration</p>
          <p className="text-gray-400">© 2024 OpenContinuity</p>
        </div>
      </div>
    </div>
  );
}

interface ToggleSettingProps {
  label: string;
  description: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  icon?: React.ComponentType<{ className?: string }>;
}

function ToggleSetting({ label, description, checked, onChange, icon: Icon }: ToggleSettingProps) {
  return (
    <div className="flex items-center justify-between py-2">
      <div className="flex items-center gap-3">
        {Icon && <Icon className="w-5 h-5 text-gray-400" />}
        <div>
          <p className="font-medium text-gray-900 dark:text-white">{label}</p>
          <p className="text-sm text-gray-500 dark:text-gray-400">{description}</p>
        </div>
      </div>
      <button
        onClick={() => onChange(!checked)}
        className={`relative w-11 h-6 rounded-full transition-colors ${
          checked ? 'bg-primary-500' : 'bg-gray-300 dark:bg-gray-600'
        }`}
      >
        <span
          className={`absolute top-1 left-1 w-4 h-4 bg-white rounded-full transition-transform ${
            checked ? 'translate-x-5' : ''
          }`}
        />
      </button>
    </div>
  );
}

export default Settings;
