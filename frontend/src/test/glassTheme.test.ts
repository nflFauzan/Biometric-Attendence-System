import { beforeEach, describe, expect, it } from 'vitest'
import {
  loadGlassSettings,
  saveGlassSettings,
  loadWallpaper,
  saveWallpaper,
  WALLPAPER_PRESETS,
} from '../lib/glassTheme'

describe('Glass Theme State Manager', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('mengembalikan nilai bawaan jika localStorage kosong', () => {
    const s = loadGlassSettings()
    expect(s.blur).toBeGreaterThan(0)
    expect(s.opacity).toBeGreaterThan(0)
    expect(loadWallpaper()).toBe(WALLPAPER_PRESETS[0].url)
  })

  it('menormalkan nilai yang korup di localStorage ke rentang aman', () => {
    localStorage.setItem('takka_glass_settings', JSON.stringify({ blur: 999, opacity: -5 }))
    const s = loadGlassSettings()
    expect(s.blur).toBeLessThanOrEqual(40)
    expect(s.opacity).toBeGreaterThanOrEqual(0.08)
  })

  it('menyimpan dan memuat preferensi wallpaper', () => {
    saveGlassSettings({ blur: 15, opacity: 0.25, saturation: 1.2, tint: '#ffffff', edge: 0.7 })
    expect(loadGlassSettings().blur).toBe(15)
    saveWallpaper('https://example.com/custom.jpg')
    expect(loadWallpaper()).toBe('https://example.com/custom.jpg')
  })
})
