import React, { useState, useRef, useEffect } from 'react';
import { useConnection } from '../contexts/ConnectionContext';
import {
  TvIcon,
  VideoCameraIcon,
  PlayIcon,
  StopIcon,
  ArrowsPointingOutIcon,
  CogIcon,
  CursorArrowRaysIcon
} from '@heroicons/react/24/outline';

type StreamType = 'screen' | 'camera' | null;

function ScreenMirror() {
  const { connectionState } = useConnection();
  const [activeStream, setActiveStream] = useState<StreamType>(null);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showTouchpad, setShowTouchpad] = useState(false);
  const [streamQuality, setStreamQuality] = useState<'low' | 'medium' | 'high'>('medium');
  const videoRef = useRef<HTMLVideoElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const isConnected = connectionState.status === 'connected';

  useEffect(() => {
    let lastObjectUrl: string | null = null;

    const removeListener = window.api.onStreamFrame?.((frame: any) => {
      if (videoRef.current && frame.data) {
        const blob = new Blob([Buffer.from(frame.data, 'base64')], { type: 'image/jpeg' });
        const nextUrl = URL.createObjectURL(blob);
        videoRef.current.src = nextUrl;
        if (lastObjectUrl) {
          URL.revokeObjectURL(lastObjectUrl);
        }
        lastObjectUrl = nextUrl;
      }
    });

    return () => {
      removeListener?.();
      if (lastObjectUrl) {
        URL.revokeObjectURL(lastObjectUrl);
      }
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
    <div className="space-y-6 animate-fade-in">
      <div>
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">Screen Mirror</h1>
        <p className="text-gray-500 dark:text-gray-400 mt-1">
          Mirror your phone screen or use it as a webcam
        </p>
      </div>

      {!isConnected ? (
        <div className="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-xl p-6 text-center">
          <p className="text-yellow-800 dark:text-yellow-200">
            Please connect to a device first
          </p>
        </div>
      ) : (
        <>
          {/* Stream Type Selection */}
          {!activeStream && (
            <div className="grid md:grid-cols-2 gap-6">
              <button
                onClick={() => startStream('screen')}
                className="p-8 bg-white dark:bg-gray-800 rounded-xl border-2 border-gray-200 dark:border-gray-700 hover:border-primary-500 transition-all text-left group"
              >
                <div className="w-16 h-16 bg-indigo-100 dark:bg-indigo-900 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                  <TvIcon className="w-8 h-8 text-indigo-500" />
                </div>
                <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
                  Screen Mirror
                </h3>
                <p className="text-gray-500 dark:text-gray-400">
                  Mirror your phone's screen to this PC. Control your phone remotely.
                </p>
              </button>

              <button
                onClick={() => startStream('camera')}
                className="p-8 bg-white dark:bg-gray-800 rounded-xl border-2 border-gray-200 dark:border-gray-700 hover:border-primary-500 transition-all text-left group"
              >
                <div className="w-16 h-16 bg-pink-100 dark:bg-pink-900 rounded-xl flex items-center justify-center mb-4 group-hover:scale-110 transition-transform">
                  <VideoCameraIcon className="w-8 h-8 text-pink-500" />
                </div>
                <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
                  Phone as Webcam
                </h3>
                <p className="text-gray-500 dark:text-gray-400">
                  Use your phone's camera as a webcam for video calls and streaming.
                </p>
              </button>
            </div>
          )}

          {/* Active Stream */}
          {activeStream && (
            <div ref={containerRef} className="relative bg-black rounded-xl overflow-hidden">
              {/* Video Display */}
              <div className="aspect-video flex items-center justify-center">
                <video
                  ref={videoRef}
                  className="max-w-full max-h-full"
                  autoPlay
                  playsInline
                />
                {/* Placeholder when no frames */}
                {!videoRef.current?.src && (
                  <div className="absolute inset-0 flex items-center justify-center text-white">
                    <div className="text-center">
                      {activeStream === 'screen' ? (
                        <TvIcon className="w-16 h-16 mx-auto mb-4 animate-pulse" />
                      ) : (
                        <VideoCameraIcon className="w-16 h-16 mx-auto mb-4 animate-pulse" />
                      )}
                      <p>Waiting for stream...</p>
                    </div>
                  </div>
                )}
              </div>

              {/* Controls Overlay */}
              <div className="absolute bottom-0 left-0 right-0 p-4 bg-gradient-to-t from-black/80 to-transparent">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-white text-sm">
                      {activeStream === 'screen' ? 'Screen Mirror' : 'Camera Webcam'}
                    </span>
                    <span className="w-2 h-2 bg-red-500 rounded-full animate-pulse" />
                    <span className="text-white/60 text-sm">Live</span>
                  </div>

                  <div className="flex items-center gap-2">
                    {activeStream === 'screen' && (
                      <button
                        onClick={() => setShowTouchpad(!showTouchpad)}
                        className={`p-2 rounded-lg transition-colors ${
                          showTouchpad
                            ? 'bg-primary-500 text-white'
                            : 'bg-white/20 text-white hover:bg-white/30'
                        }`}
                        title="Toggle Touchpad"
                      >
                        <CursorArrowRaysIcon className="w-5 h-5" />
                      </button>
                    )}
                    
                    <button
                      onClick={toggleFullscreen}
                      className="p-2 bg-white/20 text-white rounded-lg hover:bg-white/30 transition-colors"
                      title="Fullscreen"
                    >
                      <ArrowsPointingOutIcon className="w-5 h-5" />
                    </button>

                    <button
                      onClick={stopStream}
                      className="px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors flex items-center gap-2"
                    >
                      <StopIcon className="w-5 h-5" />
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
          <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
            <div className="flex items-center gap-3 mb-4">
              <CogIcon className="w-5 h-5 text-gray-400" />
              <h3 className="font-semibold text-gray-900 dark:text-white">Stream Settings</h3>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
                  Stream Quality
                </label>
                <div className="flex gap-2">
                  {(['low', 'medium', 'high'] as const).map(quality => (
                    <button
                      key={quality}
                      onClick={() => setStreamQuality(quality)}
                      className={`px-4 py-2 rounded-lg capitalize transition-colors ${
                        streamQuality === quality
                          ? 'bg-primary-500 text-white'
                          : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
                      }`}
                    >
                      {quality}
                    </button>
                  ))}
                </div>
                <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">
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
