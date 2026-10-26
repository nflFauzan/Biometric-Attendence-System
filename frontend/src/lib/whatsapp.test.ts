import { describe, expect, it } from 'vitest'

import { sampleRequest } from '@/components/leave/fixtures'
import { buildWhatsAppUrl, normalizeWhatsAppNumber, whatsAppSendUrl } from '@/lib/whatsapp'

describe('buildWhatsAppUrl', () => {
  it('menyusun tautan wa.me berisi nomor surat, nama, rentang, dan tautan tinjau', () => {
    const url = buildWhatsAppUrl(sampleRequest({ id: 123, employeeName: 'Budi Santoso' }), 'https://absen.contoh')
    expect(url.startsWith('https://wa.me/?text=')).toBe(true)
    const text = decodeURIComponent(url.slice('https://wa.me/?text='.length))
    expect(text).toContain('IZ-000123')
    expect(text).toContain('Budi Santoso')
    expect(text).toContain('https://absen.contoh/izin/123')
    expect(text).toMatch(/^Permohonan Cuti IZ-000123 a\.n\. Budi Santoso, /)
  })
})

describe('normalizeWhatsAppNumber', () => {
  it.each([
    ['0812-3456-7890', '6281234567890'],
    ['+62 812 3456 7890', '6281234567890'],
    ['6281234567890', '6281234567890'],
    ['81234567890', '6281234567890'],
  ])('%s → %s', (input, expected) => {
    expect(normalizeWhatsAppNumber(input)).toBe(expected)
  })

  it.each(['', null, undefined, '-'])('nomor kosong (%s) → null', (input) => {
    expect(normalizeWhatsAppNumber(input)).toBeNull()
  })
})

describe('whatsAppSendUrl', () => {
  it('menyertakan nomor yang sudah dirapikan', () => {
    expect(whatsAppSendUrl('a b', '0812')).toBe('https://wa.me/62812?text=a%20b')
  })

  it('tanpa nomor membiarkan pengguna memilih kontak', () => {
    expect(whatsAppSendUrl('a b', null)).toBe('https://wa.me/?text=a%20b')
  })
})
