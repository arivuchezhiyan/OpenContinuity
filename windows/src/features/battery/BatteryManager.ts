/**
 * Battery Manager - receives and stores battery/device status from Android
 */

import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { MessageType, BatteryStatusPayload, DeviceStatusPayload } from '../../shared/protocol';

export class BatteryManager extends EventEmitter {
    private connectionManager: ConnectionManager;
    private lastBatteryStatus: BatteryStatusPayload | null = null;
    private lastDeviceStatus: DeviceStatusPayload | null = null;

    constructor(connectionManager: ConnectionManager) {
        super();
        this.connectionManager = connectionManager;
        this.setupHandlers();
    }

    private setupHandlers(): void {
        this.connectionManager.registerHandler(MessageType.BATTERY_STATUS, (message) => {
            this.lastBatteryStatus = message.payload as BatteryStatusPayload;
            this.emit('batteryUpdate', this.lastBatteryStatus);
        });

        this.connectionManager.registerHandler(MessageType.DEVICE_STATUS, (message) => {
            this.lastDeviceStatus = message.payload as DeviceStatusPayload;
            // Also extract battery from device status
            if (this.lastDeviceStatus.battery) {
                this.lastBatteryStatus = this.lastDeviceStatus.battery;
                this.emit('batteryUpdate', this.lastBatteryStatus);
            }
            this.emit('deviceStatusUpdate', this.lastDeviceStatus);
        });
    }

    getBatteryStatus(): BatteryStatusPayload | null {
        return this.lastBatteryStatus;
    }

    getDeviceStatus(): DeviceStatusPayload | null {
        return this.lastDeviceStatus;
    }

    destroy(): void {
        this.removeAllListeners();
    }
}
