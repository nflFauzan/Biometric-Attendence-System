import { KeyRound, LogOut, Menu, Settings2, UserRound } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { AppBackdrop } from '@/components/AppBackdrop'
import { BrandMark, Wordmark } from '@/components/Brand'
import { DeviceNotifications } from '@/components/DeviceNotifications'
import { GlassAdjustModal } from '@/components/GlassAdjustModal'
import { LeaveNotifications } from '@/components/LeaveNotifications'
import { ThemeToggle } from '@/components/ThemeToggle'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { initials } from '@/lib/format'
import { ROLE_LABELS } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { cn } from '@/lib/utils'
import { MENU_GROUPS, menuFor, menuItemFor, settingsPageFor, type MenuItem } from '@/navigation'

/**
 * Kerangka aplikasi dengan arsitektur Floating Islands 21st.dev:
 * - AppBackdrop dinamis (wallpaper ambient + vignette shade)
 * - Topbar mengambang (.gw-topbar.gw-glass) dengan tombol Atur Kaca & profil
 * - Side rail mengambang (.gw-rail.gw-glass) untuk navigasi desktop/tablet
 * - Workspace card mengambang (.gw-panel.gw-glass) membungkus Outlet
 * - Mobile floating dock (.gw-dock.gw-glass) di perangkat mobile
 */
