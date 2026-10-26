import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { DatePicker } from '@/components/DatePickers'
import { selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { TimeCell } from '@/components/TimeCell'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { dayStatus, reviewNotes } from '@/lib/attendance'
import { formatDuration, localDateIso } from '@/lib/format'
import type { DailyAttendance, DailyRow } from '@/lib/types'
import { useDashboardStream } from '@/lib/useDashboardStream'
import { useBranchOptions } from '@/pages/options'

function Notes({ row }: { row: DailyRow }) {
  const notes = row.day.ruleMissing ? [row.day.ruleMissing] : reviewNotes(row.day)
  if (notes.length === 0) {
    return <span className="text-muted-foreground">—</span>
  }
  return (
    <ul className="text-xs text-warning">
      {notes.map((note) => (
        <li key={note}>{note}</li>
      ))}
    </ul>
  )
}

const HEADERS = ['Karyawan', 'Cabang', 'Masuk', 'Pulang', 'Terlambat', 'Jam normal', 'Kandidat lembur', 'Status', 'Catatan']

/** Rekap absensi semua karyawan aktif untuk satu tanggal, dihitung dari scan asli + koreksi (K-058). */
export function AttendancePage() {
  useDashboardStream()
  const branchOptions = useBranchOptions()
  const [date, setDate] = useState(localDateIso())
  const [branchId, setBranchId] = useState('')
  const params = new URLSearchParams({ date, ...(branchId ? { branchId } : {}) })
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/attendance', 'daily', date, branchId],
    queryFn: () => api<DailyAttendance>(`/api/attendance/daily?${params}`),
    enabled: Boolean(date),
  })

  const link = (row: DailyRow) => (
    <Link
      to={`/absensi/karyawan/${row.employeeId}?tanggal=${date}`}
      className="text-steel-700 underline-offset-4 hover:underline dark:text-foreground"
    >
      {row.fullName}
    </Link>
  )

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Absensi Harian"
        description="Jam masuk = scan pertama, jam pulang = scan terakhir. Koreksi dilakukan dari detail karyawan."
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="tanggal" className="font-semibold">
            Tanggal
          </Label>
          <DatePicker id="tanggal" context="Tanggal absensi" value={date} onChange={setDate} />
        </div>
        <div className="flex min-w-48 flex-col gap-1.5">
          <Label htmlFor="cabang" className="font-semibold">
            Cabang
          </Label>
          <select id="cabang" className={selectClass} value={branchId} onChange={(e) => setBranchId(e.target.value)}>
            <option value="">Semua cabang</option>
            {branchOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && <ErrorAlert message={error.message} />}

      <section className="overflow-hidden rounded-lg border glass-strong">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.rows.length} karyawan</p>}
        {isPending && <SkeletonLines />}
        {data?.rows.length === 0 && <EmptyState message="Belum ada karyawan aktif." />}
        {data && data.rows.length > 0 && (
          <>
            <div className="hidden md:block">
              <Table>
                <TableHeader>
                  <TableRow className="glass-head hover:glass-head">
                    {HEADERS.map((header) => (
                      <TableHead key={header} scope="col" className="label-caps h-11 px-4 text-muted-foreground">
                        {header}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.rows.map((row) => {
                    const status = dayStatus(row.day)
                    const result = row.day.result
                    return (
                      <TableRow key={row.employeeId} className="hover:bg-accent/60">
                        <TableCell className="px-4 font-semibold">{link(row)}</TableCell>
                        <TableCell className="px-4">{row.branchName}</TableCell>
                        <TableCell className="px-4">
                          <TimeCell value={result?.checkIn ?? null} />
                        </TableCell>
                        <TableCell className="px-4">
                          <TimeCell value={result?.checkOut ?? null} />
                        </TableCell>
                        <TableCell className="px-4 tabular-nums">{formatDuration(result?.lateSeconds)}</TableCell>
                        <TableCell className="px-4 tabular-nums">{formatDuration(result?.regularWorkSeconds)}</TableCell>
                        <TableCell className="px-4 tabular-nums">{formatDuration(result?.overtimeCandidateSeconds)}</TableCell>
                        <TableCell className="px-4">
                          <Badge variant={status.tone}>{status.label}</Badge>
                        </TableCell>
                        <TableCell className="max-w-64 px-4">
                          <Notes row={row} />
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </div>

            <ul className="divide-y md:hidden">
              {data.rows.map((row) => {
                const status = dayStatus(row.day)
                const result = row.day.result
                return (
                  <li key={row.employeeId} className="flex flex-col gap-2 p-4">
                    <div className="flex items-start justify-between gap-3">
                      <p className="min-w-0 font-semibold break-words">{link(row)}</p>
                      <Badge variant={status.tone}>{status.label}</Badge>
                    </div>
                    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
                      <dt className="text-muted-foreground">Masuk</dt>
                      <dd>
                        <TimeCell value={result?.checkIn ?? null} />
                      </dd>
                      <dt className="text-muted-foreground">Pulang</dt>
                      <dd>
                        <TimeCell value={result?.checkOut ?? null} />
                      </dd>
                      <dt className="text-muted-foreground">Terlambat</dt>
                      <dd>{formatDuration(result?.lateSeconds)}</dd>
                      <dt className="text-muted-foreground">Kandidat lembur</dt>
                      <dd>{formatDuration(result?.overtimeCandidateSeconds)}</dd>
                    </dl>
                    <Notes row={row} />
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </section>
    </div>
  )
}
