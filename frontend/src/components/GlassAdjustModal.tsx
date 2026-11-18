import { Check, Image as ImageIcon, Layers, RotateCcw, Settings2, X } from 'lucide-react'
import { useState } from 'react'

import {
  DEFAULT_GLASS,
  loadGlassSettings,
  loadWallpaper,
  normalizeGlass,
  saveGlassSettings,
  saveWallpaper,
  WALLPAPER_PRESETS,
  type GlassSettings,
} from '@/lib/glassTheme'

interface GlassAdjustModalProps {
  open: boolean
  onClose: () => void
}

export function GlassAdjustModal({ open, onClose }: GlassAdjustModalProps) {
  const [settings, setSettings] = useState<GlassSettings>(() => loadGlassSettings())
  const [wallpaper, setWallpaper] = useState<string>(() => loadWallpaper())
  const [customUrl, setCustomUrl] = useState('')

  if (!open) return null

  const handleUpdate = (patch: Partial<GlassSettings>) => {
    const next = normalizeGlass({ ...settings, ...patch })
    setSettings(next)
    saveGlassSettings(next)
  }

  const handleSelectWallpaper = (url: string) => {
    setWallpaper(url)
    saveWallpaper(url)
  }

  const handleCustomWallpaper = (e: React.FormEvent) => {
    e.preventDefault()
    if (customUrl.trim()) {
      handleSelectWallpaper(customUrl.trim())
      setCustomUrl('')
    }
  }

  const presets = [
    { name: 'Clear', value: { blur: 8, opacity: 0.15, saturation: 1.1, edge: 0.8 } },
    { name: 'Frosted', value: DEFAULT_GLASS },
    { name: 'Solid', value: { blur: 32, opacity: 0.86, saturation: 1.0, edge: 0.35 } },
  ]

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Sesuaikan Efek Kaca"
      className="fixed top-20 right-4 z-50 flex max-h-[calc(100dvh-6rem)] w-[320px] flex-col overflow-y-auto rounded-3xl border border-white/20 bg-card/85 p-5 text-foreground shadow-2xl backdrop-blur-2xl sm:right-6 md:w-[350px]"
    >
      <div className="flex items-center justify-between pb-3 border-b border-white/10">
        <div className="flex items-center gap-2">
          <span className="grid size-7 place-items-center rounded-lg bg-primary/15 text-primary">
            <Settings2 className="size-4" />
          </span>
          <h2 className="font-heading text-sm font-bold">Pengaturan Kaca & Latar</h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="grid size-7 place-items-center rounded-full hover:bg-accent/60"
          aria-label="Tutup"
        >
          <X className="size-4" />
        </button>
      </div>

      <div className="mt-4 flex flex-col gap-5 text-xs">
        {/* Preset Kaca */}
        <div>
          <label className="font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">
            Preset Kaca
          </label>
          <div className="mt-1.5 grid grid-cols-3 gap-2">
            {presets.map((p) => (
              <button
                key={p.name}
                type="button"
                onClick={() => handleUpdate(p.value)}
                className="h-8 rounded-xl border border-white/15 bg-white/10 font-medium transition-all hover:bg-white/20 active:scale-95"
              >
                {p.name}
              </button>
            ))}
          </div>
        </div>

        {/* Sliders Kaca */}
        <div className="flex flex-col gap-3 rounded-2xl border border-white/10 bg-white/5 p-3.5">
          <label className="flex flex-col gap-1">
            <span className="flex justify-between font-medium">
              <span>Blur Kaca</span>
              <span className="text-muted-foreground font-mono">{Math.round(settings.blur)}px</span>
            </span>
            <input
              type="range"
              min="0"
              max="40"
              step="1"
              value={settings.blur}
              onChange={(e) => handleUpdate({ blur: Number(e.target.value) })}
              className="accent-primary cursor-pointer"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="flex justify-between font-medium">
              <span>Opasitas</span>
              <span className="text-muted-foreground font-mono">{Math.round(settings.opacity * 100)}%</span>
            </span>
            <input
              type="range"
              min="0.08"
              max="0.9"
              step="0.01"
              value={settings.opacity}
              onChange={(e) => handleUpdate({ opacity: Number(e.target.value) })}
              className="accent-primary cursor-pointer"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="flex justify-between font-medium">
              <span>Saturasi</span>
              <span className="text-muted-foreground font-mono">{Math.round(settings.saturation * 100)}%</span>
            </span>
            <input
              type="range"
              min="0.5"
              max="2.0"
              step="0.05"
              value={settings.saturation}
              onChange={(e) => handleUpdate({ saturation: Number(e.target.value) })}
              className="accent-primary cursor-pointer"
            />
          </label>

          <label className="flex flex-col gap-1">
            <span className="flex justify-between font-medium">
              <span>Kilau Tepi (Edge Light)</span>
              <span className="text-muted-foreground font-mono">{Math.round(settings.edge * 100)}%</span>
            </span>
            <input
              type="range"
              min="0"
              max="1.0"
              step="0.05"
              value={settings.edge}
              onChange={(e) => handleUpdate({ edge: Number(e.target.value) })}
              className="accent-primary cursor-pointer"
            />
          </label>
        </div>

        {/* Wallpaper Picker */}
        <div>
          <label className="flex items-center gap-1.5 font-semibold text-muted-foreground uppercase tracking-wider text-[10px]">
            <ImageIcon className="size-3" />
            <span>Pilihan Latar Belakang (Produksi)</span>
          </label>
          <div className="mt-2 grid grid-cols-2 gap-2">
            {WALLPAPER_PRESETS.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => handleSelectWallpaper(item.url)}
                className={`relative flex h-16 flex-col justify-end overflow-hidden rounded-xl border text-left p-1.5 transition-all ${
                  wallpaper === item.url
                    ? 'border-primary ring-2 ring-primary/40'
                    : 'border-white/10 hover:border-white/30'
                }`}
              >
                <img src={item.thumbnail} alt="" className="absolute inset-0 size-full object-cover" />
                <span className="relative z-10 block truncate text-[9.5px] font-bold text-white drop-shadow-md">
                  {item.name}
                </span>
                {wallpaper === item.url && (
                  <span className="absolute top-1 right-1 z-10 grid size-4 place-items-center rounded-full bg-primary text-white">
                    <Check className="size-2.5" />
                  </span>
                )}
              </button>
            ))}
          </div>

          <form onSubmit={handleCustomWallpaper} className="mt-2.5 flex gap-1.5">
            <input
              type="url"
              placeholder="Atau tempel URL gambar kustom..."
              value={customUrl}
              onChange={(e) => setCustomUrl(e.target.value)}
              className="h-8 flex-1 rounded-lg border border-white/15 bg-white/10 px-2 text-[11px] placeholder:text-muted-foreground focus:outline-primary"
            />
            <button
              type="submit"
              className="h-8 rounded-lg bg-primary px-3 text-[11px] font-semibold text-white hover:brightness-110"
            >
              Pasang
            </button>
          </form>
        </div>

        {/* Reset Button */}
        <button
          type="button"
          onClick={() => handleUpdate(DEFAULT_GLASS)}
          className="mt-1 flex h-8 items-center justify-center gap-1.5 rounded-xl border border-white/15 text-[11px] font-medium text-muted-foreground hover:bg-accent/50 hover:text-foreground"
        >
          <RotateCcw className="size-3" />
          <span>Kembalikan Setelan Kaca Bawaan</span>
        </button>
      </div>
    </div>
  )
}
