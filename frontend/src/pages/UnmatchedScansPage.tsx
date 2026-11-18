import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { api } from '@/lib/api'
import { formatDateTime, formatLocalDateTime } from '@/lib/format'
import type { Scan } from '@/lib/types'

/** Scan dengan PIN yang belum dipetakan ke karyawan (K-065). Mengisi PIN di data karyawan menghubungkannya (K-068). */
export function UnmatchedScansSection({ from, to }: { from: string; to: string }) {
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/attendance', 'unmatched', from, to],
    queryFn: () => api<Scan[]>(`/api/attendance/unmatched-scans?from=${from}&to=${to}`),
    enabled: Boolean(from && to),
  })

  return (
    <div className="flex flex-col gap-5">
      <p className="text-sm text-muted-foreground">
        Isi kolom <strong>PIN mesin</strong> pada{' '}
        <Link to="/karyawan" className="text-steel-700 underline underline-offset-4 dark:text-foreground">
          data karyawan
        </Link>{' '}
        yang sesuai; scan di bawah otomatis terhubung ke karyawan tersebut dan tercatat di audit log.
      </p>

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
