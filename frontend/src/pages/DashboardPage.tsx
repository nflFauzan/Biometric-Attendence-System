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
import { useState, type ReactNode } from 'react'
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

/** Cincin persentase hadir dekoratif dengan aksen emas Takka Steel */
function Ring({ value, total }: { value: number; total: number }) {
  const ratio = total > 0 ? Math.min(value / total, 1) : 0
  const radius = 34
  const circumference = 2 * Math.PI * radius
  return (
    <div className="relative grid size-24 shrink-0 place-items-center" aria-hidden="true">
      <svg viewBox="0 0 80 80" className="absolute inset-0 -rotate-90">
        <circle cx="40" cy="40" r={radius} fill="none" strokeWidth="7" className="stroke-white/10" />
        <circle
          cx="40"
          cy="40"
          r={radius}
          fill="none"
          strokeWidth="7"
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - ratio)}
          className="stroke-gold-400 transition-[stroke-dashoffset] duration-700 ease-out motion-reduce:transition-none"
        />
      </svg>
      <span className="font-heading text-lg font-bold tabular-nums text-foreground">{Math.round(ratio * 100)}%</span>
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
      className="gw-panel gw-glass group flex min-w-0 flex-col justify-between gap-4 rounded-[24px] border border-white/20 p-5 shadow-xl backdrop-blur-xl transition-all duration-200 hover:border-white/35 hover:scale-[1.01] active:scale-[0.98]"
    >
      <div className="flex items-center justify-between">
        <span
          className={cn(
            'grid size-10 place-items-center rounded-2xl border border-white/15 backdrop-blur-md shadow-sm',
            tone === 'warning' ? 'bg-amber-500/15 text-amber-400' : 'bg-sky-500/15 text-sky-400',
          )}
        >
          <Icon className="size-5" />
        </span>
        <span className="grid size-7 place-items-center rounded-full bg-white/10 text-muted-foreground transition-colors group-hover:bg-white/20 group-hover:text-foreground">
          <ArrowUpRight aria-hidden="true" className="size-4" />
        </span>
      </div>
      <div className="min-w-0">
        <p className="font-heading text-3xl font-extrabold tracking-tight text-foreground tabular-nums">{value ?? '…'}</p>
        <p className="mt-1 text-sm font-semibold text-foreground/90">{label}</p>
        <p className="mt-0.5 truncate text-xs text-muted-foreground">{hint}</p>
      </div>
    </Link>
  )
}

function Section({ title, trailing, className, children }: { title: string; trailing?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <section className={cn('flex min-w-0 flex-col gap-3', className)}>
      <div className="flex items-center justify-between gap-3 px-1">
        <h2 className="text-sm font-semibold tracking-wide uppercase text-muted-foreground">{title}</h2>
        {trailing}
      </div>
      {children}
    </section>
  )
}

const listCard = 'gw-panel gw-glass overflow-hidden rounded-[24px] border border-white/20 shadow-xl backdrop-blur-xl'

