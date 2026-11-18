import { useQuery, type UseQueryResult } from '@tanstack/react-query'

import { api } from '@/lib/api'
import type { HeldScan, OvertimeRow, ReviewItem, Scan } from '@/lib/types'

/** Periode dan saringan yang dipakai badge tab Absensi dan daftar cek Gaji & Laporan (K-145). */
export interface FixFilter {
  from: string
  to: string
  branchId?: string
  categoryId?: string
  /** Awal rentang khusus scan tertahan; bawaan `from`. Dipakai agar scan lama yang belum ditinjau tidak tersembunyi. */
  heldFrom?: string
}

/** Awal rentang "semua tanggal" untuk scan tertahan yang belum ditinjau (sebelum sistem dipakai). */
export const ALL_DATES_FROM = '2000-01-01'

export interface FixCount {
  /** null bila belum dimuat atau gagal dimuat. */
  count: number | null
  /** true bila server memotong daftar di batasnya, jadi jumlah sebenarnya bisa lebih banyak. */
  capped: boolean
  error: string | null
  isPending: boolean
}

/** Batas jumlah scan tak dikenal yang dikirim server (AttendanceController.UNMATCHED_LIMIT). */
export const UNMATCHED_LIMIT = 500

export function formatCount(count: FixCount): string {
  if (count.count === null) return '…'
  return count.capped ? `${count.count}+` : String(count.count)
}

function toCount<T>(query: UseQueryResult<T[]>, measure: (rows: T[]) => number, limit?: number): FixCount {
  const count = query.data ? measure(query.data) : null
  return {
    count,
    capped: limit !== undefined && query.data !== undefined && query.data.length >= limit,
    error: query.error ? query.error.message : null,
    isPending: query.isPending,
  }
}

/**
 * Jumlah hal yang belum beres pada satu periode, dihitung dari endpoint yang sudah ada. Query key berawalan
 * '/api/attendance' atau '/api/overtime' sehingga ikut dimuat ulang setelah aksi di halaman absensi atau lembur.
 * Scan tertahan dan scan tak dikenal tidak bisa disaring per cabang di server, jadi selalu semua cabang.
 */
export function useFixCounts(filter: FixFilter) {
  const range = new URLSearchParams({ from: filter.from, to: filter.to })
  const scoped = new URLSearchParams(range)
  if (filter.branchId) scoped.set('branchId', filter.branchId)
  if (filter.categoryId) scoped.set('categoryId', filter.categoryId)
  const heldRange = new URLSearchParams({ from: filter.heldFrom ?? filter.from, to: filter.to })
  const enabled = Boolean(filter.from && filter.to)

  const review = useQuery({
    queryKey: ['/api/attendance', 'fix-count', 'review-queue', scoped.toString()],
    queryFn: () => api<ReviewItem[]>(`/api/attendance/review-queue?${scoped}`),
    enabled,
  })
  const overtime = useQuery({
    queryKey: ['/api/overtime', 'fix-count', scoped.toString()],
    queryFn: () => api<OvertimeRow[]>(`/api/overtime?${scoped}`),
    enabled,
  })
  const held = useQuery({
    queryKey: ['/api/attendance', 'fix-count', 'held-scans', heldRange.toString()],
    queryFn: () => api<HeldScan[]>(`/api/attendance/held-scans?${heldRange}&status=BELUM_DITINJAU`),
    enabled,
  })
  const unmatched = useQuery({
    queryKey: ['/api/attendance', 'fix-count', 'unmatched-scans', range.toString()],
    queryFn: () => api<Scan[]>(`/api/attendance/unmatched-scans?${range}`),
    enabled,
  })

  const reviewDays = toCount(review, (rows) => rows.length)
  const overtimePending = toCount(overtime, (rows) => rows.filter((row) => row.status !== 'TERVALIDASI').length)
  const heldPending = toCount(held, (rows) => rows.length)
  const unmatchedCount = toCount(unmatched, (rows) => rows.length, UNMATCHED_LIMIT)
  const parts = [reviewDays, overtimePending, heldPending, unmatchedCount]

  return {
    reviewDays,
    overtimePending,
    heldPending,
    unmatched: unmatchedCount,
    total: parts.reduce((sum, part) => sum + (part.count ?? 0), 0),
    isPending: parts.some((part) => part.isPending),
  }
}

export type FixCounts = ReturnType<typeof useFixCounts>

const parts = (counts: FixCounts) => [counts.reviewDays, counts.overtimePending, counts.heldPending, counts.unmatched]

/** Jumlah yang masih harus dibereskan, dengan "+" bila daftar dari server terpotong. */
export function outstandingLabel(counts: FixCounts) {
  return `${counts.total}${parts(counts).some((c) => c.capped) ? '+' : ''}`
}

/** Masih ada yang belum beres, atau ada bagian yang gagal dimuat sehingga belum bisa dipastikan. */
export function hasOutstanding(counts: FixCounts) {
  return counts.total > 0 || parts(counts).some((c) => c.error)
}