export function AppLayout() {
  const { me, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [moreOpen, setMoreOpen] = useState(false)
  const [adjustGlassOpen, setAdjustGlassOpen] = useState(false)

  if (!me) {
    return null
  }

  const menu = menuFor(me.role)
  const current = menuItemFor(pathname)
  const settingsPage = settingsPageFor(pathname)
  const trail = settingsPage
    ? { parent: 'Pengaturan', label: settingsPage.label }
    : current && { parent: current.group === 'Utama' ? null : current.group, label: current.label }
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
    <div className="relative isolate min-h-dvh flex flex-col font-sans">
      {/* Dynamic wallpaper backdrop */}
      <AppBackdrop className="fixed inset-0 pointer-events-none -z-10" />

      {/* Accent gold line at very top edge */}
      <div aria-hidden="true" className="fixed inset-x-0 top-0 z-50 h-0.5 bg-gold-500" />

      {/* Floating Islands Workspace Container */}
      <div className="gw-workspace mx-auto flex w-full max-w-[1440px] flex-1 flex-col gap-3.5 p-3 sm:gap-4 sm:p-5 lg:p-6">
        {/* Floating Topbar */}
        <header className="gw-topbar gw-glass sticky top-2 z-30 flex items-center justify-between gap-3 rounded-[20px] px-4 py-3 shadow-lg backdrop-blur-xl sm:px-5 sm:py-3.5">
          <div className="flex min-w-0 items-center gap-3">
            <BrandMark className="sm:hidden" />
            <Wordmark tone="dark" className="hidden sm:flex" />
            {trail && (
              <p className="hidden min-w-0 truncate text-sm text-muted-foreground md:block">
                {trail.parent && (
                  <>
                    <span>{trail.parent}</span>
                    <span className="mx-2 text-steel-300">/</span>
                  </>
                )}
                <span className="font-semibold text-foreground">{trail.label}</span>
              </p>
            )}
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            {/* Tombol Atur Kaca 21st.dev */}
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setAdjustGlassOpen(true)}
              className="h-8 gap-1.5 rounded-full border-white/20 bg-white/10 px-3 text-xs font-medium text-foreground backdrop-blur-md hover:bg-white/20"
              title="Atur transparansi, blur, dan wallpaper kaca"
            >
              <Settings2 className="size-3.5 text-gold-400" />
              <span>Atur Kaca</span>
            </Button>

            <ThemeToggle />
            {me.role !== 'EMPLOYEE' && <LeaveNotifications />}
            {me.role !== 'EMPLOYEE' && <DeviceNotifications />}

            <div className="hidden text-right sm:block">
              <p className="text-sm leading-tight font-semibold text-foreground">{displayName}</p>
              <p className="text-xs text-muted-foreground">{ROLE_LABELS[me.role]}</p>
            </div>

            <span
              aria-hidden="true"
              className="grid size-9 place-items-center rounded-full bg-steel-100 font-heading text-xs font-bold text-primary dark:bg-secondary dark:text-foreground shadow-sm"
            >
              {initials(displayName)}
            </span>

            <div className="hidden items-center gap-1 md:flex">
              {me.role !== 'EMPLOYEE' && (
                <Button variant="ghost" size="sm" onClick={() => navigate('/profil')}>
                  <UserRound className="size-4" />
                  Profil
                </Button>
              )}
              <Button variant="ghost" size="sm" onClick={() => navigate('/ganti-password')}>
                <KeyRound className="size-4" />
                Ganti password
              </Button>
              <Button variant="ghost" size="sm" onClick={handleLogout}>
                <LogOut className="size-4" />
                Keluar
              </Button>
            </div>
          </div>
        </header>

        {/* Floating Middle Layout: Side Rail + Main Panel */}
        <div className="flex flex-1 gap-3.5 sm:gap-4">
          {/* Floating Side Rail for Desktop / Tablet */}
          <aside className="gw-rail gw-glass hidden w-[76px] shrink-0 flex-col rounded-[23px] p-2.5 shadow-xl md:flex lg:w-60">
            <nav className="relative z-10 flex flex-1 flex-col gap-4 overflow-y-auto px-1 py-2" aria-label="Menu utama">
              {MENU_GROUPS.map((group) => {
                const items = menu.filter((item) => item.group === group && !item.mobileOnly)
                if (items.length === 0) {
                  return null
                }
                return (
                  <div key={group} className="flex flex-col gap-1">
                    {group !== 'Utama' && (
                      <p className="label-caps hidden px-3 pb-1 text-[11px] font-bold tracking-wider text-steel-200 lg:block">
                        {group}
                      </p>
                    )}
                    {items.map((item) => (
                      <SidebarLink key={item.path} item={item} />
                    ))}
                  </div>
                )
              })}
            </nav>
            <div
              aria-hidden="true"
              className="motif-spandek pointer-events-none absolute inset-x-0 bottom-0 h-32 text-white/[0.04] [mask-image:linear-gradient(to_top,black,transparent)]"
            />
          </aside>

          {/* Main Floating Workspace Card */}
          <main className="gw-panel gw-glass min-w-0 flex-1 rounded-[27px] p-4 sm:p-6 lg:p-7 shadow-2xl relative overflow-hidden flex flex-col pb-24 md:pb-6">
            <Outlet />
          </main>
        </div>
      </div>

      {/* Floating Bottom Mobile Dock */}
      <nav
        aria-label="Menu bawah"
        className="gw-dock gw-glass fixed inset-x-3 bottom-[calc(0.75rem+env(safe-area-inset-bottom))] z-30 mx-auto max-w-md rounded-[24px] p-1 shadow-2xl md:hidden border border-white/15 backdrop-blur-2xl"
      >
        <ul className="flex items-center justify-around">
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
              className="flex h-14 w-full flex-col items-center justify-center gap-0.5 text-[11px] font-semibold text-muted-foreground hover:text-foreground transition-colors"
            >
              <Menu className="size-5" />
              Lainnya
            </button>
          </li>
        </ul>
      </nav>

      {/* Modal Pengatur Efek Kaca */}
      <GlassAdjustModal open={adjustGlassOpen} onClose={() => setAdjustGlassOpen(false)} />

      {/* Dialog Menu Lainnya untuk Mobile */}
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
          <div className="flex max-h-[65vh] flex-col gap-3.5 overflow-y-auto pr-1">
            {MENU_GROUPS.map((group) => {
              const items = menu.filter((item) => !item.mobile && item.group === group)
              if (items.length === 0) return null
              return (
                <div key={group} className="flex flex-col gap-1 rounded-xl border bg-card/60 p-2.5">
                  <p className="px-1 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                    {group === 'Utama' ? 'Menu' : group}
                  </p>
                  <ul className="flex flex-col gap-0.5">
                    {items.map((item) => (
                      <li key={item.path}>
                        <NavLink
                          to={item.path}
                          onClick={() => setMoreOpen(false)}
                          className="flex h-11 items-center gap-3 rounded-lg px-2 text-sm font-medium transition-colors hover:bg-accent"
                        >
                          <div className="grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
                            <item.icon className="size-4" />
                          </div>
                          <span>{item.label}</span>
                        </NavLink>
                      </li>
                    ))}
                  </ul>
                </div>
              )
            })}

            <div className="flex flex-col gap-1 rounded-xl border bg-card/60 p-2.5">
              <p className="px-1 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">Tampilan & Kaca</p>
              <button
                type="button"
                onClick={() => {
                  setMoreOpen(false)
                  setAdjustGlassOpen(true)
                }}
                className="flex h-11 w-full items-center gap-3 rounded-lg px-2 text-sm font-medium transition-colors hover:bg-accent"
              >
                <div className="grid size-8 place-items-center rounded-md bg-gold-500/10 text-gold-500">
                  <Settings2 className="size-4" />
                </div>
                <span>Sesuaikan Efek Kaca</span>
              </button>
            </div>

            <div className="flex flex-col gap-1 rounded-xl border bg-card/60 p-2.5">
              <p className="px-1 text-[11px] font-bold uppercase tracking-wider text-muted-foreground">Akun & Sesi</p>
              <ul className="flex flex-col gap-0.5">
                <li>
                  <button
                    type="button"
                    onClick={goChangePassword}
                    className="flex h-11 w-full items-center gap-3 rounded-lg px-2 text-sm font-medium transition-colors hover:bg-accent"
                  >
                    <div className="grid size-8 place-items-center rounded-md bg-primary/10 text-primary">
                      <KeyRound className="size-4" />
                    </div>
                    <span>Ganti password</span>
                  </button>
                </li>
                <li>
                  <button
                    type="button"
                    onClick={handleLogout}
                    className="flex h-11 w-full items-center gap-3 rounded-lg px-2 text-sm font-medium text-danger transition-colors hover:bg-danger-bg"
                  >
                    <div className="grid size-8 place-items-center rounded-md bg-danger/10 text-danger">
                      <LogOut className="size-4" />
                    </div>
                    <span>Keluar</span>
                  </button>
                </li>
              </ul>
            </div>
          </div>
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
          'relative flex flex-col items-center gap-1 rounded-xl px-2 py-2 text-center text-[10.5px] leading-tight transition-all duration-200 hover:bg-white/10 hover:text-white',
          'lg:flex-row lg:gap-3 lg:px-3 lg:py-2.5 lg:text-left lg:text-sm',
          isActive
            ? 'bg-white/15 font-semibold text-white shadow-sm before:absolute before:inset-y-2 before:left-0 before:w-[3px] before:rounded-full before:bg-gold-500'
            : 'text-foreground/80 hover:text-foreground',
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
          'relative flex h-14 flex-col items-center justify-center gap-0.5 text-[11px] font-semibold text-muted-foreground transition-colors',
          isActive
            ? 'text-primary font-bold before:absolute before:top-1 before:size-1.5 before:rounded-full before:bg-gold-500'
            : 'hover:text-foreground',
        )
      }
    >
      <item.icon className="size-5" />
      <span className="max-w-full truncate px-1">{item.label}</span>
    </NavLink>
  )
}
