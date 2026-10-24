import { useQuery } from '@tanstack/react-query'
import { ChevronRight, Plus } from 'lucide-react'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { LeaveStatusBadge } from '@/components/leave/LeaveStatusBadge'
import { TeamStatusBadges } from '@/components/TeamStatusBadges'
import { buttonVariants } from '@/components/ui/button'
import { useAuth } from '@/auth/AuthContext'
import { api } from '@/lib/api'
import { formatDate, formatTime, localDateIso } from '@/lib/format'
import { formatLeaveRange, isPendingStatus } from '@/lib/leave'
import { monthOf, monthRange } from '@/lib/team'
import type { LeaveRequest, TeamMember } from '@/lib/types'
import { cn } from '@/lib/utils'

const MAX_ONGOING = 3

const cardClass = 'flex flex-col gap-3 rounded-xl border p-5 glass-strong'

/** Beranda karyawan: sapaan, kehadiran hari ini, pengajuan berjalan, dan ringkasan bulan ini (spec §11). */
export function HomePage() {
  const { me } = useAuth()
  const employeeId = me?.employeeId
  const today = localDateIso()
  const firstName = me?.employeeName?.trim().split(/\s+/)[0] ?? me?.username ?? ''

  // Memakai endpoint rekan kerja untuk diri sendiri (K-083): satu hari untuk "Hari ini", satu bulan untuk "Bulan ini".
  const attendance = (from: string, to: string) => ({
    queryKey: ['/api/team/employees', String(employeeId), from, to],
    queryFn: () => api<TeamMember>(`/api/team/employees/${employeeId}/attendance?from=${from}&to=${to}`),
    enabled: employeeId != null,
  })
  const todayQuery = useQuery(attendance(today, today))
  const { from, to } = monthRange(monthOf(today), today)
  const monthQuery = useQuery(attendance(from, to))
  const leaves = useQuery({
    queryKey: ['/api/me/leave-requests'],
    queryFn: () => api<LeaveRequest[]>('/api/me/leave-requests'),
  })

  const todayDay = todayQuery.data?.days.find((d) => d.date === today)
  const ongoing = (leaves.data ?? []).filter((r) => r.status === 'DRAFT' || isPendingStatus(r.status)).slice(0, MAX_ONGOING)
  const days = monthQuery.data?.days ?? []
  const summary = [
    { label: 'Hadir', value: days.filter((d) => d.status === 'HADIR').length },
    { label: 'Izin', value: days.filter((d) => d.status === 'IZIN').length },
    { label: 'Tidak hadir', value: days.filter((d) => d.status === 'TIDAK_HADIR').length },
  ]

  return (
    <div className="mx-auto flex w-full max-w-xl flex-col gap-4">
      <header>
        <h1 className="font-heading text-2xl font-bold text-steel-800 dark:text-foreground">Halo, {firstName}</h1>
        <p className="text-muted-foreground">{formatDate(today)}</p>
      </header>

      <Link to="/izin-saya/baru" className={cn(buttonVariants({ size: 'lg' }), 'min-h-11 w-full sm:w-fit')}>
        <Plus />
        Ajukan izin
      </Link>

      <section aria-labelledby="home-today" className={cardClass}>
        <h2 id="home-today" className="font-heading text-lg font-bold">
          Hari ini
        </h2>
        {todayQuery.error && <ErrorAlert message={todayQuery.error.message} />}
        {todayQuery.isPending && todayQuery.fetchStatus !== 'idle' && <SkeletonLines count={2} />}
        {todayQuery.data && (
          <>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <p className="text-sm text-muted-foreground">Masuk</p>
                <p className="text-xl font-semibold tabular-nums">{todayDay?.checkIn ? formatTime(todayDay.checkIn) : '-'}</p>
              </div>
              <div>
                <p className="text-sm text-muted-foreground">Pulang</p>
                <p className="text-xl font-semibold tabular-nums">{todayDay?.checkOut ? formatTime(todayDay.checkOut) : '-'}</p>
              </div>
            </div>
            {todayDay ? <TeamStatusBadges day={todayDay} /> : <p className="text-sm text-muted-foreground">Belum ada data hari ini.</p>}
          </>
        )}
      </section>

      <section aria-labelledby="home-leaves" className={cardClass}>
        <div className="flex items-center justify-between gap-2">
          <h2 id="home-leaves" className="font-heading text-lg font-bold">
            Pengajuan berjalan
          </h2>
          <Link to="/izin-saya" className="inline-flex min-h-11 items-center text-sm font-semibold text-steel-600 hover:underline">
            Lihat semua
          </Link>
        </div>
        {leaves.error && <ErrorAlert message={leaves.error.message} />}
        {leaves.isPending && <SkeletonLines count={2} />}
        {leaves.data && ongoing.length === 0 && <EmptyState message="Belum ada pengajuan berjalan" />}
        <ul className="divide-y">
          {ongoing.map((r) => (
            <li key={r.id}>
              <Link to={`/izin-saya/${r.id}`} className="flex min-h-11 items-center justify-between gap-3 py-3">
                <div className="min-w-0">
                  <p className="font-semibold">{r.leaveTypeName}</p>
                  <p className="text-sm text-muted-foreground">{formatLeaveRange(r)}</p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <LeaveStatusBadge status={r.status} />
                  <ChevronRight className="size-4 text-muted-foreground" />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <section aria-labelledby="home-month" className={cardClass}>
        <h2 id="home-month" className="font-heading text-lg font-bold">
          Bulan ini
        </h2>
        {monthQuery.error && <ErrorAlert message={monthQuery.error.message} />}
        {monthQuery.isPending && monthQuery.fetchStatus !== 'idle' && <SkeletonLines count={1} />}
        {monthQuery.data && (
          <dl className="grid grid-cols-3 gap-3 text-center">
            {summary.map((item) => (
              <div key={item.label} className="rounded-lg bg-steel-50 px-2 py-3 dark:bg-secondary">
                <dd className="text-2xl font-bold tabular-nums">{item.value}</dd>
                <dt className="text-sm text-muted-foreground">{item.label}</dt>
              </div>
            ))}
          </dl>
        )}
      </section>
    </div>
  )
}
