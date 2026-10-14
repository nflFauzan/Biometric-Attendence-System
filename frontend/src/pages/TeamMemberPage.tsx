import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, ChevronLeft, ChevronRight } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { PageHeader } from '@/components/PageHeader'
import { TeamStatusBadges } from '@/components/TeamStatusBadges'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatDate, formatDuration, formatTime, localDateIso } from '@/lib/format'
import { monthLabel, monthOf, monthRange, shiftMonth } from '@/lib/team'
import type { TeamDay, TeamMember } from '@/lib/types'

const HEADERS = ['Tanggal', 'Status', 'Masuk', 'Pulang', 'Terlambat', 'Lembur']

/** Riwayat absensi satu rekan kerja per bulan (K-083). */
export function TeamMemberPage() {
  const { id } = useParams()
  const [params, setParams] = useSearchParams()
  const today = localDateIso()
  const month = params.get('bulan') ?? monthOf(today)
  const { from, to } = monthRange(month, today)
  const isFuture = from > today
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/team/employees', id, from, to],
    queryFn: () => api<TeamMember>(`/api/team/employees/${id}/attendance?from=${from}&to=${to}`),
    enabled: !isFuture,
  })
  const setMonth = (value: string) => setParams({ bulan: value }, { replace: true })

  const days = data?.days ?? []
  const count = (predicate: (d: TeamDay) => boolean) => days.filter(predicate).length
  const summary = [
    { label: 'Hadir', value: count((d) => d.status === 'HADIR') },
    { label: 'Terlambat', value: count((d) => (d.lateSeconds ?? 0) > 0) },
    { label: 'Tidak hadir', value: count((d) => d.status === 'TIDAK_HADIR') },
    { label: 'Izin', value: count((d) => d.status === 'IZIN') },
    { label: 'Belum lengkap', value: count((d) => d.status === 'TIDAK_LENGKAP') },
  ]
  const newestFirst = [...days].reverse()

  return (
    <div className="flex flex-col gap-5">
      <Link to="/rekan-kerja" className="inline-flex w-fit items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" />
        Rekan Kerja
      </Link>
      <PageHeader
        title={data?.fullName ?? 'Riwayat absensi'}
        description={data ? [data.employeeNumber, data.branchName, data.position].filter(Boolean).join(' · ') : undefined}
      />

      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon" aria-label="Bulan sebelumnya" onClick={() => setMonth(shiftMonth(month, -1))}>
          <ChevronLeft />
        </Button>
        <p className="min-w-40 text-center font-semibold">{monthLabel(month)}</p>
        <Button variant="ghost" size="icon" aria-label="Bulan berikutnya" onClick={() => setMonth(shiftMonth(month, 1))}>
          <ChevronRight />
        </Button>
      </div>

      {error && <ErrorAlert message={error.message} />}
      {isFuture && <EmptyState message="Bulan ini belum berjalan." />}

      {data && (
        <ul aria-label="Ringkasan bulan ini" className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          {summary.map((item) => (
            <li key={item.label} className="rounded-lg border bg-card p-4">
              <p className="label-caps text-muted-foreground">{item.label}</p>
              <p className="font-heading text-2xl font-bold tabular-nums">{item.value}</p>
            </li>
          ))}
        </ul>
      )}

      {!isFuture && (
        <section className="overflow-hidden rounded-lg border bg-card">
          {isPending && <SkeletonLines />}
          {data && days.length === 0 && <EmptyState message="Belum ada data di bulan ini." />}
          {days.length > 0 && (
            <>
              <div className="hidden md:block">
                <Table>
                  <TableHeader>
                    <TableRow className="bg-steel-50 hover:bg-steel-50 dark:bg-secondary dark:hover:bg-secondary">
                      {HEADERS.map((header) => (
                        <TableHead key={header} scope="col" className="label-caps h-11 px-4 text-muted-foreground">
                          {header}
                        </TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {newestFirst.map((day) => (
                      <TableRow key={day.date} className="hover:bg-accent/60">
                        <TableCell className="px-4">{formatDate(day.date)}</TableCell>
                        <TableCell className="px-4">
                          <TeamStatusBadges day={day} />
                        </TableCell>
                        <TableCell className="px-4 font-semibold tabular-nums">{formatTime(day.checkIn)}</TableCell>
                        <TableCell className="px-4 font-semibold tabular-nums">{formatTime(day.checkOut)}</TableCell>
                        <TableCell className="px-4 tabular-nums">{formatDuration(day.lateSeconds)}</TableCell>
                        <TableCell className="px-4 tabular-nums">{formatDuration(day.overtimeSeconds)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <ul className="divide-y md:hidden">
                {newestFirst.map((day) => (
                  <li key={day.date} className="flex flex-col gap-2 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <p className="font-semibold">{formatDate(day.date)}</p>
                      <TeamStatusBadges day={day} />
                    </div>
                    <p className="text-base tabular-nums">
                      {formatTime(day.checkIn)} – {formatTime(day.checkOut)}
                      {(day.lateSeconds ?? 0) > 0 && <span className="text-warning"> · terlambat {formatDuration(day.lateSeconds)}</span>}
                    </p>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}
    </div>
  )
}
