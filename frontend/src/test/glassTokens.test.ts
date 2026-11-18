import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'

describe('Glassmorphism CSS Tokens (21st.dev)', () => {
  it('mendefinisikan variabel materi kaca 21st.dev', () => {
    expect(css).toContain('--gw-blur')
    expect(css).toContain('--gw-opacity')
    expect(css).toContain('--gw-saturation')
    expect(css).toContain('--gw-tint')
    expect(css).toContain('--gw-edge')
  })

  it('mencegah nested backdrop blur untuk performa GPU', () => {
    expect(css).toMatch(/:where\([^)]*\.gw-glass[^)]*\)\s*:is\([^)]*\.gw-glass[^)]*\)/)
  })

  it('memiliki fallback prefers-reduced-transparency', () => {
    expect(css).toContain('prefers-reduced-transparency')
  })
})
