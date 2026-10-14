import { describe, expect, it } from 'vitest'

import css from '@/index.css?raw'

/**
 * Kontras teks di atas kaca (docs/rencana-tema-kaca.md §5): setiap tingkat kaca dicampur dengan latar
 * terterang dan tergelap di tiap mode, lalu teks harus minimal 4.5:1. Nilai dibaca langsung dari index.css.
 */
type Rgba = [number, number, number, number]

function block(selector: ':root' | '.dark'): Record<string, string> {
  // Hanya blok di tingkat atas (baris diawali selektor), bukan versi di dalam @supports/@media.
  const pattern = new RegExp(`^${selector.replace('.', '\\.')} \\{([\\s\\S]*?)^\\}`, 'm')
  const body = css.match(pattern)?.[1] ?? ''
  return Object.fromEntries([...body.matchAll(/(--[\w-]+):\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]))
}

function parse(value: string): Rgba {
  const hex = value.match(/^#([0-9a-f]{6})$/i)
  if (hex) {
    const n = parseInt(hex[1], 16)
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255, 1]
  }
  const rgb = value.match(/^rgb\((\d+) (\d+) (\d+)(?: \/ ([\d.]+))?\)$/)
  if (!rgb) {
    throw new Error(`Warna tidak dikenali: ${value}`)
  }
  return [Number(rgb[1]), Number(rgb[2]), Number(rgb[3]), rgb[4] === undefined ? 1 : Number(rgb[4])]
}

/** Warna `top` (boleh transparan) di atas `bottom` yang solid. */
function over(top: Rgba, bottom: Rgba): Rgba {
  const a = top[3]
  return [0, 1, 2].map((i) => top[i] * a + bottom[i] * (1 - a)).concat(1) as Rgba
}

function luminance([r, g, b]: Rgba): number {
  const channel = (c: number) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  }
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
}

function contrast(a: Rgba, b: Rgba): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

/** Latar terterang dan tergelap: dasar polos, tiap cahaya, dan cahaya yang bertumpuk. */
function backdrops(v: Record<string, string>): Rgba[] {
  const base = parse(v['--page-base'])
  const [g1, g2, g3] = ['--page-glow-1', '--page-glow-2', '--page-glow-3'].map((k) => parse(v[k]))
  return [base, over(g1, base), over(g2, base), over(g3, base), over(g2, over(g1, base)), over(g3, over(g2, base))]
}

// Warna teks sidebar mengikuti kelas di AppLayout: menu dan judul grup steel-200 (steel-300/400 tidak
// mencapai 4.5:1 di atas kaca sidebar terang), menu aktif putih.
const SIDEBAR_TEXT = { menu: '#C3D2EA', aktif: '#FFFFFF' }

describe.each([':root', '.dark'] as const)('kontras kaca %s', (selector) => {
  const v = block(selector)

  it.each(['--glass-bg', '--glass-strong-bg', '--glass-bar-bg'])('teks di atas %s minimal 4.5:1', (surface) => {
    for (const backdrop of backdrops(v)) {
      const bg = over(parse(v[surface]), backdrop)
      for (const text of ['--foreground', '--muted-foreground']) {
        expect(contrast(parse(v[text]), bg), `${text} di atas ${surface}`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('teks sidebar di atas kaca sidebar minimal 4.5:1', () => {
    for (const backdrop of backdrops(v)) {
      const bg = over(parse(v['--glass-sidebar-bg']), backdrop)
      for (const [name, color] of Object.entries(SIDEBAR_TEXT)) {
        expect(contrast(parse(color), bg), `teks sidebar ${name}`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })
})
