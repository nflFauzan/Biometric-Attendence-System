import { useQuery } from '@tanstack/react-query'
import {
  ArrowUpRight,
  CalendarOff,
  CheckCircle2,
  ChevronRight,
  CirclePause,
  Clock,
  Fingerprint,
  Moon,
  ScanEye,
  Settings2,
  TriangleAlert,
  Wifi,
  WifiOff,
  XCircle,
  type LucideIcon,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { Badge } from '@/components/ui/badge'
import { api } from '@/lib/api'
import { SCAN_FLAG_LABELS } from '@/lib/attendance'
import { formatDateTime, formatDayLabel, initials } from '@/lib/format'
import type { DashboardSummary } from '@/lib/types'
import { useDashboardStream } from '@/lib/useDashboardStream'
import { cn } from '@/lib/utils'

/*
 * Dashboard bergaya aplikasi HP (docs/design-system.md §6.10): sapaan, kartu utama kehadiran,
 * dua kartu angka, lalu daftar yang hanya menampilkan hal yang perlu ditindak.
 * Semua grid memakai kolom minmax(0, 1fr) agar isi panjang tidak melebarkan halaman di HP.
 */

function greeting(hour: number): string {
  if (hour < 11) return 'Selamat pagi'
  if (hour < 15) return 'Selamat siang'
  if (hour < 18) return 'Selamat sore'
  return 'Selamat malam'
}

function scanTime(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleTimeString('id-ID', { hour: '2-digit', minute: '2-digit' })
}

/** Cincin persentase hadir; dekoratif karena angkanya sudah tertulis di kartu. */
function Ring({ value, total }: { value: number; total: number }) {
  const ratio = total > 0 ? Math.min(value / total, 1) : 0
  const radius = 34
  const circumference = 2 * Math.PI * radius
  return (
    <div className="relative grid size-24 shrink-0 place-items-center" aria-hidden="true">
      <svg viewBox="0 0 80 80" className="absolute inset-0 -rotate-90">
        <circle cx="40" cy="40" r={radius} fill="none" strokeWidth="7" className="stroke-white/12" />
        <circle
          cx="40"
          cy="40"
          r={radius}
          fill="none"
          strokeWidth="7"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - ratio)}
          className="stroke-gold-500 transition-[stroke-dashoffset] duration-500 ease-out motion-reduce:transition-none"
        />
      </svg>
      <span className="font-heading text-lg font-bold tabular-nums">{Math.round(ratio * 100)}%</span>
    </div>
  )
}

interface StatTileProps {
  label: string
  value: number | undefined
  hint: string
  to: string
  icon: LucideIcon
  tone: 'warning' | 'info'
}

function StatTile({ label, value, hint, to, icon: Icon, tone }: StatTileProps) {
  return (
    <Link
      to={to}
      className="group flex min-w-0 flex-col gap-4 rounded-2xl border glass p-4 transition-[transform,border-color] duration-150 ease-out hover:border-steel-300 active:scale-[0.98] md:p-5"
    >
      <div className="flex items-center justify-between">
        <span
          className={cn(
            'grid size-10 place-items-center rounded-full',
            tone === 'warning' ? 'bg-warning-bg text-warning' : 'bg-info-bg text-info',
          )}
        >
          <Icon className="size-5" />
        </span>
        <ArrowUpRight aria-hidden="true" className="size-4 text-muted-foreground transition-colors group-hover:text-foreground" />
      </div>
      <div className="min-w-0">
        <p className="font-heading text-3xl leading-none font-bold text-steel-800 tabular-nums dark:text-foreground">{value ?? '…'}</p>
        <p className="mt-2 text-sm font-semibold">{label}</p>
        <p className="mt-0.5 text-xs text-muted-foreground">{hint}</p>
      </div>
    </Link>
  )
}

function Section({ title, trailing, className, children }: { title: string; trailing?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <section className={cn('flex min-w-0 flex-col gap-3', className)}>
      <div className="flex items-center justify-between gap-3 px-1">
        <h2 className="text-base font-semibold">{title}</h2>
        {trailing}
      </div>
      {children}
    </section>
  )
}

const listCard = 'overflow-hidden rounded-2xl border glass'

