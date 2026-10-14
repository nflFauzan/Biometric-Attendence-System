import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { fieldClass, selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { TeamStatusBadges } from '@/components/TeamStatusBadges'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { addDays, formatDuration, formatTime, localDateIso } from '@/lib/format'
import type { TeamDaily, TeamRow } from '@/lib/types'

const HEADERS = ['No.', 'Nama', 'Cabang', 'Jabatan', 'Status', 'Masuk', 'Pulang', 'Terlambat', 'Lembur']

/** Absensi semua karyawan aktif per tanggal untuk semua role (K-083), tanpa data sensitif. */
export function TeamPage() {
  const [params, setParams] = useSearchParams()
  const today = localDateIso()
  const date = params.get('tanggal') ?? today
  const [branchId, setBranchId] = useState('')
  const [search, setSearch] = useState('')
  const query = new URLSearchParams({ date, ...(branchId ? { branchId } : {}) })
  const { data, error, isPending, isFetching, refetch } = useQuery({
    queryKey: ['/api/team/attendance', date, branchId],
    queryFn: () => api<TeamDaily>(`/api/team/attendance?${query}`),
    refetchInterval: 60_000,
  })

  const setDate = (value: string) => setParams(value ? { tanggal: value } : {}, { replace: true })
  const needle = search.trim().toLowerCase()
  const rows = (data?.rows ?? []).filter(
    (r) => !needle || r.fullName.toLowerCase().includes(needle) || r.employeeNumber.toLowerCase().includes(needle),
  )
  const link = (row: TeamRow) => (
    <Link to={`/rekan-kerja/${row.employeeId}`} className="text-steel-700 underline-offset-4 hover:underline dark:text-foreground">
      {row.fullName}
    </Link>
  )

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Rekan Kerja"
        description="Absensi semua karyawan aktif. Diperbarui otomatis setiap menit."
        actions={
          <Button variant="outline" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw className={isFetching ? 'animate-spin' : undefined} />
            Muat ulang
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="tanggal" className="font-semibold">
            Tanggal
          </Label>
          <div className="flex items-center gap-1">
            <Button variant="ghost" size="icon" aria-label="Hari sebelumnya" onClick={() => setDate(addDays(date, -1))}>
              <ChevronLeft />
            </Button>
            <Input id="tanggal" type="date" className={fieldClass} value={date} max={today} onChange={(e) => setDate(e.target.value)} />
            {/* Hari yang belum terjadi ditolak backend; semua orang akan tampil "Tidak hadir". */}
            <Button
              variant="ghost"
              size="icon"
              aria-label="Hari berikutnya"
              disabled={date >= today}
              onClick={() => setDate(addDays(date, 1))}
            >
              <ChevronRight />
            </Button>
          </div>
        </div>
        <div className="flex min-w-48 flex-col gap-1.5">
          <Label htmlFor="cabang" className="font-semibold">
            Cabang
          </Label>
          <select id="cabang" className={selectClass} value={branchId} onChange={(e) => setBranchId(e.target.value)}>
            <option value="">Semua cabang</option>
            {data?.branches.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex min-w-56 flex-1 flex-col gap-1.5">
          <Label htmlFor="cari" className="font-semibold">
            Cari
          </Label>
          <Input id="cari" className={fieldClass} placeholder="Nama atau No. karyawan" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
      </div>

      {error && <ErrorAlert message={error.message} />}

      <section className="overflow-hidden rounded-lg border glass-strong">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{rows.length} karyawan</p>}
        {isPending && <SkeletonLines />}
        {data && rows.length === 0 && <EmptyState message={needle ? 'Tidak ada yang cocok dengan pencarian.' : 'Belum ada karyawan aktif.'} />}
        {rows.length > 0 && (
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
                  {rows.map((row) => (
                    <TableRow key={row.employeeId} className="hover:bg-accent/60">
                      <TableCell className="px-4 tabular-nums">{row.employeeNumber}</TableCell>
                      <TableCell className="px-4 font-semibold">{link(row)}</TableCell>
                      <TableCell className="px-4">{row.branchName}</TableCell>
                      <TableCell className="px-4">{row.position ?? '—'}</TableCell>
                      <TableCell className="px-4">
                        <TeamStatusBadges day={row.day} />
                      </TableCell>
                      <TableCell className="px-4 font-semibold tabular-nums">{formatTime(row.day.checkIn)}</TableCell>
                      <TableCell className="px-4 font-semibold tabular-nums">{formatTime(row.day.checkOut)}</TableCell>
                      <TableCell className="px-4 tabular-nums">{formatDuration(row.day.lateSeconds)}</TableCell>
                      <TableCell className="px-4 tabular-nums">{formatDuration(row.day.overtimeSeconds)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <ul className="divide-y md:hidden">
              {rows.map((row) => (
                <li key={row.employeeId} className="flex flex-col gap-2 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold break-words">{link(row)}</p>
                      <p className="text-sm text-muted-foreground">
                        {[row.employeeNumber, row.branchName, row.position].filter(Boolean).join(' · ')}
                      </p>
                    </div>
                    <TeamStatusBadges day={row.day} />
                  </div>
                  <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-base">
                    <dt className="text-muted-foreground">Masuk</dt>
                    <dd className="font-semibold tabular-nums">{formatTime(row.day.checkIn)}</dd>
                    <dt className="text-muted-foreground">Pulang</dt>
                    <dd className="font-semibold tabular-nums">{formatTime(row.day.checkOut)}</dd>
                    <dt className="text-muted-foreground">Terlambat</dt>
                    <dd className="tabular-nums">{formatDuration(row.day.lateSeconds)}</dd>
                    <dt className="text-muted-foreground">Lembur</dt>
                    <dd className="tabular-nums">{formatDuration(row.day.overtimeSeconds)}</dd>
                  </dl>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>
    </div>
  )
}
