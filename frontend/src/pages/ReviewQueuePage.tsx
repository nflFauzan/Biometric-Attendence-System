import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'

import { DatePicker } from '@/components/DatePickers'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { formatDate, localDateIso } from '@/lib/format'
import type { ReviewItem } from '@/lib/types'
import { useBranchOptions, useCategoryOptions } from '@/pages/options'

/**
 * Hari yang perlu keputusan manusia (FR-REV-001): jam tidak lengkap, urutan janggal, hadir saat izin atau hari
 * libur, scan tertahan, atau aturan kerja belum diatur. Daftar dihitung dari rekap yang sama dengan Absensi Harian,
 * jadi sebuah hari keluar sendiri setelah dikoreksi atau scan tertahannya diputuskan.
 */
export function ReviewQueuePage() {
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()
  const [to, setTo] = useState(localDateIso())
  const [from, setFrom] = useState(`${localDateIso().slice(0, 8)}01`)
  const [branchId, setBranchId] = useState('')
  const [categoryId, setCategoryId] = useState('')
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
      <PageHeader
        title="Perlu Ditinjau"
        description="Hari yang perlu keputusan sebelum dipakai untuk penggajian. Buka rinciannya untuk mengoreksi jam; hari keluar dari daftar setelah masalahnya selesai."
      />

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
        <div className="flex min-w-48 flex-col gap-1.5">
          <Label htmlFor="kategori" className="font-semibold">
            Kategori
          </Label>
          <select id="kategori" className={selectClass} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="">Semua kategori</option>
            {categoryOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

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
                        to={`/absensi/karyawan/${row.employeeId}?from=${row.date}&to=${row.date}`}
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
