import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, Radio, WifiOff } from 'lucide-react'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { api } from '@/lib/api'
import { SCAN_FLAG_LABELS } from '@/lib/attendance'
import { formatDate, formatDateTime } from '@/lib/format'
import type { DashboardSummary } from '@/lib/types'
import { useDashboardStream } from '@/lib/useDashboardStream'
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
          : 'glass hover:border-steel-300',
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

/** Kehadiran hari ini, diperbarui otomatis lewat SSE begitu scan tersimpan (DASH-001–007). */
export function DashboardPage() {
  const live = useDashboardStream()
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/dashboard/summary'],
    queryFn: () => api<DashboardSummary>('/api/dashboard/summary'),
  })
  const totals = data?.totals

  const attention = data
    ? [
        { label: 'Tidak hadir', value: data.totals.absent, to: '/absensi' },
        { label: 'Belum lengkap (baru scan sekali)', value: data.totals.incomplete, to: '/absensi' },
        { label: 'Perlu ditinjau', value: data.totals.needsReview, to: '/absensi' },
        { label: 'Aturan kerja belum diatur', value: data.totals.ruleMissing, to: '/aturan-kerja' },
        { label: 'Scan dengan PIN tak dikenal', value: data.unmatchedScans, to: '/absensi/scan-tak-dikenal' },
        { label: 'Pengajuan izin menunggu persetujuan', value: data.pendingLeaveRequests ?? 0, to: '/izin' },
      ]
    : []

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Dashboard"
        description={data ? `Kehadiran ${formatDate(data.date)}. Diperbarui otomatis saat ada scan baru.` : 'Kehadiran hari ini.'}
        actions={
          live ? (
            <Badge variant="success">
              <Radio />
              Langsung
            </Badge>
          ) : (
            <Badge variant="neutral">
              <WifiOff />
              Menyambung…
            </Badge>
          )
        }
      />

      {error && <ErrorAlert message={error.message} />}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Tile
          label="Hadir hari ini"
          value={totals?.present}
          to="/absensi"
          hint={
            totals
              ? `dari ${totals.totalEmployees} karyawan aktif${totals.onLeave ? ` · ${totals.onLeave} izin/cuti` : ''}`
              : 'Memuat…'
          }
          highlight
        />
        <Tile label="Terlambat" value={totals?.late} to="/absensi" hint="Melewati jam masuk + toleransi" />
        <Tile label="Kandidat lembur" value={totals?.overtimeCandidates} to="/absensi" hint="Belum diverifikasi" />
      </div>

      <div className="grid gap-4 lg:grid-cols-12">
        <section className="rounded-lg border glass lg:col-span-7">
          <h2 className="border-b px-5 py-4 text-base font-semibold">Scan terbaru</h2>
          {isPending && <SkeletonLines />}
          {data?.recentScans.length === 0 && <EmptyState message="Belum ada scan hari ini." />}
          <ul className="divide-y">
            {data?.recentScans.map((scan) => (
              <li key={scan.id} className="flex items-center justify-between gap-4 px-5 py-3">
                <div className="min-w-0">
                  <p className="truncate font-semibold">
                    {scan.employeeName ?? <span className="text-muted-foreground">PIN {scan.devicePin} (belum dipetakan)</span>}
                  </p>
                  <p className="text-xs text-muted-foreground">{scan.deviceName}</p>
                  {scan.flags
                    .filter((flag) => flag !== 'PIN_TIDAK_DIKENAL')
                    .map((flag) => (
                      <p key={flag} className="text-xs text-warning">
                        {SCAN_FLAG_LABELS[flag]}
                      </p>
                    ))}
                </div>
                <span className="shrink-0 text-sm tabular-nums">{formatDateTime(scan.scannedAt)}</span>
              </li>
            ))}
          </ul>
        </section>

        <div className="flex flex-col gap-4 lg:col-span-5">
          <section className="rounded-lg border glass">
            <h2 className="border-b px-5 py-4 text-base font-semibold">Perlu perhatian</h2>
            <ul className="divide-y">
              {attention.map((item) => (
                <li key={item.label}>
                  <Link to={item.to} className="flex items-center justify-between gap-4 px-5 py-3 hover:bg-accent/60">
                    <span className="text-sm">{item.label}</span>
                    <span className={cn('font-heading text-lg font-bold tabular-nums', item.value > 0 && 'text-warning')}>
                      {item.value}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>

          <section className="rounded-lg border glass">
            <h2 className="border-b px-5 py-4 text-base font-semibold">Status mesin</h2>
            {data?.devices.length === 0 && <EmptyState message="Belum ada mesin terdaftar." />}
            <ul className="divide-y">
              {data?.devices.map((device) => (
                <li key={device.id} className="flex items-center justify-between gap-4 px-5 py-3">
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{device.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {device.branchName} · terakhir {formatDateTime(device.lastSeenAt)}
                    </p>
                  </div>
                  <Badge variant={device.online ? 'success' : 'neutral'}>{device.online ? 'Online' : 'Offline'}</Badge>
                </li>
              ))}
            </ul>
          </section>

          <section className="rounded-lg border glass">
            <h2 className="border-b px-5 py-4 text-base font-semibold">Per cabang</h2>
            <ul className="divide-y">
              {data?.branches.map((branch) => (
                <li key={branch.branchId} className="flex items-center justify-between gap-4 px-5 py-3">
                  <p className="min-w-0 truncate font-semibold">{branch.branchName}</p>
                  <dl className="flex gap-5 text-right text-sm tabular-nums">
                    <div>
                      <dt className="label-caps text-muted-foreground">Hadir</dt>
                      <dd className="font-heading text-lg font-bold">
                        {branch.counts.present}/{branch.counts.totalEmployees}
                      </dd>
                    </div>
                    <div>
                      <dt className="label-caps text-muted-foreground">Terlambat</dt>
                      <dd className="font-heading text-lg font-bold">{branch.counts.late}</dd>
                    </div>
                  </dl>
                </li>
              ))}
            </ul>
          </section>
        </div>
      </div>
    </div>
  )
}
