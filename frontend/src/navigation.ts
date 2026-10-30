import {
  Building2,
  Calculator,
  CalendarCheck,
  CalendarDays,
  FileSpreadsheet,
  FileText,
  House,
  ClipboardCheck,
  ListChecks,
  ScanSearch,
  ShieldAlert,
  Fingerprint,
  SlidersHorizontal,
  Timer,
  LayoutDashboard,
  ScrollText,
  Tags,
  UserCog,
  UserRound,
  Users,
  type LucideIcon,
} from 'lucide-react'

import type { Role } from '@/lib/types'

export type MenuGroup = 'Operasional' | 'Master data' | 'Pengaturan' | 'Sistem' | 'Saya'

export interface MenuItem {
  path: string
  label: string
  roles: Role[]
  icon: LucideIcon
  group: MenuGroup
  /** Tampil langsung di bottom navigation HP; menu lain masuk ke "Lainnya". */
  mobile?: boolean
}

const MANAGERS: Role[] = ['OWNER', 'HR_FINANCE']

/** Urutan kelompok di sidebar (docs/design-system.md §6.9). */
export const MENU_GROUPS: MenuGroup[] = ['Operasional', 'Master data', 'Pengaturan', 'Sistem', 'Saya']

/** Menu per role sesuai docs/kebutuhan.md. Backend tetap menjadi penentu akses yang sebenarnya. */
export const MENU: MenuItem[] = [
  { path: '/', label: 'Dashboard', roles: MANAGERS, icon: LayoutDashboard, group: 'Operasional', mobile: true },
  { path: '/absensi', label: 'Absensi Harian', roles: MANAGERS, icon: ClipboardCheck, group: 'Operasional', mobile: true },
  { path: '/absensi/perlu-ditinjau', label: 'Perlu Ditinjau', roles: MANAGERS, icon: ListChecks, group: 'Operasional' },
  { path: '/absensi/lembur', label: 'Validasi Lembur', roles: MANAGERS, icon: Timer, group: 'Operasional' },
  { path: '/absensi/scan-tak-dikenal', label: 'Scan Tak Dikenal', roles: MANAGERS, icon: ScanSearch, group: 'Operasional' },
  { path: '/absensi/scan-tertahan', label: 'Scan Tertahan', roles: MANAGERS, icon: ShieldAlert, group: 'Operasional' },
  // Di HP diakses lewat lonceng notifikasi di header atau menu "Lainnya", agar bottom navigation tidak penuh.
  { path: '/izin', label: 'Izin & Cuti', roles: MANAGERS, icon: CalendarCheck, group: 'Operasional' },
  { path: '/laporan', label: 'Laporan', roles: MANAGERS, icon: FileSpreadsheet, group: 'Operasional' },
  { path: '/karyawan', label: 'Karyawan', roles: MANAGERS, icon: Users, group: 'Master data', mobile: true },
  { path: '/cabang', label: 'Cabang', roles: MANAGERS, icon: Building2, group: 'Master data' },
  { path: '/kategori', label: 'Kategori Karyawan', roles: MANAGERS, icon: Tags, group: 'Master data' },
  { path: '/mesin', label: 'Mesin Absensi', roles: MANAGERS, icon: Fingerprint, group: 'Master data', mobile: true },
  { path: '/akun', label: 'Akun', roles: MANAGERS, icon: UserCog, group: 'Master data' },
  { path: '/aturan-kerja', label: 'Aturan Kerja', roles: MANAGERS, icon: SlidersHorizontal, group: 'Pengaturan' },
  { path: '/kalender-libur', label: 'Kalender Libur', roles: MANAGERS, icon: CalendarDays, group: 'Pengaturan' },
  { path: '/jenis-izin', label: 'Jenis Izin/Cuti', roles: ['OWNER'], icon: FileText, group: 'Pengaturan' },
  { path: '/simulasi', label: 'Simulasi Perhitungan', roles: MANAGERS, icon: Calculator, group: 'Pengaturan' },
  { path: '/audit-log', label: 'Audit Log', roles: ['OWNER'], icon: ScrollText, group: 'Sistem' },
  { path: '/beranda', label: 'Beranda', roles: ['EMPLOYEE'], icon: House, group: 'Saya', mobile: true },
  { path: '/izin-saya', label: 'Izin', roles: ['EMPLOYEE'], icon: CalendarCheck, group: 'Saya', mobile: true },
  { path: '/absensi-saya', label: 'Absensi', roles: ['EMPLOYEE'], icon: ClipboardCheck, group: 'Saya', mobile: true },
  { path: '/profil', label: 'Profil', roles: ['EMPLOYEE'], icon: UserRound, group: 'Saya', mobile: true },
  // Owner/HR: di HP lewat menu "Lainnya", agar bottom navigation tidak penuh.
  { path: '/profil', label: 'Profil', roles: MANAGERS, icon: UserRound, group: 'Saya' },
]

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
