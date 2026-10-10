import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, Clock } from 'lucide-react'
import { Link } from 'react-router'

import { EmptyState } from '@/components/feedback'
import { PageHeader } from '@/components/PageHeader'
import { api } from '@/lib/api'
import type { Branch, Device, Employee } from '@/lib/types'
import { cn } from '@/lib/utils'

interface TileProps {
  label: string
  value: number | undefined
  to: string
  hint: string
  highlight?: boolean
}

/** KPI tile (docs/design-system.md §6.10). Tile utama berlatar biru steel dan memakan 2 kolom. */
function Tile({ label, value, to, hint, highlight }: TileProps) {
  return (
    <Link
      to={to}
      className={cn(
        'group flex flex-col justify-between gap-6 rounded-lg border p-5 transition-colors',
        highlight
          ? 'col-span-2 border-steel-600 bg-steel-600 text-white hover:bg-steel-700'
          : 'bg-card hover:border-steel-300',
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <span className={cn('label-caps', highlight ? 'text-steel-200' : 'text-muted-foreground')}>{label}</span>
        <ArrowUpRight
          aria-hidden="true"
          className={cn('size-4 opacity-60 transition-opacity group-hover:opacity-100', highlight && 'text-gold-500')}
        />
      </div>
      <div>
        <p className={cn('font-heading font-extrabold tabular-nums', highlight ? 'text-5xl' : 'text-4xl text-steel-800 dark:text-foreground')}>
          {value ?? '…'}
        </p>
        <p className={cn('mt-1 text-xs', highlight ? 'text-steel-200' : 'text-muted-foreground')}>{hint}</p>
      </div>
    </Link>
  )
}

export function DashboardPage() {
  const employees = useQuery({ queryKey: ['/api/employees'], queryFn: () => api<Employee[]>('/api/employees') })
  const branches = useQuery({ queryKey: ['/api/branches'], queryFn: () => api<Branch[]>('/api/branches') })
  const devices = useQuery({ queryKey: ['/api/devices'], queryFn: () => api<Device[]>('/api/devices') })

  const activeEmployees = employees.data?.filter((e) => e.active)
  const activeBranches = branches.data?.filter((b) => b.active) ?? []

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Dashboard" description="Ringkasan data karyawan, cabang, dan mesin absensi." />

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Tile label="Karyawan aktif" value={activeEmployees?.length} to="/karyawan" hint="Lihat daftar karyawan" highlight />
        <Tile label="Cabang aktif" value={branches.data ? activeBranches.length : undefined} to="/cabang" hint="Kelola cabang" />
        <Tile label="Mesin terdaftar" value={devices.data?.length} to="/mesin" hint="Kelola mesin" />
      </div>

      <div className="grid gap-4 lg:grid-cols-12">
        <section className="rounded-lg border bg-card lg:col-span-7">
          <h2 className="border-b px-5 py-4 text-base font-semibold">Kehadiran hari ini</h2>
          <EmptyState message="Kehadiran real-time, keterlambatan, dan lembur akan tampil di sini setelah modul absensi (Fase 3)." />
        </section>

        <section className="rounded-lg border bg-card lg:col-span-5">
          <h2 className="border-b px-5 py-4 text-base font-semibold">Ringkasan per cabang</h2>
          {branches.data && activeBranches.length === 0 && <EmptyState message="Belum ada cabang aktif." />}
          <ul className="divide-y">
            {activeBranches.map((branch) => {
              const employeeCount = activeEmployees?.filter((e) => e.branchId === branch.id).length ?? 0
              const deviceCount = devices.data?.filter((d) => d.branchId === branch.id).length ?? 0
              return (
                <li key={branch.id} className="flex items-center justify-between gap-4 px-5 py-3.5">
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{branch.name}</p>
                    <p className="flex items-center gap-1 text-xs text-muted-foreground">
                      <Clock className="size-3" aria-hidden="true" />
                      {branch.timezone}
                    </p>
                  </div>
                  <dl className="flex gap-5 text-right text-sm tabular-nums">
                    <div>
                      <dt className="label-caps text-muted-foreground">Karyawan</dt>
                      <dd className="font-heading text-lg font-bold">{employeeCount}</dd>
                    </div>
                    <div>
                      <dt className="label-caps text-muted-foreground">Mesin</dt>
                      <dd className="font-heading text-lg font-bold">{deviceCount}</dd>
                    </div>
                  </dl>
                </li>
              )
            })}
          </ul>
        </section>
      </div>
    </div>
  )
}