/** Kehadiran hari ini, diperbarui otomatis lewat SSE begitu scan tersimpan (DASH-001–007). */
export function DashboardPage() {
  const { me } = useAuth()
  const live = useDashboardStream()
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/dashboard/summary'],
    queryFn: () => api<DashboardSummary>('/api/dashboard/summary'),
  })
  const totals = data?.totals
  const firstName = (me?.employeeName ?? me?.username ?? '').split(' ')[0]

  const attention: { label: string; value: number; to: string; icon: LucideIcon }[] = data
    ? [
        { label: 'Pengajuan izin menunggu persetujuan', value: data.pendingLeaveRequests ?? 0, to: '/izin', icon: CalendarOff },
        { label: 'Tidak hadir', value: data.totals.absent, to: '/absensi', icon: XCircle },
        { label: 'Belum lengkap (baru scan sekali)', value: data.totals.incomplete, to: '/absensi', icon: TriangleAlert },
        { label: 'Perlu ditinjau', value: data.totals.needsReview, to: '/absensi', icon: ScanEye },
        { label: 'Scan dengan PIN tak dikenal', value: data.unmatchedScans, to: '/absensi/scan-tak-dikenal', icon: Fingerprint },
        { label: 'Scan tertahan', value: data.heldScans, to: '/absensi/scan-tertahan', icon: CirclePause },
        { label: 'Aturan kerja belum diatur', value: data.totals.ruleMissing, to: '/aturan-kerja', icon: Settings2 },
      ].filter((item) => item.value > 0)
    : []

  return (
    <div className="flex flex-col gap-6">
      <header className="flex items-start justify-between gap-3 px-1">
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{data ? formatDayLabel(data.date) : 'Hari ini'}</p>
          <h1 className="mt-0.5 text-2xl leading-8 font-bold text-balance text-steel-800 dark:text-foreground">
            {greeting(new Date().getHours())}
            {firstName && `, ${firstName}`}
          </h1>
        </div>
        {live ? (
          <Badge variant="success" className="mt-1 shrink-0">
            <span className="relative flex size-2">
              <span className="absolute inline-flex size-full rounded-full bg-success opacity-60 motion-safe:animate-ping" />
              <span className="relative inline-flex size-2 rounded-full bg-success" />
            </span>
            Langsung
          </Badge>
        ) : (
          <Badge variant="neutral" className="mt-1 shrink-0">
            <WifiOff />
            Menyambung…
          </Badge>
        )}
      </header>

      {error && <ErrorAlert message={error.message} />}

      <div className="grid grid-cols-2 gap-3 md:gap-4 lg:grid-cols-4">
        <Link
          to="/absensi"
          className="group relative col-span-2 isolate flex min-w-0 flex-col gap-5 overflow-hidden rounded-2xl bg-gradient-to-br from-steel-600 via-steel-700 to-steel-900 p-5 text-white shadow-lg shadow-steel-900/20 transition-transform duration-150 ease-out active:scale-[0.99] md:p-6"
        >
          <span aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-gold-500" />
          <span
            aria-hidden="true"
            className="motif-spandek absolute -right-6 -bottom-10 -z-10 h-40 w-56 rotate-[-8deg] text-white/[0.06]"
          />
          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-steel-100">Hadir hari ini</p>
              <p className="mt-2 font-heading leading-none font-bold tabular-nums">
                <span className="text-6xl">{totals?.present ?? '…'}</span>
                {totals && <span className="ml-1.5 text-2xl text-steel-200">/ {totals.totalEmployees}</span>}
              </p>
              <p className="mt-2 text-sm text-steel-100">
                {totals ? `dari ${totals.totalEmployees} karyawan aktif` : 'Memuat…'}
              </p>
            </div>
            {totals && <Ring value={totals.present} total={totals.totalEmployees} />}
          </div>
          {totals && (
            <dl className="grid grid-cols-3 divide-x divide-white/15 rounded-xl bg-white/8 py-3 text-center">
              {[
                { label: 'Izin/cuti', value: totals.onLeave ?? 0 },
                { label: 'Belum hadir', value: totals.absent },
                { label: 'Belum lengkap', value: totals.incomplete },
              ].map((item) => (
                <div key={item.label} className="flex min-w-0 flex-col-reverse px-1">
                  <dt className="truncate text-xs text-steel-100">{item.label}</dt>
                  <dd className="font-heading text-xl font-bold tabular-nums">{item.value}</dd>
                </div>
              ))}
            </dl>
          )}
        </Link>
        <StatTile label="Terlambat" value={totals?.late} hint="Lewat jam masuk + toleransi" to="/absensi" icon={Clock} tone="warning" />
        <StatTile label="Kandidat lembur" value={totals?.overtimeCandidates} hint="Belum diverifikasi" to="/absensi" icon={Moon} tone="info" />
      </div>

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-flow-dense lg:grid-cols-12 lg:grid-rows-[auto_auto_1fr]">
        <Section
          title="Perlu perhatian"
          trailing={attention.length > 0 && <Badge variant="warning">{attention.length}</Badge>}
          className="lg:col-span-5 lg:col-start-8"
        >
          {data && attention.length === 0 ? (
            <div className="flex items-center gap-3 rounded-2xl border glass p-4">
              <span className="grid size-10 shrink-0 place-items-center rounded-full bg-success-bg text-success">
                <CheckCircle2 className="size-5" />
              </span>
              <div className="min-w-0">
                <p className="text-sm font-semibold">Semua beres</p>
                <p className="text-xs text-muted-foreground">Tidak ada yang perlu ditindak saat ini.</p>
              </div>
            </div>
          ) : (
            <div className={listCard}>
              {isPending && <SkeletonLines />}
              <ul className="divide-y">
                {attention.map((item) => (
                  <li key={item.label}>
                    <Link to={item.to} className="flex min-h-14 items-center gap-3 px-4 py-3 transition-colors hover:bg-accent/60">
                      <span className="grid size-9 shrink-0 place-items-center rounded-full bg-warning-bg text-warning">
                        <item.icon className="size-[18px]" />
                      </span>
                      <span className="min-w-0 flex-1 text-sm">{item.label}</span>
                      <span className="font-heading text-lg font-bold tabular-nums">{item.value}</span>
                      <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </Section>

        <Section
          title="Scan terbaru"
          trailing={
            <Link to="/absensi" className="text-sm font-semibold text-primary">
              Lihat semua
            </Link>
          }
          className="lg:col-span-7 lg:col-start-1 lg:row-span-3 lg:row-start-1"
        >
          <div className={listCard}>
            {isPending && <SkeletonLines />}
            {data?.recentScans.length === 0 && <EmptyState message="Belum ada scan hari ini." />}
            <ul className="divide-y">
              {data?.recentScans.map((scan) => {
                const flags = scan.flags.filter((flag) => flag !== 'PIN_TIDAK_DIKENAL')
                return (
                  <li key={scan.id} className="flex items-center gap-3 px-4 py-3">
                    {scan.employeeName ? (
                      <span
                        aria-hidden="true"
                        className="grid size-10 shrink-0 place-items-center rounded-full bg-steel-100 font-heading text-xs font-bold text-primary dark:bg-secondary dark:text-foreground"
                      >
                        {initials(scan.employeeName)}
                      </span>
                    ) : (
                      <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-full bg-warning-bg text-warning">
                        <Fingerprint className="size-5" />
                      </span>
                    )}
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-semibold">
                        {scan.employeeName ?? <span className="text-muted-foreground">PIN {scan.devicePin} (belum dipetakan)</span>}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">{scan.deviceName}</p>
                      {flags.map((flag) => (
                        <p key={flag} className="text-xs text-warning">
                          {SCAN_FLAG_LABELS[flag]}
                        </p>
                      ))}
                    </div>
                    <time
                      dateTime={scan.scannedAt}
                      title={formatDateTime(scan.scannedAt)}
                      className="shrink-0 rounded-full bg-secondary px-2.5 py-1 font-heading text-xs font-semibold tabular-nums"
                    >
                      {scanTime(scan.scannedAt)}
                    </time>
                  </li>
                )
              })}
            </ul>
          </div>
        </Section>

        <Section title="Status mesin" className="lg:col-span-5 lg:col-start-8">
          <div className={listCard}>
            {data?.devices.length === 0 && <EmptyState message="Belum ada mesin terdaftar." />}
            <ul className="divide-y">
              {data?.devices.map((device) => (
                <li key={device.id} className="flex items-center gap-3 px-4 py-3">
                  <span
                    className={cn(
                      'grid size-9 shrink-0 place-items-center rounded-full',
                      device.online ? 'bg-success-bg text-success' : 'bg-secondary text-muted-foreground',
                    )}
                  >
                    {device.online ? <Wifi className="size-[18px]" /> : <WifiOff className="size-[18px]" />}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold">{device.name}</p>
                    <p className="truncate text-xs text-muted-foreground">
                      {device.branchName} · terakhir {formatDateTime(device.lastSeenAt)}
                    </p>
                  </div>
                  <Badge variant={device.online ? 'success' : 'neutral'}>{device.online ? 'Online' : 'Offline'}</Badge>
                </li>
              ))}
            </ul>
          </div>
        </Section>

        {data && data.branches.length > 0 && (
          <Section title="Per cabang" className="lg:col-span-5 lg:col-start-8">
            <ul className={cn(listCard, 'divide-y')}>
              {data.branches.map((branch) => (
                <li key={branch.branchId} className="flex items-center justify-between gap-4 px-4 py-3">
                  <p className="min-w-0 truncate font-semibold">{branch.branchName}</p>
                  <dl className="flex shrink-0 gap-5 text-right tabular-nums">
                    <div className="flex flex-col-reverse">
                      <dt className="text-xs text-muted-foreground">Hadir</dt>
                      <dd className="font-heading text-lg font-bold">
                        {branch.counts.present}/{branch.counts.totalEmployees}
                      </dd>
                    </div>
                    <div className="flex flex-col-reverse">
                      <dt className="text-xs text-muted-foreground">Terlambat</dt>
                      <dd className="font-heading text-lg font-bold">{branch.counts.late}</dd>
                    </div>
                  </dl>
                </li>
              ))}
            </ul>
          </Section>
        )}
      </div>
    </div>
  )
}
