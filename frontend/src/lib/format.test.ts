import { describe, expect, it } from 'vitest'

import { formatDate, initials } from '@/lib/format'

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
