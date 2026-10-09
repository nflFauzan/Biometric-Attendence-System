import type { Role } from '@/lib/types'

export interface MenuItem {
  path: string
  label: string
  roles: Role[]
}

const MANAGERS: Role[] = ['OWNER', 'HR_FINANCE']

/** Menu per role sesuai docs/kebutuhan.md. Backend tetap menjadi penentu akses yang sebenarnya. */
export const MENU: MenuItem[] = [
  { path: '/', label: 'Dashboard', roles: MANAGERS },
  { path: '/karyawan', label: 'Karyawan', roles: MANAGERS },
  { path: '/cabang', label: 'Cabang', roles: MANAGERS },
  { path: '/kategori', label: 'Kategori Karyawan', roles: MANAGERS },
  { path: '/mesin', label: 'Mesin Absensi', roles: MANAGERS },
  { path: '/akun', label: 'Akun', roles: MANAGERS },
  { path: '/audit-log', label: 'Audit Log', roles: ['OWNER'] },
  { path: '/profil', label: 'Profil Saya', roles: ['EMPLOYEE'] },
]

export function menuFor(role: Role): MenuItem[] {
  return MENU.filter((item) => item.roles.includes(role))
}

export function homePathFor(role: Role): string {
  return role === 'EMPLOYEE' ? '/profil' : '/'
}
