/**
 * Touchpad Manager - receives TOUCHPAD_EVENT messages from Android phone
 * and translates them into native mouse/scroll actions via InputControlManager.
 */

import { EventEmitter } from 'events';
import { ConnectionManager } from '../../main/connection/ConnectionManager';
import { InputControlManager } from '../input/InputControlManager';
import { MessageType, TouchpadEventPayload } from '../../shared/protocol';

export class TouchpadManager extends EventEmitter {
    private connectionManager: ConnectionManager;
    private inputControlManager: InputControlManager;

    constructor(connectionManager: ConnectionManager, inputControlManager: InputControlManager) {
        super();
        this.connectionManager = connectionManager;
        this.inputControlManager = inputControlManager;
        this.setupHandlers();
    }

    private setupHandlers(): void {
        this.connectionManager.registerHandler(MessageType.TOUCHPAD_EVENT, (message) => {
            const payload = message.payload as TouchpadEventPayload;
            this.handleTouchpadEvent(payload);
        });
    }

    private handleTouchpadEvent(payload: TouchpadEventPayload): void {
        switch (payload.eventType) {
            case 'move':
                this.inputControlManager.handleMouseEvent({
                    type: 'move',
                    deltaX: payload.deltaX,
                    deltaY: payload.deltaY,
                });
                break;

            case 'click':
                this.inputControlManager.handleMouseEvent({
                    type: 'click',
                    button: 'left',
                    clicks: 1,
                });
                break;

            case 'right_click':
                this.inputControlManager.handleMouseEvent({
                    type: 'click',
                    button: 'right',
                    clicks: 1,
                });
                break;

            case 'scroll':
                this.inputControlManager.handleMouseEvent({
                    type: 'scroll',
                    deltaY: payload.scrollDelta,
                });
                break;

            case 'drag_start':
                this.inputControlManager.handleMouseEvent({
                    type: 'drag',
                    deltaX: 0,
                    deltaY: 0,
                });
                break;

            case 'drag_end':
                // InputControlManager's drag handler already does mouseToggle('up')
                // after each move. No explicit action needed here.
                break;
        }

        this.emit('touchpadEvent', payload);
    }

    destroy(): void {
        this.removeAllListeners();
    }
}
