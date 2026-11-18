import { ChevronRight, Eye } from 'lucide-react'
import { Link } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { PageHeader } from '@/components/PageHeader'
import { SETTINGS_GROUPS, SETTINGS_PAGES } from '@/navigation'

/**
 * Pintu ke halaman yang jarang dipakai (K-145): data perusahaan, aturan & gaji, akses & keamanan.
 * Kartu yang tidak boleh dibuka role pengguna tidak ditampilkan; backend tetap penentu akses.
 */
export function SettingsPage() {
  const { me } = useAuth()
  if (!me) return null
  const pages = SETTINGS_PAGES.filter((page) => page.roles.includes(me.role))

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Pengaturan"
        description="Data perusahaan dan aturan yang biasanya hanya diatur sekali di awal atau sesekali."
      />
      {SETTINGS_GROUPS.map((group) => {
        const items = pages.filter((page) => page.group === group)
        if (items.length === 0) return null
        return (
          <section key={group} aria-labelledby={`grup-${group}`} className="flex flex-col gap-3">
            <h2 id={`grup-${group}`} className="text-base font-semibold text-steel-800 dark:text-foreground">
              {group}
            </h2>
            <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {items.map((page) => {
                const readOnly = page.readOnlyFor?.includes(me.role)
                return (
                  <li key={page.slug} className="min-w-0">
                    <Link
                      to={`/pengaturan/${page.slug}`}
                      className="group flex h-full min-h-20 cursor-pointer items-center gap-4 rounded-xl border p-4 glass transition-colors duration-200 hover:border-primary/40 hover:bg-accent/60 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                    >
                      <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
                        <page.icon className="size-5" aria-hidden="true" />
                      </span>
                      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                        <span className="font-semibold text-foreground">{page.label}</span>
                        <span className="text-sm text-muted-foreground">{page.description}</span>
                        {readOnly && (
                          <span className="mt-1 inline-flex w-fit items-center gap-1 text-xs font-semibold text-muted-foreground">
                            <Eye className="size-3.5" aria-hidden="true" />
                            Hanya lihat
                          </span>
                        )}
                      </span>
                      <ChevronRight
                        className="size-4 shrink-0 text-muted-foreground transition-transform duration-200 group-hover:translate-x-0.5"
                        aria-hidden="true"
                      />
                    </Link>
                  </li>
                )
              })}
            </ul>
          </section>
        )
      })}
    </div>
  )
}
