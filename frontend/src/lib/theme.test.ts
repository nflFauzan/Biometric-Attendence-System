import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { initTheme, nextTheme, readTheme, setTheme, THEME_KEY } from '@/lib/theme'

/** matchMedia palsu yang bisa diubah, untuk meniru pengaturan terang/gelap di perangkat. */
function fakeSystemDark(initial: boolean) {
  let dark = initial
  const listeners = new Set<() => void>()
  vi.stubGlobal('matchMedia', (query: string) => ({
    get matches() {
      return query === '(prefers-color-scheme: dark)' && dark
    },
    media: query,
    addEventListener: (_: string, fn: () => void) => listeners.add(fn),
    removeEventListener: (_: string, fn: () => void) => listeners.delete(fn),
  }))
  return (value: boolean) => {
    dark = value
    listeners.forEach((fn) => fn())
  }
}

const isDark = () => document.documentElement.classList.contains('dark')

describe('tema terang/gelap', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })
  afterEach(() => vi.unstubAllGlobals())

  it('default ikut perangkat', () => {
    fakeSystemDark(true)
    expect(readTheme()).toBe('system')
    initTheme()
    expect(isDark()).toBe(true)
  })

  it('pilihan disimpan dan dipakai lagi', () => {
    fakeSystemDark(true)
    setTheme('light')
    expect(localStorage.getItem(THEME_KEY)).toBe('light')
    expect(isDark()).toBe(false)
    document.documentElement.classList.add('dark')
    initTheme()
    expect(isDark()).toBe(false)
  })

  it('nilai tersimpan yang rusak dianggap ikut perangkat', () => {
    fakeSystemDark(false)
    localStorage.setItem(THEME_KEY, 'ungu')
    expect(readTheme()).toBe('system')
  })

  it('mode ikut perangkat mengikuti perubahan pengaturan; pilihan tetap tidak', () => {
    const setSystemDark = fakeSystemDark(false)
    initTheme()
    expect(isDark()).toBe(false)
    setSystemDark(true)
    expect(isDark()).toBe(true)

    setTheme('light')
    setSystemDark(false)
    setSystemDark(true)
    expect(isDark()).toBe(false)
  })

  it('urutan tombol: terang → gelap → ikut perangkat', () => {
    expect(nextTheme('light')).toBe('dark')
    expect(nextTheme('dark')).toBe('system')
    expect(nextTheme('system')).toBe('light')
  })
})
