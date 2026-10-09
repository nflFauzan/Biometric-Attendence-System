import { NavLink, Outlet, useNavigate } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { ROLE_LABELS } from '@/lib/types'
import { menuFor } from '@/navigation'

export function AppLayout() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  if (!me) {
    return null
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-muted/40">
      <aside className="flex w-60 shrink-0 flex-col border-r bg-background">
        <div className="border-b px-5 py-4">
          <p className="text-sm font-semibold">Biometric Attendance</p>
          <p className="text-xs text-muted-foreground">Sistem absensi &amp; penggajian</p>
        </div>
        <nav className="flex flex-1 flex-col gap-1 p-3" aria-label="Menu utama">
          {menuFor(me.role).map((item) => (
            <NavLink
              key={item.path}
              to={item.path}
              end={item.path === '/'}
              className={({ isActive }) =>
                cn(
                  'rounded-md px-3 py-2 text-sm hover:bg-accent',
                  isActive && 'bg-accent font-medium text-accent-foreground',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="flex flex-1 flex-col">
        <header className="flex items-center justify-end gap-3 border-b bg-background px-6 py-3">
          <div className="text-right text-sm">
            <p className="font-medium">{me.employeeName ?? me.username}</p>
            <p className="text-xs text-muted-foreground">{ROLE_LABELS[me.role]}</p>
          </div>
          <Button variant="outline" size="sm" onClick={() => navigate('/ganti-password')}>
            Ganti password
          </Button>
          <Button variant="ghost" size="sm" onClick={handleLogout}>
            Keluar
          </Button>
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
