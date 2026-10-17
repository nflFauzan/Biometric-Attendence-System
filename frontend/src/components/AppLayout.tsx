import { KeyRound, LogOut, Menu } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { BrandMark, Wordmark } from '@/components/Brand'
import { DeviceNotifications } from '@/components/DeviceNotifications'
import { LeaveNotifications } from '@/components/LeaveNotifications'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { initials } from '@/lib/format'
import { ROLE_LABELS } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { cn } from '@/lib/utils'
import { MENU_GROUPS, menuFor, menuItemFor, type MenuItem } from '@/navigation'

/**
 * Kerangka aplikasi (docs/design-system.md §5 dan §6.9):
 * HP = header + bottom navigation, tablet = sidebar ringkas, desktop = sidebar penuh.
 * Semua bagian memakai kaca di atas latar statis (docs/rencana-tema-kaca.md §2–§3).
 */
export function AppLayout() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [moreOpen, setMoreOpen] = useState(false)
  if (!me) {
    return null
  }

  const menu = menuFor(me.role)
  const current = menuItemFor(pathname)
  const displayName = me.employeeName ?? me.username

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }
  const goChangePassword = () => {
    setMoreOpen(false)
    navigate('/ganti-password')
  }

  return (
    <div className="relative isolate min-h-dvh">
      <div aria-hidden="true" className="app-backdrop pointer-events-none fixed inset-0 -z-10" />
      <div aria-hidden="true" className="fixed inset-x-0 top-0 z-40 h-0.5 bg-gold-500" />

      <aside className="glass-sidebar fixed inset-y-0 left-0 z-30 hidden w-[76px] flex-col overflow-hidden border-r border-white/10 text-steel-200 md:flex lg:w-62">
        <div className="flex h-16 items-center justify-center border-b border-white/10 px-4 lg:justify-start lg:px-5">
          <BrandMark className="lg:hidden" />
          <Wordmark tone="dark" className="hidden lg:flex" />
        </div>
        <nav className="relative z-10 flex flex-1 flex-col gap-5 overflow-y-auto px-2 py-4 lg:px-3" aria-label="Menu utama">
          {MENU_GROUPS.map((group) => {
            const items = menu.filter((item) => item.group === group)
            if (items.length === 0) {
              return null
            }
            return (
              <div key={group} className="flex flex-col gap-1">
                <p className="label-caps hidden px-3 pb-1 text-steel-200 lg:block">{group}</p>
                {items.map((item) => (
                  <SidebarLink key={item.path} item={item} />
                ))}
              </div>
            )
          })}
        </nav>
        <div
          aria-hidden="true"
          className="motif-spandek pointer-events-none absolute inset-x-0 bottom-0 h-40 text-white/[0.06] [mask-image:linear-gradient(to_top,black,transparent)]"
        />
      </aside>

      <div className="flex min-h-dvh flex-col md:pl-[76px] lg:pl-62">
        <header className="glass-bar sticky top-0 z-20 flex h-14 items-center justify-between gap-3 border-b px-4 md:h-16 md:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Wordmark className="md:hidden" />
            {current && (
              <p className="hidden min-w-0 truncate text-sm text-muted-foreground md:block">
                {current.group}
                <span className="mx-2 text-steel-300">/</span>
                <span className="font-semibold text-foreground">{current.label}</span>
              </p>
            )}
          </div>
          <div className="flex items-center gap-2 md:gap-3">
            <ThemeToggle />
            {me.role !== 'EMPLOYEE' && <LeaveNotifications />}
            {me.role !== 'EMPLOYEE' && <DeviceNotifications />}
            <div className="hidden text-right sm:block">
              <p className="text-sm leading-tight font-semibold">{displayName}</p>
              <p className="text-xs text-muted-foreground">{ROLE_LABELS[me.role]}</p>
            </div>
            <span
              aria-hidden="true"
              className="grid size-9 place-items-center rounded-full bg-steel-100 font-heading text-xs font-bold text-primary dark:bg-secondary dark:text-foreground"
            >
              {initials(displayName)}
            </span>
            <div className="hidden items-center gap-1 md:flex">
              <Button variant="ghost" size="sm" onClick={() => navigate('/ganti-password')}>
                <KeyRound />
                Ganti password
              </Button>
              <Button variant="ghost" size="sm" onClick={handleLogout}>
                <LogOut />
                Keluar
              </Button>
            </div>
          </div>
        </header>

        <main className="mx-auto w-full max-w-[1440px] flex-1 px-4 pt-4 pb-28 md:px-6 md:py-6">
          <Outlet />
        </main>
      </div>

      <nav
        aria-label="Menu bawah"
        className="glass-bar fixed inset-x-2 bottom-[calc(0.5rem+env(safe-area-inset-bottom))] z-30 overflow-hidden rounded-2xl border shadow-lg shadow-steel-900/15 md:hidden"
      >
        <ul className="flex">
          {menu
            .filter((item) => item.mobile)
            .map((item) => (
              <li key={item.path} className="min-w-0 flex-1">
                <BottomLink item={item} />
              </li>
            ))}
          <li className="min-w-0 flex-1">
            <button
              type="button"
              onClick={() => setMoreOpen(true)}
              className="flex h-16 w-full flex-col items-center justify-center gap-1 text-[11px] font-semibold text-muted-foreground"
            >
              <Menu className="size-6" />
              Lainnya
            </button>
          </li>
        </ul>
      </nav>

      <Dialog open={moreOpen} onOpenChange={setMoreOpen}>
        <DialogContent className={dialogSheetClass}>
          <DialogHeader>
            <DialogTitle>Menu lainnya</DialogTitle>
          </DialogHeader>
          <div className="flex items-center gap-3 rounded-lg bg-secondary/60 p-3">
            <span className="grid size-10 place-items-center rounded-full bg-steel-600 font-heading text-sm font-bold text-white">
              {initials(displayName)}
            </span>
            <div>
              <p className="font-semibold">{displayName}</p>
              <p className="text-sm text-muted-foreground">{ROLE_LABELS[me.role]}</p>
            </div>
          </div>
          <ul className="flex flex-col">
            {menu
              .filter((item) => !item.mobile)
              .map((item) => (
                <li key={item.path}>
                  <NavLink
                    to={item.path}
                    onClick={() => setMoreOpen(false)}
                    className="flex h-12 items-center gap-3 rounded-md px-2 text-[15px] hover:bg-accent"
                  >
                    <item.icon className="size-5 text-primary" />
                    {item.label}
                  </NavLink>
                </li>
              ))}
            <li className="mt-2 border-t pt-2">
              <button
                type="button"
                onClick={goChangePassword}
                className="flex h-12 w-full items-center gap-3 rounded-md px-2 text-[15px] hover:bg-accent"
              >
                <KeyRound className="size-5 text-primary" />
                Ganti password
              </button>
            </li>
            <li>
              <button
                type="button"
                onClick={handleLogout}
                className="flex h-12 w-full items-center gap-3 rounded-md px-2 text-[15px] text-danger hover:bg-danger-bg"
              >
                <LogOut className="size-5" />
                Keluar
              </button>
            </li>
          </ul>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function SidebarLink({ item }: { item: MenuItem }) {
  return (
    <NavLink
      to={item.path}
      end={item.path === '/'}
      title={item.label}
      className={({ isActive }) =>
        cn(
          'relative flex flex-col items-center gap-1 rounded-md px-1 py-2 text-center text-[10.5px] leading-tight transition-colors hover:bg-white/10 hover:text-white',
          'lg:flex-row lg:gap-3 lg:px-3 lg:py-2.5 lg:text-left lg:text-sm',
          isActive &&
            'bg-white/15 font-semibold text-white before:absolute before:inset-y-2 before:left-0 before:w-[3px] before:rounded-full before:bg-gold-500',
        )
      }
    >
      <item.icon className="size-5 shrink-0" />
      <span>{item.label}</span>
    </NavLink>
  )
}

function BottomLink({ item }: { item: MenuItem }) {
  return (
    <NavLink
      to={item.path}
      end={item.path === '/'}
      className={({ isActive }) =>
        cn(
          'relative flex h-16 flex-col items-center justify-center gap-1 text-[11px] font-semibold text-muted-foreground',
          isActive && 'text-primary before:absolute before:top-1.5 before:size-1.5 before:rounded-full before:bg-gold-500',
        )
      }
    >
      <item.icon className="size-6" />
      <span className="max-w-full truncate px-1">{item.label}</span>
    </NavLink>
  )
}
