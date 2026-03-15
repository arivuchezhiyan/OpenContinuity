import React, { createContext, useContext, useState, useEffect, useRef, ReactNode } from 'react';

interface ConnectionState {
  status: 'disconnected' | 'connecting' | 'connected' | 'error';
  deviceId?: string;
  deviceName?: string;
  error?: string;
}

interface BatteryStatus {
  level: number;
  isCharging: boolean;
  temperature?: number;
}

interface ConnectionContextType {
  connectionState: ConnectionState;
  batteryStatus: BatteryStatus | null;
  connect: (host: string, port: number) => Promise<void>;
  disconnect: () => void;
  discoveredDevices: DiscoveredDevice[];
  startDiscovery: () => void;
  stopDiscovery: () => void;
}

interface DiscoveredDevice {
  name: string;
  host: string;
  port: number;
  platform: string;
}

const ConnectionContext = createContext<ConnectionContextType | null>(null);

export function ConnectionProvider({ children }: { children: ReactNode }) {
  const [connectionState, setConnectionState] = useState<ConnectionState>({
    status: 'disconnected'
  });
  const [batteryStatus, setBatteryStatus] = useState<BatteryStatus | null>(null);
  const [discoveredDevices, setDiscoveredDevices] = useState<DiscoveredDevice[]>([]);
  // Only auto-connect once on first device discovery — ConnectionManager handles all reconnects
  const initialConnectDoneRef = useRef(false);

  useEffect(() => {
    const removeStateListener = window.api.onConnectionStateChanged((state) => {
      setConnectionState(state);
    });
    const removeBatteryListener = window.api.onBatteryUpdate((status) => {
      setBatteryStatus(status);
    });
    const removeDeviceListener = window.api.onDeviceDiscovered((device) => {
      setDiscoveredDevices(prev => {
        const exists = prev.some(d => d.host === device.host && d.port === device.port);
        if (exists) return prev;
        return [...prev, device];
      });
    });

    // When discovery restarts (e.g. network/IP change) main process clears the device map
    // and sends this event — mirror that in the renderer and re-arm auto-connect.
    const removeClearedListener = window.api.onDevicesCleared(() => {
      setDiscoveredDevices([]);
      initialConnectDoneRef.current = false; // allow auto-connect to fire again after new devices arrive
    });

    window.api.connection.getState().then(setConnectionState);
    window.api.discovery.getDevices().then(setDiscoveredDevices);

    return () => {
      removeStateListener();
      removeBatteryListener();
      removeDeviceListener();
      removeClearedListener();
    };
  }, []);

  // Auto-connect to the first discovered device on app start (one-time only).
  // The flag is re-armed whenever devicesCleared fires (network/IP change)
  // or when we go back to disconnected after exhausting retries (no target left).
  useEffect(() => {
    if (initialConnectDoneRef.current) return;
    if (connectionState.status !== 'disconnected') return;
    if (discoveredDevices.length === 0) return;

    initialConnectDoneRef.current = true;
    const first = discoveredDevices[0];
    connect(first.host, first.port);
  }, [discoveredDevices, connectionState.status]);

  const connect = async (host: string, port: number) => {
    try {
      await window.api.connection.connect(host, port);
    } catch (error) {
      console.error('connect() error:', error);
    }
  };

  const disconnect = () => {
    window.api.connection.disconnect();
  };

  const startDiscovery = () => {
    window.api.discovery.start();
  };

  const stopDiscovery = () => {
    window.api.discovery.stop();
  };

  return (
    <ConnectionContext.Provider
      value={{
        connectionState,
        batteryStatus,
        connect,
        disconnect,
        discoveredDevices,
        startDiscovery,
        stopDiscovery
      }}
    >
      {children}
    </ConnectionContext.Provider>
  );
}

export function useConnection() {
  const context = useContext(ConnectionContext);
  if (!context) {
    throw new Error('useConnection must be used within ConnectionProvider');
  }
  return context;
}