/** Kehadiran hari ini dengan layout Bento Grid 21st.dev */
export function DashboardPage() {
  const { me } = useAuth()
  const live = useDashboardStream()
  const [salutation] = useState(() => greeting(new Date().getHours()))
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
        { label: 'Scan dengan PIN tak dikenal', value: data.unmatchedScans, to: '/absensi?tab=bereskan', icon: Fingerprint },
        { label: 'Scan tertahan', value: data.heldScans, to: '/absensi?tab=bereskan', icon: CirclePause },
        { label: 'Aturan kerja belum diatur', value: data.totals.ruleMissing, to: '/pengaturan/aturan-kerja', icon: Settings2 },
      ].filter((item) => item.value > 0)
    : []

  return (
    <div className="flex flex-col gap-6">
      {/* Overview Hero Header 21st.dev Style */}
      <header className="flex items-start justify-between gap-3 px-1">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {data ? formatDayLabel(data.date) : 'Hari ini'}
          </p>
          <h1 className="mt-1 text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
            {salutation}
            {firstName && `, ${firstName}`}
          </h1>
        </div>
        {live ? (
          <Badge variant="success" className="mt-1 shrink-0 rounded-full px-3 py-1 border border-emerald-500/30 bg-emerald-500/15 text-emerald-400 backdrop-blur-md">
            <span className="relative flex size-2 mr-1.5">
              <span className="absolute inline-flex size-full rounded-full bg-emerald-400 opacity-60 motion-safe:animate-ping" />
              <span className="relative inline-flex size-2 rounded-full bg-emerald-400" />
            </span>
            Langsung
          </Badge>
        ) : (
          <Badge variant="neutral" className="mt-1 shrink-0 rounded-full px-3 py-1 border border-white/15 bg-white/10 text-muted-foreground backdrop-blur-md">
            <WifiOff className="size-3.5 mr-1" />
            Menyambung…
          </Badge>
        )}
      </header>

      {error && <ErrorAlert message={error.message} />}

      {/* Top Bento Row: Hero Stat + Quick Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Main Attendance Hero Card */}
        <Link
          to="/absensi"
          className="gw-panel gw-glass group relative col-span-1 md:col-span-2 isolate flex min-w-0 flex-col justify-between gap-5 overflow-hidden rounded-[26px] border border-white/20 p-6 shadow-2xl backdrop-blur-2xl transition-all duration-300 hover:border-white/35 hover:scale-[1.005] active:scale-[0.99]"
        >
          <div aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-gradient-to-r from-steel-500 via-gold-400 to-steel-500 opacity-80" />

          <div className="flex items-center justify-between gap-4">
            <div className="min-w-0">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">Hadir hari ini</p>
              <p className="mt-2 font-heading leading-none font-extrabold tracking-tight text-foreground tabular-nums">
                <span className="text-5xl sm:text-6xl">{totals?.present ?? '…'}</span>
                {totals && <span className="ml-2 text-xl sm:text-2xl font-semibold text-muted-foreground">/ {totals.totalEmployees}</span>}
              </p>
              <p className="mt-2 text-xs sm:text-sm text-muted-foreground">
                {totals ? `dari ${totals.totalEmployees} karyawan terdaftar` : 'Memuat data…'}
              </p>
            </div>
            {totals && <Ring value={totals.present} total={totals.totalEmployees} />}
          </div>

          {totals && (
            <dl className="grid grid-cols-3 divide-x divide-white/10 rounded-2xl border border-white/10 bg-white/5 py-3 text-center backdrop-blur-md">
              {[
                { label: 'Izin/cuti', value: totals.onLeave ?? 0 },
                { label: 'Belum hadir', value: totals.absent },
                { label: 'Belum lengkap', value: totals.incomplete },
              ].map((item) => (
                <div key={item.label} className="flex min-w-0 flex-col-reverse px-2">
                  <dt className="truncate text-[11px] font-medium text-muted-foreground">{item.label}</dt>
                  <dd className="font-heading text-lg sm:text-xl font-bold tabular-nums text-foreground">{item.value}</dd>
                </div>
              ))}
            </dl>
          )}
        </Link>

        {/* Quick Stat Tiles */}
        <StatTile label="Terlambat" value={totals?.late} hint="Lewat batas toleransi" to="/absensi" icon={Clock} tone="warning" />
        <StatTile label="Kandidat lembur" value={totals?.overtimeCandidates} hint="Menunggu verifikasi" to="/absensi?tab=lembur" icon={Moon} tone="info" />
      </div>

      {/* Bottom Bento Row: Feeds, Attention, Devices, Branches */}
      <div className="grid grid-cols-1 items-start gap-5 lg:grid-flow-dense lg:grid-cols-12">
        {/* Kolom Kiri / Utama: Recent Activity (Scan Terbaru) */}
        <Section
          title="Scan terbaru"
          trailing={
            <Link to="/absensi" className="text-xs font-semibold text-primary hover:underline">
              Lihat semua
            </Link>
          }
          className="lg:col-span-7"
        >
          <div className={listCard}>
            {isPending && <SkeletonLines />}
            {data?.recentScans.length === 0 && <EmptyState message="Belum ada scan hari ini." />}
            <ul className="divide-y divide-white/10">
              {data?.recentScans.map((scan) => {
                const flags = scan.flags.filter((flag) => flag !== 'PIN_TIDAK_DIKENAL')
                return (
                  <li key={scan.id} className="flex items-center gap-3.5 px-4 py-3.5 transition-colors hover:bg-white/5">
                    {scan.employeeName ? (
                      <span
                        aria-hidden="true"
                        className="grid size-10 shrink-0 place-items-center rounded-2xl border border-white/15 bg-white/10 font-heading text-xs font-bold text-primary shadow-sm backdrop-blur-md"
                      >
                        {initials(scan.employeeName)}
                      </span>
                    ) : (
                      <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-2xl border border-amber-500/20 bg-amber-500/10 text-amber-400">
                        <Fingerprint className="size-5" />
                      </span>
                    )}
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-semibold text-foreground text-sm">
                        {scan.employeeName ?? <span className="text-muted-foreground">PIN {scan.devicePin} (belum dipetakan)</span>}
                      </p>
                      <p className="truncate text-xs text-muted-foreground">{scan.deviceName}</p>
                      {flags.map((flag) => (
                        <p key={flag} className="text-xs text-amber-400 font-medium">
                          {SCAN_FLAG_LABELS[flag]}
                        </p>
                      ))}
                    </div>
                    <time
                      dateTime={scan.scannedAt}
                      title={formatDateTime(scan.scannedAt)}
                      className="shrink-0 rounded-full border border-white/10 bg-white/5 px-2.5 py-1 font-heading text-xs font-semibold tabular-nums text-foreground/90 backdrop-blur-sm"
                    >
                      {scanTime(scan.scannedAt)}
                    </time>
                  </li>
                )
              })}
            </ul>
          </div>
        </Section>

        {/* Kolom Kanan: Perlu Perhatian, Status Mesin, Per Cabang */}
        <div className="flex flex-col gap-5 lg:col-span-5">
          {/* Perlu Perhatian */}
          <Section
            title="Perlu perhatian"
            trailing={attention.length > 0 && <Badge variant="warning">{attention.length}</Badge>}
          >
            {data && attention.length === 0 ? (
              <div className="flex items-center gap-3 rounded-[24px] border border-white/20 gw-panel gw-glass p-4">
                <span className="grid size-10 shrink-0 place-items-center rounded-2xl border border-emerald-500/25 bg-emerald-500/15 text-emerald-400">
                  <CheckCircle2 className="size-5" />
                </span>
                <div className="min-w-0">
                  <p className="text-sm font-semibold text-foreground">Semua beres</p>
                  <p className="text-xs text-muted-foreground">Tidak ada yang perlu ditindak saat ini.</p>
                </div>
              </div>
            ) : (
              <div className={listCard}>
                {isPending && <SkeletonLines />}
                <ul className="divide-y divide-white/10">
                  {attention.map((item) => (
                    <li key={item.label}>
                      <Link to={item.to} className="flex min-h-14 items-center gap-3 px-4 py-3 transition-colors hover:bg-white/5">
                        <span className="grid size-9 shrink-0 place-items-center rounded-2xl border border-amber-500/20 bg-amber-500/10 text-amber-400">
                          <item.icon className="size-4" />
                        </span>
                        <span className="min-w-0 flex-1 text-sm text-foreground/90">{item.label}</span>
                        <span className="font-heading text-base font-bold tabular-nums text-foreground">{item.value}</span>
                        <ChevronRight aria-hidden="true" className="size-4 shrink-0 text-muted-foreground" />
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </Section>

          {/* Status Mesin */}
          <Section title="Status mesin">
            <div className={listCard}>
              {data?.devices.length === 0 && <EmptyState message="Belum ada mesin terdaftar." />}
              <ul className="divide-y divide-white/10">
                {data?.devices.map((device) => (
                  <li key={device.id} className="flex items-center gap-3 px-4 py-3">
                    <span
                      className={cn(
                        'grid size-9 shrink-0 place-items-center rounded-2xl border backdrop-blur-sm',
                        device.online
                          ? 'border-emerald-500/30 bg-emerald-500/15 text-emerald-400'
                          : 'border-white/10 bg-white/5 text-muted-foreground',
                      )}
                    >
                      {device.online ? <Wifi className="size-4" /> : <WifiOff className="size-4" />}
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-semibold text-foreground">{device.name}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {device.branchName} · {formatDateTime(device.lastSeenAt)}
                      </p>
                    </div>
                    <Badge variant={device.online ? 'success' : 'neutral'} className="text-[11px] rounded-full px-2 py-0.5">
                      {device.online ? 'Online' : 'Offline'}
                    </Badge>
                  </li>
                ))}
              </ul>
            </div>
          </Section>

          {/* Per Cabang */}
          {data && data.branches.length > 0 && (
            <Section title="Per cabang">
              <ul className={cn(listCard, 'divide-y divide-white/10')}>
                {data.branches.map((branch) => (
                  <li key={branch.branchId} className="flex items-center justify-between gap-4 px-4 py-3">
                    <p className="min-w-0 truncate text-sm font-semibold text-foreground">{branch.branchName}</p>
                    <dl className="flex shrink-0 gap-5 text-right tabular-nums">
                      <div className="flex flex-col-reverse">
                        <dt className="text-[11px] text-muted-foreground">Hadir</dt>
                        <dd className="font-heading text-base font-bold text-foreground">
                          {branch.counts.present}/{branch.counts.totalEmployees}
                        </dd>
                      </div>
                      <div className="flex flex-col-reverse">
                        <dt className="text-[11px] text-muted-foreground">Terlambat</dt>
                        <dd className="font-heading text-base font-bold text-amber-400">{branch.counts.late}</dd>
                      </div>
                    </dl>
                  </li>
                ))}
              </ul>
            </Section>
          )}
        </div>
      </div>
    </div>
  )
}
