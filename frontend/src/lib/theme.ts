import { useSyncExternalStore } from 'react'

/**
 * Tema terang/gelap (docs/rencana-tema-kaca.md §4, K-085). Kelas `dark` dipasang di <html>; pemasangan pertama
 * dilakukan public/tema-awal.js sebelum halaman tampil agar tidak berkedip. Kunci penyimpanan harus sama.
 */
export const THEME_KEY = 'tema'

export type ThemeChoice = 'light' | 'dark' | 'system'

export const THEME_LABELS: Record<ThemeChoice, string> = {
  light: 'Terang',
  dark: 'Gelap',
  system: 'Ikut perangkat',
}

const DARK_QUERY = '(prefers-color-scheme: dark)'
const listeners = new Set<() => void>()

function systemDark(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia(DARK_QUERY).matches
}

export function readTheme(): ThemeChoice {
  try {
    const stored = localStorage.getItem(THEME_KEY)
    return stored === 'light' || stored === 'dark' || stored === 'system' ? stored : 'system'
  } catch {
    // localStorage bisa diblokir (mode privat tertentu); pakai pengaturan perangkat.
    return 'system'
  }
}

function apply(choice: ThemeChoice) {
  const dark = choice === 'dark' || (choice === 'system' && systemDark())
  document.documentElement.classList.toggle('dark', dark)
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light'
}

export function setTheme(choice: ThemeChoice) {
  try {
    localStorage.setItem(THEME_KEY, choice)
  } catch {
    // Tetap diterapkan untuk sesi ini walau tidak bisa disimpan.
  }
  apply(choice)
  listeners.forEach((fn) => fn())
}

export function nextTheme(choice: ThemeChoice): ThemeChoice {
  return choice === 'light' ? 'dark' : choice === 'dark' ? 'system' : 'light'
}

/** Dipanggil sekali saat aplikasi mulai: terapkan pilihan dan ikuti perubahan pengaturan perangkat. */
export function initTheme() {
  apply(readTheme())
  if (typeof window.matchMedia === 'function') {
    window.matchMedia(DARK_QUERY).addEventListener('change', () => {
      if (readTheme() === 'system') {
        apply('system')
      }
    })
  }
}

function subscribe(fn: () => void) {
  listeners.add(fn)
  return () => {
    listeners.delete(fn)
  }
}

export function useTheme(): [ThemeChoice, (choice: ThemeChoice) => void] {
  return [useSyncExternalStore(subscribe, readTheme), setTheme]
}
