import {
  Building2,
  Calculator,
  CalendarCheck,
  CalendarDays,
  FileSpreadsheet,
  FileText,
  House,
  ClipboardCheck,
  Settings,
  Fingerprint,
  SlidersHorizontal,
  LayoutDashboard,
  ScrollText,
  Tags,
  UserCog,
  UserRound,
  Users,
  type LucideIcon,
} from 'lucide-react'

import type { Role } from '@/lib/types'

export type MenuGroup = 'Utama' | 'Saya'

export interface MenuItem {
  path: string
  label: string
  roles: Role[]
  icon: LucideIcon
  group: MenuGroup
  /** Tampil langsung di bottom navigation HP; menu lain masuk ke "Lainnya". */
  mobile?: boolean
  /** Hanya di menu "Lainnya" HP, tidak di sidebar (di desktop sudah ada tombolnya di header). */
  mobileOnly?: boolean
}

const MANAGERS: Role[] = ['OWNER', 'HR_FINANCE']

/** Urutan kelompok di sidebar (docs/design-system.md §6.9). Kelompok "Utama" tampil tanpa judul. */
export const MENU_GROUPS: MenuGroup[] = ['Utama', 'Saya']

/**
 * Menu per role. Owner dan Finance memakai 6 menu yang disusun menurut pekerjaan (K-145); halaman pengaturan yang
 * jarang dipakai ada di balik menu Pengaturan. Backend tetap menjadi penentu akses yang sebenarnya.
 */
export const MENU: MenuItem[] = [
  { path: '/', label: 'Beranda', roles: MANAGERS, icon: LayoutDashboard, group: 'Utama', mobile: true },
  { path: '/absensi', label: 'Absensi', roles: MANAGERS, icon: ClipboardCheck, group: 'Utama', mobile: true },
  { path: '/izin', label: 'Izin & Cuti', roles: MANAGERS, icon: CalendarCheck, group: 'Utama', mobile: true },
  { path: '/gaji', label: 'Gaji & Laporan', roles: MANAGERS, icon: FileSpreadsheet, group: 'Utama', mobile: true },
  { path: '/karyawan', label: 'Karyawan', roles: MANAGERS, icon: Users, group: 'Utama' },
  { path: '/pengaturan', label: 'Pengaturan', roles: MANAGERS, icon: Settings, group: 'Utama' },
  { path: '/profil', label: 'Profil', roles: MANAGERS, icon: UserRound, group: 'Utama', mobileOnly: true },
  { path: '/beranda', label: 'Beranda', roles: ['EMPLOYEE'], icon: House, group: 'Saya', mobile: true },
  { path: '/izin-saya', label: 'Izin', roles: ['EMPLOYEE'], icon: CalendarCheck, group: 'Saya', mobile: true },
  { path: '/absensi-saya', label: 'Absensi', roles: ['EMPLOYEE'], icon: ClipboardCheck, group: 'Saya', mobile: true },
  { path: '/profil', label: 'Profil', roles: ['EMPLOYEE'], icon: UserRound, group: 'Saya', mobile: true },
]

export type SettingsGroup = 'Perusahaan' | 'Aturan & gaji' | 'Akses & keamanan'

export interface SettingsPage {
  slug: string
  label: string
  description: string
  group: SettingsGroup
  roles: Role[]
  icon: LucideIcon
  /** Role yang hanya bisa melihat, tidak mengubah (mis. upah hanya diubah Owner). */
  readOnlyFor?: Role[]
}

export const SETTINGS_GROUPS: SettingsGroup[] = ['Perusahaan', 'Aturan & gaji', 'Akses & keamanan']

/** Halaman di balik menu Pengaturan (K-145). Hak akses sama dengan saat masih menjadi menu sendiri. */
export const SETTINGS_PAGES: SettingsPage[] = [
  { slug: 'cabang', label: 'Cabang', description: 'Daftar cabang tempat karyawan bekerja.', group: 'Perusahaan', roles: MANAGERS, icon: Building2 },
  { slug: 'kategori', label: 'Kategori Karyawan', description: 'Kelompok karyawan, misalnya supir atau staf kantor.', group: 'Perusahaan', roles: MANAGERS, icon: Tags },
  { slug: 'mesin', label: 'Mesin Absensi', description: 'Mesin sidik jari di tiap cabang dan status koneksinya.', group: 'Perusahaan', roles: MANAGERS, icon: Fingerprint },
  { slug: 'aturan-kerja', label: 'Aturan Kerja', description: 'Jam kerja, istirahat, lembur, dan upah.', group: 'Aturan & gaji', roles: MANAGERS, icon: SlidersHorizontal, readOnlyFor: ['HR_FINANCE'] },
  { slug: 'kalender-libur', label: 'Kalender Libur', description: 'Hari libur nasional dan libur perusahaan.', group: 'Aturan & gaji', roles: MANAGERS, icon: CalendarDays },
  { slug: 'jenis-izin', label: 'Jenis Izin/Cuti', description: 'Jenis izin dan cuti yang bisa diajukan karyawan.', group: 'Aturan & gaji', roles: ['OWNER'], icon: FileText },
  { slug: 'simulasi', label: 'Simulasi Perhitungan', description: 'Coba hitung satu hari untuk memeriksa aturan kerja.', group: 'Aturan & gaji', roles: MANAGERS, icon: Calculator },
  { slug: 'akun', label: 'Akun', description: 'Akun login dan reset password.', group: 'Akses & keamanan', roles: MANAGERS, icon: UserCog },
  { slug: 'audit-log', label: 'Audit Log', description: 'Riwayat setiap perubahan data.', group: 'Akses & keamanan', roles: ['OWNER'], icon: ScrollText },
]

export function settingsPageFor(pathname: string): SettingsPage | undefined {
  return SETTINGS_PAGES.find((page) => pathname === `/pengaturan/${page.slug}` || pathname.startsWith(`/pengaturan/${page.slug}/`))
}

export function menuFor(role: Role): MenuItem[] {
  return MENU.filter((item) => item.roles.includes(role))
}

/** Item menu untuk path yang sedang dibuka (termasuk sub-halaman seperti /karyawan/12), untuk judul di header. */
export function menuItemFor(pathname: string): MenuItem | undefined {
  // Cocok persis dulu, agar /absensi/scan-tak-dikenal tidak terbaca sebagai sub-halaman /absensi.
  return (
    MENU.find((item) => item.path === pathname) ??
    MENU.find((item) => item.path !== '/' && pathname.startsWith(`${item.path}/`))
  )
}

export function homePathFor(role: Role): string {
  return role === 'EMPLOYEE' ? '/beranda' : '/'
}
