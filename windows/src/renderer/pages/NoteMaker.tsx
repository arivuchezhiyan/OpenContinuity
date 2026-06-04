import { useEffect, useRef, useState } from 'react';

interface NotePoint {
  x: number;
  y: number;
}

interface NoteSyncPayload {
  action: 'stroke' | 'clear' | 'pan' | 'zoom';
  tool: 'pen' | 'eraser' | 'cursor';
  color: string;
  thickness: number;
  points: NotePoint[];
  panX?: number;
  panY?: number;
  zoom?: number;
}

interface Stroke {
  tool: 'pen' | 'eraser';
  color: string;
  thickness: number;
  points: NotePoint[];
}

export default function NoteMaker() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [strokes, setStrokes] = useState<Stroke[]>([]);
  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [scale, setScale] = useState(1);
  const isDrawing = useRef(false);

  // Load and setup canvas listener
  useEffect(() => {
    // Resize observer
    const handleResize = () => {
      if (containerRef.current && canvasRef.current) {
        canvasRef.current.width = containerRef.current.clientWidth;
        canvasRef.current.height = containerRef.current.clientHeight;
        redraw();
      }
    };
    window.addEventListener('resize', handleResize);
    handleResize();

    // IPC listener
    const removeListener = window.api.onNoteSync?.((payload: NoteSyncPayload) => {
      if (payload.action === 'stroke') {
        setStrokes((prev) => [...prev, {
          tool: payload.tool as 'pen' | 'eraser',
          color: payload.color,
          thickness: payload.thickness,
          points: payload.points,
        }]);
      } else if (payload.action === 'clear') {
        setStrokes([]);
      } else if (payload.action === 'pan') {
        if (payload.panX !== undefined && payload.panY !== undefined) {
          setOffset(prev => ({ x: prev.x + payload.panX!, y: prev.y + payload.panY! }));
        }
      } else if (payload.action === 'zoom') {
        if (payload.zoom !== undefined) {
          setScale(prev => Math.max(0.1, prev * payload.zoom!));
        }
      }
    });

    return () => {
      window.removeEventListener('resize', handleResize);
      removeListener?.();
    };
  }, []);

  useEffect(() => {
    redraw();
  }, [strokes, offset, scale]);

  const redraw = () => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Reset and clear
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Apply pan and zoom
    ctx.translate(offset.x, offset.y);
    ctx.scale(scale, scale);

    // Draw grid
    drawGrid(ctx, canvas.width, canvas.height, offset.x, offset.y, scale);

    // Draw strokes
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';

    strokes.forEach(stroke => {
      if (stroke.points.length === 0) return;
      
      ctx.beginPath();
      ctx.moveTo(stroke.points[0].x, stroke.points[0].y);
      for (let i = 1; i < stroke.points.length; i++) {
        ctx.lineTo(stroke.points[i].x, stroke.points[i].y);
      }

      if (stroke.tool === 'eraser') {
        ctx.globalCompositeOperation = 'destination-out';
        ctx.lineWidth = stroke.thickness;
        ctx.stroke();
        ctx.globalCompositeOperation = 'source-over';
      } else {
        ctx.lineWidth = stroke.thickness;
        ctx.strokeStyle = stroke.color;
        ctx.stroke();
      }
    });
  };

  const drawGrid = (ctx: CanvasRenderingContext2D, width: number, height: number, offsetX: number, offsetY: number, scale: number) => {
    const gridSize = 50;
    const scaledGridSize = gridSize;
    ctx.strokeStyle = 'rgba(16, 185, 129, 0.05)';
    ctx.lineWidth = 1 / scale;

    const startX = -offsetX / scale - width / scale;
    const endX = startX + width / scale * 3;
    const startY = -offsetY / scale - height / scale;
    const endY = startY + height / scale * 3;

    ctx.beginPath();
    for (let x = Math.floor(startX / scaledGridSize) * scaledGridSize; x < endX; x += scaledGridSize) {
      ctx.moveTo(x, startY);
      ctx.lineTo(x, endY);
    }
    for (let y = Math.floor(startY / scaledGridSize) * scaledGridSize; y < endY; y += scaledGridSize) {
      ctx.moveTo(startX, y);
      ctx.lineTo(endX, y);
    }
    ctx.stroke();
  };

  return (
    <div className="flex flex-col h-full bg-[#06141B] overflow-hidden relative font-inter">
      {/* Top-right toolbar */}
      <div className="absolute top-4 right-4 z-10 flex gap-2 glass-panel !rounded-xl p-1.5">
        <button 
          onClick={() => setStrokes([])}
          className="p-2.5 rounded-lg hover:bg-white/10 text-on-surface-variant hover:text-error transition-colors duration-200"
          title="Clear Board"
        >
          <span className="material-symbols-outlined text-xl">delete</span>
        </button>
        <button 
          onClick={() => setOffset({x:0, y:0})}
          className="p-2.5 rounded-lg hover:bg-white/10 text-on-surface-variant hover:text-primary transition-colors duration-200"
          title="Reset View"
        >
          <span className="material-symbols-outlined text-xl">fit_screen</span>
        </button>
      </div>

      {/* Bottom-left info */}
      <div className="absolute bottom-4 left-4 z-10 glass-panel !rounded-xl px-5 py-2.5 flex items-center gap-3">
        <span className="material-symbols-outlined text-primary text-lg">draw</span>
        <span className="text-label-md text-on-surface-variant">
          Use your mobile device to draw, pan, and zoom.
        </span>
      </div>

      {/* Bottom-right zoom controls */}
      <div className="absolute bottom-4 right-4 z-10 flex flex-col gap-2">
        <div className="glass-panel !rounded-lg px-2 py-1 flex items-center justify-between">
          <button className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-on-surface hover:bg-white/10 rounded-md transition-colors">
            <span className="material-symbols-outlined text-lg">remove</span>
          </button>
          <span className="text-label-sm text-on-surface font-medium w-12 text-center">{Math.round(scale * 100)}%</span>
          <button className="w-8 h-8 flex items-center justify-center text-on-surface-variant hover:text-on-surface hover:bg-white/10 rounded-md transition-colors">
            <span className="material-symbols-outlined text-lg">add</span>
          </button>
        </div>
      </div>

      <div ref={containerRef} className="flex-1 w-full h-full cursor-crosshair">
        <canvas ref={canvasRef} className="block w-full h-full" />
      </div>
    </div>
  );
}
