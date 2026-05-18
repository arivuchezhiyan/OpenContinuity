/**
 * Input Control Manager - handles mouse/keyboard input injection
 */

import { EventEmitter } from 'events';
import robot from 'robotjs';

export interface MouseEvent {
  type: 'move' | 'click' | 'scroll' | 'drag';
  x?: number;
  y?: number;
  deltaX?: number;
  deltaY?: number;
  button?: 'left' | 'right' | 'middle';
  clicks?: number;
}

export interface KeyboardEvent {
  type: 'keypress' | 'keydown' | 'keyup';
  key: string;
  modifiers?: ('shift' | 'control' | 'alt' | 'command')[];
}

export class InputControlManager extends EventEmitter {
  private isEnabled: boolean = true;
  private pendingDeltaX = 0;
  private pendingDeltaY = 0;
  private isDragging = false;
  private batchInterval: NodeJS.Timeout | null = null;

  constructor() {
    super();
    // Set mouse movement speed — zero delay for fastest possible response
    robot.setMouseDelay(0);
    robot.setKeyboardDelay(0);

    // Process batched mouse movements at ~60 FPS to prevent trackpad lag
    this.batchInterval = setInterval(() => this.applyBatchedMouseMove(), 16);
  }

  destroy(): void {
    if (this.batchInterval) {
      clearInterval(this.batchInterval);
      this.batchInterval = null;
    }
    this.removeAllListeners();
  }

  setEnabled(enabled: boolean): void {
    this.isEnabled = enabled;
  }

  /**
   * Pointer acceleration matching Windows "Enhance pointer precision" feel.
   * Android sends raw touch-pixel deltas (SENSITIVITY = 1.0, no pre-scale).
   * This curve maps them to screen pixels:
   *   raw 1 px  → ~2.3 px   (fine precision)
   *   raw 3 px  → ~8.7 px   (normal slow movement)
   *   raw 6 px  → ~20.8 px  (medium speed)
   *   raw 10 px → ~42 px    (fast sweep)
   *   raw 15 px → ~97 px    (quick flick)
   * Previously: flat 3.5 (Android) × 2.0 (Windows) = 7× for ALL speeds → overshoot + jitter.
   */
  private accelerate(delta: number): number {
    if (delta === 0) return 0;
    const abs = Math.abs(delta);
    const sign = delta < 0 ? -1 : 1;
    // Adjusted slightly lower since batched deltas are larger per frame
    return sign * abs * (1.5 + abs * 0.15);
  }

  handleMouseEvent(event: MouseEvent): void {
    if (!this.isEnabled) return;

    try {
      switch (event.type) {
        case 'move':
          this.handleMouseMove(event);
          break;
        case 'click':
          this.handleMouseClick(event);
          break;
        case 'scroll':
          this.handleMouseScroll(event);
          break;
        case 'drag':
          this.handleMouseDrag(event);
          break;
      }
    } catch (error) {
      console.error('Error handling mouse event:', error);
    }
  }

  handleKeyboardEvent(event: KeyboardEvent): void {
    if (!this.isEnabled) return;

    try {
      const key = this.mapKey(event.key);
      const modifiers = event.modifiers || [];

      switch (event.type) {
        case 'keypress':
          robot.keyTap(key, modifiers);
          break;
        case 'keydown':
          robot.keyToggle(key, 'down', modifiers);
          break;
        case 'keyup':
          robot.keyToggle(key, 'up', modifiers);
          break;
      }
    } catch (error) {
      console.error('Error handling keyboard event:', error);
    }
  }

