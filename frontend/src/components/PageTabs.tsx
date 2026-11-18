import { Link, useLocation, useSearchParams } from 'react-router'

import { cn } from '@/lib/utils'

export interface PageTab {
  value: string
  label: string
  /** Jumlah yang perlu ditindak; tampil bila lebih dari 0. */
  badge?: number
}

/** Filter periode yang ikut dibawa saat pindah tab, agar tab lain menampilkan periode yang sama. */
const CARRIED = ['from', 'to', 'branchId', 'categoryId', 'jenis', 'bulan']

/** Nilai tab aktif dari query string; nilai kosong atau tak dikenal jatuh ke tab pertama. */
export function useActiveTab(tabs: { value: string }[], param = 'tab'): string {
  const [searchParams] = useSearchParams()
  const current = searchParams.get(param)
  return tabs.some((tab) => tab.value === current) ? current! : tabs[0].value
}

/**
 * Tab halaman (K-145). Setiap tab adalah tautan biasa, jadi tombol Kembali browser kembali ke tab sebelumnya dan
 * alamat tab bisa dibagikan. Gaya sama dengan sub menu absensi lama (docs/design-system.md).
 */
export function PageTabs({ tabs, param = 'tab', label }: { tabs: PageTab[]; param?: string; label: string }) {
  const { pathname } = useLocation()
  const [searchParams] = useSearchParams()
  const active = useActiveTab(tabs, param)

  const hrefFor = (value: string) => {
    const params = new URLSearchParams({ [param]: value })
    for (const key of CARRIED) {
      const carried = searchParams.get(key)
      if (carried) params.set(key, carried)
    }
    return `${pathname}?${params}`
  }

  return (
    <div
      role="tablist"
      aria-label={label}
      className="flex flex-wrap items-center gap-1.5 rounded-xl border p-1.5 glass-strong sm:w-fit"
    >
      {tabs.map((tab) => {
        const selected = tab.value === active
        return (
          <Link
            key={tab.value}
            role="tab"
            aria-selected={selected}
            to={hrefFor(tab.value)}
            className={cn(
              'inline-flex min-h-10 cursor-pointer items-center justify-center gap-2 rounded-lg px-3.5 py-2 font-heading text-xs font-semibold transition-colors duration-200 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none',
              selected
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground',
            )}
          >
            {tab.label}
            {tab.badge !== undefined && tab.badge > 0 && (
              <span
                className={cn(
                  'grid min-w-5 place-items-center rounded-full px-1.5 text-[11px] leading-5 font-bold tabular-nums',
                  selected ? 'bg-primary-foreground/20 text-primary-foreground' : 'bg-warning-bg text-warning',
                )}
              >
                {tab.badge}
              </span>
            )}
          </Link>
        )
      })}
    </div>
  )
}
