import React, { useState, useRef, useEffect } from 'react';
import { useConnection } from '../contexts/ConnectionContext';

type StreamType = 'screen' | 'camera' | null;

function ScreenMirror() {
  const { connectionState } = useConnection();
  const [activeStream, setActiveStream] = useState<StreamType>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showTouchpad, setShowTouchpad] = useState(false);
  const [streamQuality, setStreamQuality] = useState<'low' | 'medium' | 'high'>('medium');
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const previousUrlRef = useRef<string | null>(null);

  const isConnected = connectionState.status === 'connected';

  useEffect(() => {
    // Listen for stream frames
    const removeListener = window.api.onStreamFrame?.((frame: any) => {
      if (videoRef.current && frame.data) {
        // Revoke previous ObjectURL to prevent memory leak
        if (previousUrlRef.current) {
          URL.revokeObjectURL(previousUrlRef.current);
        }
        
        // Convert frame data to blob and display
        const blob = new Blob([Buffer.from(frame.data, 'base64')], { type: 'image/jpeg' });
        const newUrl = URL.createObjectURL(blob);
        videoRef.current.src = newUrl;
        previousUrlRef.current = newUrl;
      }
    });

    return () => {
      // Cleanup: revoke the last ObjectURL
      if (previousUrlRef.current) {
        URL.revokeObjectURL(previousUrlRef.current);
        previousUrlRef.current = null;
      }
      removeListener?.();
    };
  }, []);

  const startStream = async (type: StreamType) => {
    if (!type) return;

    try {
      if (type === 'screen') {
        await window.api.streaming.startScreen();
      } else {
        await window.api.streaming.startCamera();
      }
      setActiveStream(type);
    } catch (error) {
      console.error(`Failed to start ${type} stream:`, error);
    }
  };

  const stopStream = async () => {
    if (activeStream === 'screen') {
      await window.api.streaming.stopScreen();
    } else if (activeStream === 'camera') {
      await window.api.streaming.stopCamera();
    }
    setActiveStream(null);
  };

  const toggleFullscreen = () => {
    if (containerRef.current) {
      if (!document.fullscreenElement) {
        containerRef.current.requestFullscreen();
        setIsFullscreen(true);
      } else {
        document.exitFullscreen();
        setIsFullscreen(false);
      }
    }
  };

  const handleTouchpadInput = (e: React.MouseEvent | React.TouchEvent) => {
    // Send input events to phone
    // This would be implemented based on the input event type
  };

  return (
    <div className="space-y-8 animate-fade-in font-inter">
      <div>
        <h1 className="text-headline-xl text-on-surface mb-2">Screen Mirror</h1>
        <p className="text-body-lg text-on-surface-variant">
          Mirror your phone screen or use it as a webcam
        </p>
      </div>

      {!isConnected ? (
        <div className="glass-panel p-8 text-center">
          <span className="material-symbols-outlined text-primary/50 text-5xl mb-4 block">cast</span>
          <p className="text-body-lg text-on-surface-variant">
            Please connect to a device first
          </p>
        </div>
      ) : (
        <>
          {/* Stream Type Selection */}
          {!activeStream && (
            <div className="grid md:grid-cols-2 gap-gutter">
              <button
                onClick={() => startStream('screen')}
                className="glass-card p-8 text-left group cursor-pointer"
              >
                <div className="flex justify-between items-start mb-4">
                  <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-surface-dim to-surface-container border border-white/10 flex items-center justify-center group-hover:scale-110 transition-transform duration-300">
                    <span className="material-symbols-outlined text-white text-3xl">cast</span>
                  </div>
                  <span className="material-symbols-outlined text-on-surface-variant opacity-0 group-hover:opacity-100 transition-opacity">
                    arrow_forward
                  </span>
                </div>
                <h3 className="text-headline-md text-on-surface mb-2">
                  Screen Mirror
                </h3>
                <p className="text-body-md text-on-surface-variant">
                  Mirror your phone's screen to this PC. Control your phone remotely.
                </p>
              </button>

              <button
                onClick={() => startStream('camera')}
                className="glass-card p-8 text-left group cursor-pointer"
              >
                <div className="flex justify-between items-start mb-4">
                  <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-surface-dim to-surface-container border border-white/10 flex items-center justify-center group-hover:scale-110 transition-transform duration-300">
                    <span className="material-symbols-outlined text-white text-3xl">videocam</span>
                  </div>
                  <span className="material-symbols-outlined text-on-surface-variant opacity-0 group-hover:opacity-100 transition-opacity">
                    arrow_forward
                  </span>
                </div>
                <h3 className="text-headline-md text-on-surface mb-2">
                  Phone as Webcam
                </h3>
                <p className="text-body-md text-on-surface-variant">
                  Use your phone's camera as a webcam for video calls and streaming.
                </p>
              </button>
            </div>
          )}

          {/* Active Stream */}
          {activeStream && (
            <div ref={containerRef} className="relative glass-panel overflow-hidden !rounded-2xl">
              {/* Video Display */}
              <div className="aspect-video flex items-center justify-center bg-[#030a10]">
                <video
                  ref={videoRef}
                  className="max-w-full max-h-full"
                  autoPlay
                  playsInline
                />
                {/* Placeholder when no frames */}
                {!videoRef.current?.src && (
                  <div className="absolute inset-0 flex items-center justify-center text-on-surface">
                    <div className="text-center">
                      <span className="material-symbols-outlined text-6xl mb-4 block text-primary animate-pulse">
                        {activeStream === 'screen' ? 'cast' : 'videocam'}
                      </span>
                      <p className="text-body-lg text-on-surface-variant">Waiting for stream...</p>
                    </div>
                  </div>
                )}
              </div>

              {/* Controls Overlay */}
              <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/80 to-transparent">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="text-on-surface text-label-md">
                      {activeStream === 'screen' ? 'Screen Mirror' : 'Camera Webcam'}
                    </span>
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 bg-error rounded-full animate-pulse" />
                      <span className="text-on-surface-variant text-label-sm">Live</span>
                    </div>
                  </div>

                  <div className="flex items-center gap-2">
                    {activeStream === 'screen' && (
                      <button
                        onClick={() => setShowTouchpad(!showTouchpad)}
                        className={`p-2 rounded-lg transition-all duration-200 ${
                          showTouchpad
                            ? 'bg-primary text-on-primary'
                            : 'bg-white/20 text-white hover:bg-white/30'
                        }`}
                        title="Toggle Touchpad"
                      >
                        <span className="material-symbols-outlined text-lg">touch_app</span>
                      </button>
                    )}
                    
                    <button
                      onClick={toggleFullscreen}
                      className="p-2 bg-white/20 text-white rounded-lg hover:bg-white/30 transition-colors duration-200"
                      title="Fullscreen"
                    >
                      <span className="material-symbols-outlined text-lg">fullscreen</span>
                    </button>

                    <button
                      onClick={stopStream}
                      className="px-4 py-2 bg-error hover:bg-error/80 text-white rounded-glass-btn transition-all duration-200 flex items-center gap-2 text-label-md"
                    >
                      <span className="material-symbols-outlined text-lg">stop</span>
                      Stop
                    </button>
                  </div>
                </div>
              </div>

              {/* Touchpad Overlay */}
              {showTouchpad && activeStream === 'screen' && (
                <div
                  className="absolute inset-0 cursor-crosshair"
                  onMouseMove={handleTouchpadInput}
                  onClick={handleTouchpadInput}
                />
              )}
            </div>
          )}

          {/* Settings */}
          <div className="glass-panel p-6">
            <div className="flex items-center gap-3 mb-4">
              <span className="material-symbols-outlined text-on-surface-variant">settings</span>
              <h3 className="text-headline-md text-on-surface">Stream Settings</h3>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-label-md text-on-surface-variant mb-3">
                  Stream Quality
                </label>
                <div className="flex gap-2">
                  {(['low', 'medium', 'high'] as const).map(quality => (
                    <button
                      key={quality}
                      onClick={() => setStreamQuality(quality)}
                      className={`px-5 py-2.5 rounded-glass-btn capitalize text-label-md transition-all duration-300 ${
                        streamQuality === quality
                          ? 'btn-primary'
                          : 'btn-secondary'
                      }`}
                    >
                      {quality}
                    </button>
                  ))}
                </div>
                <p className="mt-2 text-label-sm text-on-surface-variant">
                  Lower quality uses less bandwidth and reduces latency
                </p>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

export default ScreenMirror;
