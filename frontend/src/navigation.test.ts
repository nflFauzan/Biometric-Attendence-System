import { describe, expect, it } from 'vitest'

import { homePathFor, menuFor } from '@/navigation'

const labels = (role: Parameters<typeof menuFor>[0]) => menuFor(role).map((item) => item.label)

describe('menu per role', () => {
  it('Owner melihat master data, akun, dan audit log', () => {
    expect(labels('OWNER')).toEqual(
      expect.arrayContaining(['Dashboard', 'Karyawan', 'Cabang', 'Mesin Absensi', 'Akun', 'Audit Log']),
    )
    expect(labels('OWNER')).not.toContain('Profil Saya')
    expect(labels('OWNER')).not.toContain('Rekan Kerja')
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

  it('Karyawan melihat rekan kerja, profil, dan pengajuan izinnya sendiri', () => {
    expect(labels('EMPLOYEE')).toEqual(['Rekan Kerja', 'Profil Saya', 'Izin & Cuti Saya'])
  })

  it('halaman awal karyawan adalah profil, role lain dashboard', () => {
    expect(homePathFor('EMPLOYEE')).toBe('/profil')
    expect(homePathFor('OWNER')).toBe('/')
    expect(homePathFor('HR_FINANCE')).toBe('/')
  })
})
