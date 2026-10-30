import { NavLink } from 'react-router'
import { cn } from '@/lib/utils'

export const ATTENDANCE_TABS = [
  { path: '/absensi', label: 'Absensi Harian' },
  { path: '/absensi/perlu-ditinjau', label: 'Perlu Ditinjau' },
  { path: '/absensi/scan-tertahan', label: 'Scan Tertahan' },
  { path: '/absensi/scan-tak-dikenal', label: 'Scan Tak Dikenal' },
  { path: '/absensi/lembur', label: 'Validasi Lembur' },
]

export function AttendanceSubNav() {
  return (
    <nav
      aria-label="Sub menu absensi"
      className="flex flex-wrap items-center gap-1.5 rounded-xl border p-1.5 glass-strong sm:w-fit"
    >
      {ATTENDANCE_TABS.map((tab) => (
        <NavLink
          key={tab.path}
          to={tab.path}
          end={tab.path === '/absensi'}
          className={({ isActive }) =>
            cn(
              'inline-flex min-h-10 items-center justify-center rounded-lg px-3.5 py-2 font-heading text-xs font-semibold transition-colors',
              isActive
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground',
            )
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}

