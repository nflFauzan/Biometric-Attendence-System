import { describe, expect, it } from 'vitest'

import { homePathFor, menuFor } from '@/navigation'

const labels = (role: Parameters<typeof menuFor>[0]) => menuFor(role).map((item) => item.label)

describe('menu per role', () => {
  it('Owner melihat master data, akun, dan audit log', () => {
    expect(labels('OWNER')).toEqual(
      expect.arrayContaining(['Dashboard', 'Karyawan', 'Cabang', 'Mesin Absensi', 'Akun', 'Audit Log']),
    )
    expect(labels('OWNER')).not.toContain('Rekan')
    expect(labels('OWNER')).toEqual(expect.arrayContaining(['Izin & Cuti', 'Jenis Izin/Cuti']))
  })

  it('HR/Finance sama seperti Owner kecuali audit log', () => {
    expect(labels('HR_FINANCE')).toContain('Karyawan')
    expect(labels('HR_FINANCE')).toContain('Akun')
    expect(labels('HR_FINANCE')).not.toContain('Audit Log')
    expect(labels('HR_FINANCE')).toContain('Izin & Cuti')
    // Jenis izin hanya diatur Owner (K-070).
    expect(labels('HR_FINANCE')).not.toContain('Jenis Izin/Cuti')
  })

  it('Owner dan HR punya Profil untuk tanda tangan (K-115), lewat menu Lainnya di HP', () => {
    for (const role of ['OWNER', 'HR_FINANCE'] as const) {
      const profil = menuFor(role).filter((item) => item.path === '/profil')
      expect(profil).toHaveLength(1)
      expect(profil[0].mobile).toBeFalsy()
    }
  })

  it('Karyawan punya tepat 4 menu berurutan, semuanya tampil di bottom nav', () => {
    const items = menuFor('EMPLOYEE')
    expect(items.map((item) => item.path)).toEqual(['/beranda', '/izin-saya', '/absensi-saya', '/profil'])
    expect(items.map((item) => item.label)).toEqual(['Beranda', 'Izin', 'Absensi', 'Profil'])
    expect(items.every((item) => item.mobile)).toBe(true)
  })

  it('halaman awal karyawan adalah beranda, role lain dashboard', () => {
    expect(homePathFor('EMPLOYEE')).toBe('/beranda')
    expect(homePathFor('OWNER')).toBe('/')
    expect(homePathFor('HR_FINANCE')).toBe('/')
  })
})
