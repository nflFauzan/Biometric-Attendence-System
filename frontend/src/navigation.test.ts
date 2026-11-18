import { describe, expect, it } from 'vitest'

import type { Role } from '@/lib/types'
import { homePathFor, menuFor, settingsPageFor, SETTINGS_PAGES } from '@/navigation'

const labels = (role: Parameters<typeof menuFor>[0]) => menuFor(role).map((item) => item.label)

describe('menu per role', () => {
  it('Owner dan Finance & Accounting punya 6 menu utama yang sama, berurutan (K-145)', () => {
    for (const role of ['OWNER', 'HR_FINANCE'] as const) {
      const sidebar = menuFor(role).filter((item) => !item.mobileOnly)
      expect(sidebar.map((item) => item.label)).toEqual(['Beranda', 'Absensi', 'Izin & Cuti', 'Gaji & Laporan', 'Karyawan', 'Pengaturan'])
    }
    expect(labels('EMPLOYEE')).not.toContain('Gaji & Laporan')
    expect(labels('EMPLOYEE')).not.toContain('Pengaturan')
  })

  it('kartu Pengaturan: Jenis Izin/Cuti dan Audit Log hanya Owner, Aturan Kerja hanya lihat untuk Finance', () => {
    const forRole = (role: Role) => SETTINGS_PAGES.filter((page) => page.roles.includes(role)).map((page) => page.slug)
    expect(forRole('OWNER')).toHaveLength(9)
    expect(forRole('HR_FINANCE')).not.toContain('jenis-izin')
    expect(forRole('HR_FINANCE')).not.toContain('audit-log')
    expect(forRole('HR_FINANCE')).toContain('akun')
    expect(SETTINGS_PAGES.find((page) => page.slug === 'aturan-kerja')?.readOnlyFor).toEqual(['HR_FINANCE'])
    expect(settingsPageFor('/pengaturan/mesin')?.label).toBe('Mesin Absensi')
    expect(settingsPageFor('/pengaturan')).toBeUndefined()
  })

  it('Owner dan HR punya Profil untuk tanda tangan (K-115), lewat menu Lainnya di HP', () => {
    for (const role of ['OWNER', 'HR_FINANCE'] as const) {
      const profil = menuFor(role).filter((item) => item.path === '/profil')
      expect(profil).toHaveLength(1)
      expect(profil[0].mobile).toBeFalsy()
    }
  })

  it('Owner dan Finance & Accounting punya tepat 4 menu di bottom nav HP', () => {
    for (const role of ['OWNER', 'HR_FINANCE'] as const) {
      const mobileItems = menuFor(role).filter((item) => item.mobile)
      expect(mobileItems.map((item) => item.path)).toEqual(['/', '/absensi', '/izin', '/gaji'])
      expect(mobileItems.map((item) => item.label)).toEqual(['Beranda', 'Absensi', 'Izin & Cuti', 'Gaji & Laporan'])
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
