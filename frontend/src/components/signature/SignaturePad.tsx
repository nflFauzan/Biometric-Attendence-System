import { Eraser, Save } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type PointerEvent } from 'react'

import { Button } from '@/components/ui/button'

const HEIGHT = 200
const FALLBACK_WIDTH = 600
const LINE_WIDTH = 2.5
const INK = '#0b1f3a'

interface Point {
  x: number
  y: number
}

interface Props {
  onSave: (blob: Blob) => void
  saving?: boolean
}

/**
 * Kanvas tanda tangan: digambar dengan jari atau mouse, disimpan sebagai PNG. Bitmap berukuran piksel CSS x
 * devicePixelRatio supaya tajam di layar HP; semua koordinat tetap dalam piksel CSS. Coretan disimpan sebagai
 * titik sehingga bisa digambar ulang saat ukuran berubah (rotasi layar, ubah ukuran jendela).
 */
export function SignaturePad({ onSave, saving = false }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const strokes = useRef<Point[][]>([])
  const drawing = useRef(false)
  const cssWidth = useRef(0)
  const [hasInk, setHasInk] = useState(false)

  const context = useCallback(() => {
    const ctx = canvasRef.current?.getContext('2d')
    if (ctx) {
      ctx.lineWidth = LINE_WIDTH
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.strokeStyle = INK
      ctx.fillStyle = INK
    }
    return ctx ?? null
  }, [])

  const dot = (ctx: CanvasRenderingContext2D, p: Point) => {
    ctx.beginPath()
    ctx.arc(p.x, p.y, LINE_WIDTH / 2, 0, Math.PI * 2)
    ctx.fill()
  }

  const redraw = useCallback(() => {
    const ctx = context()
    if (!ctx) return
    for (const stroke of strokes.current) {
      if (stroke.length === 1) {
        dot(ctx, stroke[0])
        continue
      }
      ctx.beginPath()
      ctx.moveTo(stroke[0].x, stroke[0].y)
      for (const p of stroke.slice(1)) ctx.lineTo(p.x, p.y)
      ctx.stroke()
    }
  }, [context])

  const resize = useCallback(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const width = canvas.clientWidth || FALLBACK_WIDTH
    const dpr = window.devicePixelRatio || 1
    if (width === cssWidth.current && canvas.width === Math.round(width * dpr)) return
    cssWidth.current = width
    // Mengubah ukuran bitmap mengosongkan kanvas dan mereset transformasi, jadi gambar ulang dari titik tersimpan.
    canvas.width = Math.round(width * dpr)
    canvas.height = Math.round(HEIGHT * dpr)
    canvas.getContext('2d')?.setTransform(dpr, 0, 0, dpr, 0, 0)
    redraw()
  }, [redraw])

  useEffect(() => {
    resize()
    window.addEventListener('resize', resize)
    window.addEventListener('orientationchange', resize)
    return () => {
      window.removeEventListener('resize', resize)
      window.removeEventListener('orientationchange', resize)
    }
  }, [resize])

  const point = (event: PointerEvent<HTMLCanvasElement>): Point => {
    const rect = event.currentTarget.getBoundingClientRect()
    return { x: event.clientX - rect.left, y: event.clientY - rect.top }
  }

  const start = (event: PointerEvent<HTMLCanvasElement>) => {
    const ctx = context()
    if (!ctx) return
    drawing.current = true
    event.currentTarget.setPointerCapture?.(event.pointerId)
    const p = point(event)
    strokes.current.push([p])
    // Satu ketukan tanpa gerakan tetap meninggalkan titik dan dihitung sebagai tinta.
    dot(ctx, p)
    ctx.beginPath()
    ctx.moveTo(p.x, p.y)
    setHasInk(true)
  }

  const move = (event: PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return
    const ctx = context()
    if (!ctx) return
    const p = point(event)
    strokes.current[strokes.current.length - 1].push(p)
    ctx.lineTo(p.x, p.y)
    ctx.stroke()
  }

  const end = (event: PointerEvent<HTMLCanvasElement>) => {
    if (!drawing.current) return
    drawing.current = false
    event.currentTarget.releasePointerCapture?.(event.pointerId)
  }

  const clear = () => {
    // Transformasi sudah diskalakan dpr, jadi area hapus dinyatakan dalam piksel CSS.
    canvasRef.current?.getContext('2d')?.clearRect(0, 0, cssWidth.current, HEIGHT)
    strokes.current = []
    setHasInk(false)
  }

  const save = () => canvasRef.current?.toBlob((blob) => blob && onSave(blob), 'image/png')

  return (
    <div className="flex flex-col gap-3">
      <canvas
        ref={canvasRef}
        aria-label="Area tanda tangan"
        role="img"
        className="w-full rounded-lg border-2 border-dashed border-steel-300 bg-white"
        style={{ touchAction: 'none', height: `${HEIGHT}px` }}
        onPointerDown={start}
        onPointerMove={move}
        onPointerUp={end}
        onPointerCancel={end}
      />
      <p className="text-xs text-muted-foreground">Tanda tangani di dalam kotak di atas.</p>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" onClick={clear} disabled={saving}>
          <Eraser />
          Hapus coretan
        </Button>
        <Button type="button" onClick={save} disabled={!hasInk || saving}>
          <Save />
          {saving ? 'Menyimpan...' : 'Simpan'}
        </Button>
      </div>
    </div>
  )
}
