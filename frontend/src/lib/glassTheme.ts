export interface GlassSettings {
  blur: number
  opacity: number
  saturation: number
  edge: number
  tint: string
}

export interface WallpaperOption {
  id: string
  name: string
  url: string
  thumbnail: string
}

export const DEFAULT_GLASS: GlassSettings = {
  blur: 22,
  opacity: 0.3,
  saturation: 1.25,
  edge: 0.6,
  tint: '#ffffff',
}

export const WALLPAPER_PRESETS: WallpaperOption[] = [
  {
    id: 'cozy-studio',
    name: 'Cozy Studio 21st.dev',
    url: 'https://cdn.21st.dev/assets/mirror/4a/4a0ed73b8ab76bd427aa70ab124329127f3df27cc7f31223a832ed7d28b3dda4.jpg',
    thumbnail: 'https://cdn.21st.dev/assets/mirror/4a/4a0ed73b8ab76bd427aa70ab124329127f3df27cc7f31223a832ed7d28b3dda4.jpg',
  },
  {
    id: 'steel-arch',
    name: 'Steel Architecture',
    url: 'https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=1600&q=80',
    thumbnail: 'https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=300&q=80',
  },
  {
    id: 'executive-dark',
    name: 'Executive Dark Loft',
    url: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=1600&q=80',
    thumbnail: 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?auto=format&fit=crop&w=300&q=80',
  },
  {
    id: 'modern-workspace',
    name: 'Modern Workspace',
    url: 'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1600&q=80',
    thumbnail: 'https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=300&q=80',
  },
]

const SETTINGS_KEY = 'takka_glass_settings'
const WALLPAPER_KEY = 'takka_theme_bg'

function bounded(val: unknown, min: number, max: number, fallback: number): number {
  const num = typeof val === 'number' ? val : parseFloat(String(val))
  return Number.isFinite(num) ? Math.min(max, Math.max(min, num)) : fallback
}

export function normalizeGlass(input: Partial<GlassSettings>): GlassSettings {
  return {
    blur: bounded(input.blur, 0, 40, DEFAULT_GLASS.blur),
    opacity: bounded(input.opacity, 0.08, 0.9, DEFAULT_GLASS.opacity),
    saturation: bounded(input.saturation, 0.5, 2.0, DEFAULT_GLASS.saturation),
    edge: bounded(input.edge, 0, 1.0, DEFAULT_GLASS.edge),
    tint: /^#[0-9a-f]{6}$/i.test(input.tint ?? '') ? input.tint! : DEFAULT_GLASS.tint,
  }
}

export function loadGlassSettings(): GlassSettings {
  if (typeof window === 'undefined') return DEFAULT_GLASS
  try {
    const raw = localStorage.getItem(SETTINGS_KEY)
    if (!raw) return DEFAULT_GLASS
    const parsed = JSON.parse(raw) as Partial<GlassSettings>
    return normalizeGlass(parsed)
  } catch {
    return DEFAULT_GLASS
  }
}

export function saveGlassSettings(settings: GlassSettings): void {
  if (typeof window === 'undefined') return
  try {
    const normalized = normalizeGlass(settings)
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(normalized))
    applyGlassToDOM(normalized)
  } catch {
    // Abaikan jika localStorage tidak bisa diakses
  }
}

export function loadWallpaper(): string {
  if (typeof window === 'undefined') return WALLPAPER_PRESETS[0].url
  try {
    return localStorage.getItem(WALLPAPER_KEY) || WALLPAPER_PRESETS[0].url
  } catch {
    return WALLPAPER_PRESETS[0].url
  }
}

export function saveWallpaper(url: string): void {
  if (typeof window === 'undefined') return
  try {
    localStorage.setItem(WALLPAPER_KEY, url)
    window.dispatchEvent(new CustomEvent('takka-wallpaper-changed', { detail: url }))
  } catch {
    // Abaikan
  }
}

export function applyGlassToDOM(settings: GlassSettings): void {
  if (typeof document === 'undefined') return
  const root = document.documentElement
  root.style.setProperty('--gw-blur', `${settings.blur}px`)
  root.style.setProperty('--gw-opacity', `${settings.opacity}`)
  root.style.setProperty('--gw-saturation', `${settings.saturation}`)
  root.style.setProperty('--gw-edge', `${settings.edge}`)
  root.style.setProperty('--gw-tint', settings.tint)
}