  private applyBatchedMouseMove(): void {
    if (!this.isEnabled || (this.pendingDeltaX === 0 && this.pendingDeltaY === 0)) return;

    try {
      const dx = this.pendingDeltaX;
      const dy = this.pendingDeltaY;
      
      this.pendingDeltaX = 0;
      this.pendingDeltaY = 0;

      const currentPos = robot.getMousePos();
      const screenSize = robot.getScreenSize();
      const newX = Math.max(0, Math.min(screenSize.width - 1,  Math.round(currentPos.x + this.accelerate(dx))));
      const newY = Math.max(0, Math.min(screenSize.height - 1, Math.round(currentPos.y + this.accelerate(dy))));

      if (this.isDragging) {
        robot.mouseToggle('down', 'left');
        robot.moveMouse(newX, newY);
        robot.mouseToggle('up', 'left');
      } else {
        robot.moveMouse(newX, newY);
      }
    } catch (error) {
      console.error('Error applying batched move:', error);
    }
  }

  private handleMouseMove(event: MouseEvent): void {
    if (event.deltaX !== undefined && event.deltaY !== undefined) {
      this.pendingDeltaX += event.deltaX;
      this.pendingDeltaY += event.deltaY;
      this.isDragging = false;
    } else if (event.x !== undefined && event.y !== undefined) {
      // Absolute movement immediately applied
      robot.moveMouse(event.x, event.y);
    }
  }

  private handleMouseClick(event: MouseEvent): void {
    const button = event.button || 'left';
    const clicks = event.clicks || 1;

    // Flush any pending movement before clicking
    this.applyBatchedMouseMove();

    // Move to position first if specified
    if (event.x !== undefined && event.y !== undefined) {
      robot.moveMouse(event.x, event.y);
    }

    // Click
    if (clicks === 2) {
      robot.mouseClick(button, true); // double click
    } else {
      robot.mouseClick(button);
    }
  }

  private handleMouseScroll(event: MouseEvent): void {
    const deltaY = event.deltaY || 0;
    if (deltaY !== 0) {
      // Proportional scrolling: scale raw touch pixels to scroll lines (1–8).
      // Slow two-finger drag = 1 line, fast flick = several lines — like a trackpad.
      const lines = Math.max(1, Math.min(8, Math.round(Math.abs(deltaY) / 4)));
      robot.scrollMouse(0, deltaY > 0 ? -lines : lines);
    }
    // Note: robotjs doesn't support horizontal scrolling well
  }

  private handleMouseDrag(event: MouseEvent): void {
    if (event.deltaX !== undefined && event.deltaY !== undefined) {
      this.pendingDeltaX += event.deltaX;
      this.pendingDeltaY += event.deltaY;
      this.isDragging = true;
    }
  }

  private mapKey(key: string): string {
    // Map common key names to robotjs format
    const keyMap: Record<string, string> = {
      'Enter': 'enter',
      'Escape': 'escape',
      'Backspace': 'backspace',
      'Tab': 'tab',
      'Space': 'space',
      'ArrowUp': 'up',
      'ArrowDown': 'down',
      'ArrowLeft': 'left',
      'ArrowRight': 'right',
      'Delete': 'delete',
      'Home': 'home',
      'End': 'end',
      'PageUp': 'pageup',
      'PageDown': 'pagedown',
      'F1': 'f1',
      'F2': 'f2',
      'F3': 'f3',
      'F4': 'f4',
      'F5': 'f5',
      'F6': 'f6',
      'F7': 'f7',
      'F8': 'f8',
      'F9': 'f9',
      'F10': 'f10',
      'F11': 'f11',
      'F12': 'f12',
      'Control': 'control',
      'Shift': 'shift',
      'Alt': 'alt',
      'Meta': 'command',
      'CapsLock': 'capslock',
      'NumLock': 'numlock',
      'PrintScreen': 'printscreen',
      'Insert': 'insert'
    };

    return keyMap[key] || key.toLowerCase();
  }

  // Type a string of text
  typeText(text: string): void {
    if (!this.isEnabled) return;
    robot.typeString(text);
  }

  // Get current mouse position
  getMousePosition(): { x: number; y: number } {
    return robot.getMousePos();
  }

  // Get screen size
  getScreenSize(): { width: number; height: number } {
    return robot.getScreenSize();
  }
}
