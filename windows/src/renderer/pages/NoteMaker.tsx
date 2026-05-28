import { useEffect, useRef, useState } from 'react';
import { FiEdit3, FiMaximize, FiMinimize, FiTrash2 } from 'react-icons/fi';

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
    const scaledGridSize = gridSize; // keep grid visual size constant or scale it
    ctx.strokeStyle = '#e2e8f015'; // Very faint grid lines
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
    <div className="flex flex-col h-full bg-slate-900 overflow-hidden relative">
      <div className="absolute top-4 right-4 z-10 flex gap-2 bg-slate-800 p-2 rounded-xl border border-slate-700 shadow-xl">
        <button 
          onClick={() => setStrokes([])}
          className="p-2 bg-slate-700 hover:bg-slate-600 rounded-lg text-slate-300 transition-colors"
          title="Clear Board"
        >
          <FiTrash2 size={20} />
        </button>
        <button 
          onClick={() => setOffset({x:0, y:0})}
          className="p-2 bg-slate-700 hover:bg-slate-600 rounded-lg text-slate-300 transition-colors"
          title="Reset View"
        >
          <FiMaximize size={20} />
        </button>
      </div>

      <div className="absolute bottom-4 left-4 z-10 bg-slate-800 px-4 py-2 rounded-xl border border-slate-700 shadow-xl text-slate-400 text-sm">
        Use your mobile device to draw, pan, and zoom.
      </div>

      <div ref={containerRef} className="flex-1 w-full h-full cursor-crosshair">
        <canvas ref={canvasRef} className="block w-full h-full" />
      </div>
    </div>
  );
}
