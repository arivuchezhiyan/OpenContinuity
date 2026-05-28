import React, { useRef } from 'react';
import { CursorArrowRaysIcon } from '@heroicons/react/24/outline';
import { useConnection } from '../contexts/ConnectionContext';

function Touchpad() {
  const { connectionState } = useConnection();
  const areaRef = useRef<HTMLDivElement>(null);
  const isConnected = connectionState.status === 'connected';

  const sendEvent = (type: string, x: number, y: number, extra?: Record<string, number>) => {
    if (!isConnected) return;
    window.api.input?.send({
      type,
      x,
      y,
      ...extra,
    });
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    const rect = areaRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    sendEvent('touch_down', x, y);
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (e.buttons === 0) return;
    const rect = areaRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    sendEvent('touch_move', x, y);
  };

  const handleMouseUp = (e: React.MouseEvent) => {
    const rect = areaRef.current?.getBoundingClientRect();
    if (!rect) return;
    const x = (e.clientX - rect.left) / rect.width;
    const y = (e.clientY - rect.top) / rect.height;
    sendEvent('touch_up', x, y);
  };

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    sendEvent('scroll', 0, 0, { deltaX: e.deltaX, deltaY: e.deltaY });
  };

  return (
    <div className="space-y-4 animate-fade-in h-full flex flex-col">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
          <CursorArrowRaysIcon className="w-7 h-7" />
          Touchpad
        </h1>
        <p className="text-gray-500 dark:text-gray-400 mt-1">
          Use this area to control your phone pointer when connected
        </p>
      </div>

      <div
        ref={areaRef}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        onWheel={handleWheel}
        className={`flex-1 min-h-[400px] rounded-xl border-2 border-dashed ${
          isConnected
            ? 'border-primary-400 bg-gray-50 dark:bg-gray-800 cursor-crosshair'
            : 'border-gray-300 dark:border-gray-600 opacity-50 cursor-not-allowed'
        } flex items-center justify-center`}
      >
        <p className="text-gray-500 dark:text-gray-400 pointer-events-none">
          {isConnected ? 'Drag to move • Scroll to scroll' : 'Connect your phone first'}
        </p>
      </div>
    </div>
  );
}

export default Touchpad;
