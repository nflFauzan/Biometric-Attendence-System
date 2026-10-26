import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { DatePicker } from '@/components/DatePickers'
import { PageHeader } from '@/components/PageHeader'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { addDays, formatDateTime, formatLocalDateTime, localDateIso } from '@/lib/format'
import type { Scan } from '@/lib/types'

/** Scan dengan PIN yang belum dipetakan ke karyawan (K-065). Mengisi PIN di data karyawan menghubungkannya (K-068). */
export function UnmatchedScansPage() {
  const [to, setTo] = useState(localDateIso())
  const [from, setFrom] = useState(addDays(localDateIso(), -6))
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/attendance', 'unmatched', from, to],
    queryFn: () => api<Scan[]>(`/api/attendance/unmatched-scans?from=${from}&to=${to}`),
    enabled: Boolean(from && to),
  })

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Scan Tak Dikenal"
        description="Scan dari PIN yang belum diisi di data karyawan. Scan tetap tersimpan dan belum masuk rekap."
      />
      <p className="text-sm text-muted-foreground">
        Isi kolom <strong>PIN mesin</strong> pada{' '}
        <Link to="/karyawan" className="text-steel-700 underline underline-offset-4 dark:text-foreground">
          data karyawan
        </Link>{' '}
        yang sesuai; scan di bawah otomatis terhubung ke karyawan tersebut dan tercatat di audit log.
      </p>

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="dari" className="font-semibold">
            Dari
          </Label>
          <DatePicker id="dari" context="Dari tanggal" value={from} max={to || undefined} onChange={setFrom} />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="sampai" className="font-semibold">
            Sampai
          </Label>
          <DatePicker id="sampai" context="Sampai tanggal" value={to} min={from || undefined} onChange={setTo} />
        </div>
      </div>

      {error && <ErrorAlert message={error.message} />}
      <section className="overflow-hidden rounded-lg border glass-strong">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.length} scan</p>}
        {isPending && <SkeletonLines />}
        {data?.length === 0 && <EmptyState message="Tidak ada scan dengan PIN tak dikenal pada rentang ini." />}
        <ul className="divide-y">
          {data?.map((scan) => (
            <li key={scan.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
              <div>
                <p className="font-semibold">
                  PIN <span className="font-mono">{scan.devicePin}</span>
                </p>
                <p className="text-xs text-muted-foreground">
                  {scan.deviceName} · diterima {formatDateTime(scan.receivedAt)}
                </p>
              </div>
              <span className="tabular-nums">{formatLocalDateTime(scan.deviceLocalTime)}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
