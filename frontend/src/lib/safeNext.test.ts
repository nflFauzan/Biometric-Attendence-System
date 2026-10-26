import { describe, expect, it } from 'vitest'

import { safeNextPath, withNext } from '@/lib/safeNext'

describe('safeNextPath', () => {
  it('menerima path relatif beserta query dan hash', () => {
    expect(safeNextPath('/izin/123')).toBe('/izin/123')
    expect(safeNextPath('/izin?status=PERLU_TINJAU_HR&x=1#a')).toBe('/izin?status=PERLU_TINJAU_HR&x=1#a')
  })

  it.each([
    ['//evil.com'],
    ['https://x'],
    ['http://x/izin'],
    ['javascript:alert(1)'],
    ['/\\evil.com'],
    ['/a\nb'],
    ['izin/1'],
    ['/login'],
    ['/login?next=/izin/1'],
    ['/ganti-password'],
    ['/Login'],
    ['/login/'],
    ['/GANTI-PASSWORD/?x=1'],
    [''],
  ])('menolak %j', (raw) => {
    expect(safeNextPath(raw)).toBeNull()
  })

  it('menolak null dan undefined', () => {
    expect(safeNextPath(null)).toBeNull()
    expect(safeNextPath(undefined)).toBeNull()
  })
})

describe('withNext', () => {
  it('menambahkan next ter-encode berikut query', () => {
    expect(withNext('/login', { pathname: '/izin/123', search: '?a=1', hash: '' })).toBe(
      '/login?next=%2Fizin%2F123%3Fa%3D1',
    )
  })

  it('tidak menambahkan next untuk beranda', () => {
    expect(withNext('/login', { pathname: '/', search: '', hash: '' })).toBe('/login')
  })
})
