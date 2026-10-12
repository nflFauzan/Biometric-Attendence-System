import { describe, expect, it } from 'vitest'

import {
  formatDate,
  formatDuration,
  formatRupiah,
  formatTime,
  fromDateTimeLocal,
  initials,
  toDateTimeLocal,
} from '@/lib/format'

describe('formatDuration', () => {
  it('menampilkan jam dan menit tanpa pembulatan', () => {
    expect(formatDuration(7200)).toBe('2 jam')
    expect(formatDuration(3660)).toBe('1 jam 1 menit')
    expect(formatDuration(45)).toBe('45 detik')
    expect(formatDuration(0)).toBe('0 menit')
    expect(formatDuration(null)).toBe('—')
  })
})

describe('formatTime', () => {
  it('memakai titik sebagai pemisah jam', () => {
    expect(formatTime('08:00')).toBe('08.00')
    expect(formatTime('08:00:00')).toBe('08.00')
    expect(formatTime('17:00:45')).toBe('17.00.45')
    expect(formatTime(null)).toBe('—')
  })
})

describe('formatRupiah', () => {
  it('memformat nominal rupiah', () => {
    expect(formatRupiah(80000)).toBe('Rp80.000')
    expect(formatRupiah(12500.5)).toBe('Rp12.500,5')
    expect(formatRupiah(null)).toBe('—')
  })
})

describe('formatDate', () => {
  it('menampilkan tanggal dengan bulan singkat berbahasa Indonesia', () => {
    expect(formatDate('2026-10-09')).toBe('9 Okt 2026')
    expect(formatDate('2026-05-31')).toBe('31 Mei 2026')
  })

  it('menampilkan tanda pisah untuk nilai kosong', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate('')).toBe('—')
  })
})

describe('initials', () => {
  it('mengambil huruf pertama dari dua kata pertama', () => {
    expect(initials('Budi Santoso')).toBe('BS')
    expect(initials('siti nur aisyah')).toBe('SN')
    expect(initials('Owner')).toBe('O')
    expect(initials(null)).toBe('')
  })
})

describe('toDateTimeLocal', () => {
  it('menyertakan detik agar batas "Terima scan sejak" tidak terpotong', () => {
    const iso = new Date(2026, 9, 12, 15, 51, 51).toISOString()
    expect(toDateTimeLocal(iso)).toBe('2026-10-12T15:51:51')
    expect(fromDateTimeLocal(toDateTimeLocal(iso))).toBe(iso)
    expect(toDateTimeLocal(null)).toBe('')
  })
})
