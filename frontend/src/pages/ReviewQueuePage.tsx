import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import type { FixFilter } from '@/lib/fixCounts'
import { formatDate } from '@/lib/format'
import type { ReviewItem } from '@/lib/types'

/**
 * Hari yang perlu keputusan manusia (FR-REV-001): jam tidak lengkap, urutan janggal, hadir saat izin atau hari
 * libur, scan tertahan, atau aturan kerja belum diatur. Daftar dihitung dari rekap yang sama dengan Absensi Harian,
 * jadi sebuah hari keluar sendiri setelah dikoreksi atau scan tertahannya diputuskan.
 */
export function ReviewQueueSection({ filter }: { filter: FixFilter }) {
  const { from, to, branchId = '', categoryId = '' } = filter
  const params = new URLSearchParams({
    from,
    to,
    ...(branchId ? { branchId } : {}),
    ...(categoryId ? { categoryId } : {}),
  })
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/attendance', 'review-queue', from, to, branchId, categoryId],
    queryFn: () => api<ReviewItem[]>(`/api/attendance/review-queue?${params}`),
    enabled: Boolean(from && to),
  })

  return (
    <div className="flex flex-col gap-5">
      {error && <ErrorAlert message={error.message} />}
      <section className="overflow-x-auto rounded-lg border glass-strong">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.length} hari</p>}
        {isPending && <SkeletonLines />}
        {data?.length === 0 && <EmptyState message="Tidak ada data yang perlu ditinjau pada periode ini." />}
        {data && data.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left label-caps text-muted-foreground">
                <th className="px-4 py-2">Tanggal</th>
                <th className="px-3 py-2">Karyawan</th>
                <th className="px-3 py-2">Cabang</th>
                <th className="px-3 py-2">Kategori</th>
                <th className="px-3 py-2">Alasan</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.map((row) => (
                <tr key={`${row.employeeId}-${row.date}`}>
                  <td className="px-4 py-2.5 whitespace-nowrap tabular-nums">{formatDate(row.date)}</td>
                  <td className="px-3 py-2.5 font-semibold">
                    {row.fullName}
                    <span className="block text-xs font-normal text-muted-foreground">{row.employeeNumber}</span>
                  </td>
                  <td className="px-3 py-2.5">{row.branchName}</td>
                  <td className="px-3 py-2.5">{row.categoryName ?? '—'}</td>
                  <td className="px-3 py-2.5">
                    <div className="flex flex-wrap gap-1.5">
                      {row.reasons.map((reason) => (
                        <Badge key={reason} variant="warning">
                          {reason}
                        </Badge>
                      ))}
                    </div>
                  </td>
                  <td className="px-3 py-2.5 whitespace-nowrap">
                    <Button asChild size="sm" variant="outline">
                      <Link
                        to={`/karyawan/${row.employeeId}?tab=absensi&from=${row.date}&to=${row.date}`}
                        aria-label={`Tinjau ${row.fullName}`}
                      >
                        Tinjau
                      </Link>
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
