import { describe, expect, it } from 'vitest'

import { TEAM_STATUS, monthLabel, monthOf, monthRange, shiftMonth } from '@/lib/team'

describe('helper rekan kerja', () => {
  it('rentang bulan lalu penuh, bulan berjalan dipotong di hari ini', () => {
    expect(monthRange('2026-09', '2026-10-14')).toEqual({ from: '2026-09-01', to: '2026-09-30' })
    expect(monthRange('2026-10', '2026-10-14')).toEqual({ from: '2026-10-01', to: '2026-10-14' })
    expect(monthRange('2024-02', '2026-10-14')).toEqual({ from: '2024-02-01', to: '2024-02-29' })
  })

  it('geser bulan melewati batas tahun', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(monthOf('2026-10-14')).toBe('2026-10')
  })

  it('label bulan dalam Bahasa Indonesia', () => {
    expect(monthLabel('2026-10')).toBe('Oktober 2026')
  })

  it('izin hanya berlabel "Izin" tanpa jenis', () => {
    expect(TEAM_STATUS.IZIN.label).toBe('Izin')
    expect(TEAM_STATUS.HADIR.tone).toBe('success')
  })
})
