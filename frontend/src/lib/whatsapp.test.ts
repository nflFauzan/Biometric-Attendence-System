import { describe, expect, it } from 'vitest'

import { sampleRequest } from '@/components/leave/fixtures'
import { buildWhatsAppUrl } from '@/lib/whatsapp'

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
