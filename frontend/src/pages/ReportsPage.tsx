import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Download, Loader2, Sparkles } from 'lucide-react'
import { useState } from 'react'

import { DatePicker } from '@/components/DatePickers'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { api, apiBlob } from '@/lib/api'
import { saveBlob } from '@/lib/download'
import { formatRupiah, localDateIso } from '@/lib/format'
import type { ReportEmployee, ReportModel, ReportSlug } from '@/lib/types'
import { useBranchOptions, useCategoryOptions, useEmployeeOptions } from '@/pages/options'

const REPORTS: { slug: ReportSlug; label: string }[] = [
  { slug: 'rekap-absensi', label: 'Rekap Absensi' },
  { slug: 'rekap-lembur', label: 'Rekap Lembur Tervalidasi' },
  { slug: 'detail-absensi', label: 'Detail Absensi' },
  { slug: 'upah-mingguan', label: 'Upah Mingguan (Supir & Kenek) [AS-1 & AS-2]' },
  { slug: 'gaji-bulanan', label: 'Gaji Bulanan (Staf Kantor) [AS-5, AS-6 & AS-7]' },
]

/** Jam desimal 2 angka (7,50), sama dengan tampilan di file Excel/PDF. */
const hours = (value: number) => value.toLocaleString('id-ID', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

interface Column {
  header: string
  cell: (employee: ReportEmployee) => string | number
  numeric?: boolean
}

const IDENTITY: Column[] = [
  { header: 'No. Karyawan', cell: (e) => e.employeeNumber },
  { header: 'Nama', cell: (e) => e.fullName },
  { header: 'Cabang', cell: (e) => e.branchName },
  { header: 'Kategori', cell: (e) => e.categoryName ?? '—' },
]

const COLUMNS: Record<ReportSlug, Column[]> = {
  'rekap-absensi': [
    ...IDENTITY,
    { header: 'Hari lengkap', cell: (e) => e.totals.countingDays, numeric: true },
    { header: 'Izin', cell: (e) => e.totals.leaveDays, numeric: true },
    { header: 'Tidak hadir', cell: (e) => e.totals.absentDays, numeric: true },
    { header: 'Jam kerja normal', cell: (e) => hours(e.totals.countingHours), numeric: true },
    { header: 'Lembur tervalidasi', cell: (e) => hours(e.totals.validatedOvertimeHours), numeric: true },
  ],
  'rekap-lembur': [
    ...IDENTITY,
    {
      header: 'Hari berlembur',
      cell: (e) => e.days.filter((d) => d.validatedOvertimeHours > 0).length,
      numeric: true,
    },
    { header: 'Lembur tervalidasi (jam)', cell: (e) => hours(e.totals.validatedOvertimeHours), numeric: true },
  ],
  'detail-absensi': [
    ...IDENTITY,
    { header: 'Hari ada scan', cell: (e) => e.days.filter((d) => d.scanTimes.length > 0).length, numeric: true },
    { header: 'Hari lengkap', cell: (e) => e.totals.countingDays, numeric: true },
    { header: 'Jam kerja normal', cell: (e) => hours(e.totals.countingHours), numeric: true },
  ],
  'upah-mingguan': [
    ...IDENTITY,
    { header: 'Hari', cell: (e) => e.totals.countingDays, numeric: true },
    { header: 'Upah Harian', cell: (e) => formatRupiah(e.financial?.dailyWage), numeric: true },
    { header: 'Total Upah', cell: (e) => formatRupiah(e.financial?.grossDailyWage), numeric: true },
    { header: 'Lembur', cell: (e) => formatRupiah(e.financial?.overtimeWage), numeric: true },
    { header: 'Kasbon', cell: (e) => formatRupiah(e.financial?.loanDeduction), numeric: true },
    { header: 'Sisa Upah', cell: (e) => formatRupiah(e.financial?.netWage), numeric: true },
  ],
  'gaji-bulanan': [
    ...IDENTITY,
    { header: 'Hari', cell: (e) => e.totals.countingDays, numeric: true },
    { header: 'Wages', cell: (e) => formatRupiah(e.financial?.dailyWage), numeric: true },
    { header: 'Onsite', cell: (e) => formatRupiah(e.financial?.onsiteWage), numeric: true },
    { header: 'Gaji Pokok', cell: (e) => formatRupiah(e.financial?.baseSalary), numeric: true },
    { header: 'Lembur', cell: (e) => formatRupiah(e.financial?.overtimeWage), numeric: true },
    { header: 'Piutang', cell: (e) => formatRupiah(e.financial?.loanDeduction), numeric: true },
    { header: 'Total Gaji', cell: (e) => formatRupiah(e.financial?.netWage), numeric: true },
  ],
}

/**
 * Laporan absensi & penggajian: pratinjau ringkas per karyawan dari JSON, dan unduhan Excel/PDF multi-sheet
 * identik dokumen fisik klien (AS-1 sampai AS-7). Dilengkapi aksi Tutup Buku Pemotongan Kasbon.
 */
export function ReportsPage() {
  const queryClient = useQueryClient()
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()
  const employeeOptions = useEmployeeOptions()
  const [slug, setSlug] = useState<ReportSlug>('rekap-absensi')
  const [to, setTo] = useState(localDateIso())
  const [from, setFrom] = useState(`${localDateIso().slice(0, 8)}01`)
  const [branchId, setBranchId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [employeeId, setEmployeeId] = useState('')
  const [deductionFeedback, setDeductionFeedback] = useState<string | null>(null)

  const query = new URLSearchParams({
    from,
    to,
    ...(branchId ? { branchId } : {}),
    ...(categoryId ? { categoryId } : {}),
    ...(employeeId ? { employeeId } : {}),
  }).toString()

  const { data, error, isPending } = useQuery({
    queryKey: ['/api/reports', slug, query],
    queryFn: () => api<ReportModel>(`/api/reports/${slug}?${query}`),
    enabled: Boolean(from && to),
  })

  const download = useMutation({
    mutationFn: async (format: 'xlsx' | 'pdf') => {
      const blob = await apiBlob(`/api/reports/${slug}.${format}?${query}`)
      saveBlob(blob, `${slug}_${from}_${to}.${format}`)
    },
  })

  const applyDeductions = useMutation({
    mutationFn: async () => {
      if (!data) return
      const deductions = data.employees
        .filter((e) => (e.financial?.loanDeduction ?? 0) > 0)
        .map((e) => ({
          employeeId: e.employeeId ?? 0,
          amount: e.financial?.loanDeduction ?? 0,
        }))

      return api<{ processedCount: number; totalDeducted: number; message: string }>(
        '/api/loans/apply-period-deductions',
        {
          method: 'POST',
          body: {
            periodLabel: data.periodLabel,
            reportType: data.type,
            deductions,
          },
        },
      )
    },
    onSuccess: (res) => {
      if (res) {
        setDeductionFeedback(
          `Sukses: Potongan kasbon sebesar ${formatRupiah(res.totalDeducted)} berhasil diterapkan untuk ${res.processedCount} karyawan.`,
        )
        queryClient.invalidateQueries({ queryKey: ['/api/reports'] })
        queryClient.invalidateQueries({ queryKey: ['/api/loans'] })
      }
    },
    onError: (err: any) => {
      setDeductionFeedback(`Gagal: ${err.message || 'Terjadi kesalahan saat memotong kasbon'}`)
    },
  })

  function setWeeklyCycle(offsetWeeks = 0) {
    const today = new Date()
    const day = today.getDay()
    const diffToSat = (day + 1) % 7
    const sat = new Date(today)
    sat.setDate(today.getDate() - diffToSat + offsetWeeks * 7)
    const fri = new Date(sat)
    fri.setDate(sat.getDate() + 6)
    setFrom(localDateIso(sat))
    setTo(localDateIso(fri))
  }

  function setMonthlyCycle(offsetMonths = 0) {
    const today = new Date()
    const y = today.getFullYear()
    const m = today.getMonth() + offsetMonths
    const first = new Date(y, m, 1)
    const last = new Date(y, m + 1, 0)
    setFrom(localDateIso(first))
    setTo(localDateIso(last))
  }

  const columns = COLUMNS[slug]
  const isFinancialReport = slug === 'upah-mingguan' || slug === 'gaji-bulanan'
  const totalLoanDeduction =
    data?.employees.reduce((sum, e) => sum + (e.financial?.loanDeduction ?? 0), 0) ?? 0

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Laporan"
        description="Pratinjau laporan absensi dan kalkulasi upah/gaji per karyawan, unduh dokumen Excel/PDF fisik (AS-1 s/d AS-7), dan tutup buku potongan kasbon."
      />

      {/* Shortcut Siklus Periode */}
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-semibold text-muted-foreground">Filter Cepat:</span>
        <Button size="sm" variant="outline" onClick={() => setWeeklyCycle(0)} className="h-7 text-xs">
          Sabtu–Jumat Pekan Ini
        </Button>
        <Button size="sm" variant="outline" onClick={() => setWeeklyCycle(-1)} className="h-7 text-xs">
          Pekan Lalu
        </Button>
        <Button size="sm" variant="outline" onClick={() => setMonthlyCycle(0)} className="h-7 text-xs">
          Bulan Ini
        </Button>
        <Button size="sm" variant="outline" onClick={() => setMonthlyCycle(-1)} className="h-7 text-xs">
          Bulan Lalu
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex min-w-64 flex-col gap-1.5">
          <Label htmlFor="jenis" className="font-semibold">
            Jenis laporan
          </Label>
          <select id="jenis" className={selectClass} value={slug} onChange={(e) => setSlug(e.target.value as ReportSlug)}>
            {REPORTS.map((report) => (
              <option key={report.slug} value={report.slug}>
                {report.label}
              </option>
            ))}
          </select>
        </div>
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
        <div className="flex min-w-44 flex-col gap-1.5">
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
        <div className="flex min-w-44 flex-col gap-1.5">
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
        <div className="flex min-w-44 flex-col gap-1.5">
          <Label htmlFor="karyawan" className="font-semibold">
            Karyawan
          </Label>
          <select id="karyawan" className={selectClass} value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
            <option value="">Semua karyawan</option>
            {employeeOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-2">
          <Button disabled={download.isPending || !from || !to} onClick={() => download.mutate('xlsx')}>
            {download.isPending && download.variables === 'xlsx' ? (
              <Loader2 className="animate-spin" aria-hidden />
            ) : (
              <Download aria-hidden />
            )}
            Unduh Excel
          </Button>
          <Button variant="outline" disabled={download.isPending || !from || !to} onClick={() => download.mutate('pdf')}>
            {download.isPending && download.variables === 'pdf' ? (
              <Loader2 className="animate-spin" aria-hidden />
            ) : (
              <Download aria-hidden />
            )}
            Unduh PDF
          </Button>
        </div>
      </div>

      {/* Aksi Khusus Finansial: Tutup Buku Potongan Kasbon */}
      {isFinancialReport && data && data.employees.length > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-gold-300 bg-gold-50/50 p-4 dark:border-gold-900/50 dark:bg-gold-950/20">
          <div>
            <h4 className="text-sm font-semibold text-foreground">Tutup Buku & Pemotongan Kasbon</h4>
            <p className="text-xs text-muted-foreground">
              Proyeksi potongan kasbon periode ini:{' '}
              <strong className="text-gold-700 dark:text-gold-400 font-semibold">
                {formatRupiah(totalLoanDeduction)}
              </strong>
              . Klik tombol untuk memotong saldo kasbon karyawan secara resmi.
            </p>
          </div>
          <Button
            size="sm"
            onClick={() => applyDeductions.mutate()}
            disabled={applyDeductions.isPending || totalLoanDeduction === 0}
            className="gap-2 bg-gold-600 text-white hover:bg-gold-700 dark:bg-gold-500 dark:hover:bg-gold-600"
          >
            {applyDeductions.isPending ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <CheckCircle2 className="size-4" />
            )}
            Terapkan Potongan Kasbon
          </Button>
        </div>
      )}

      {deductionFeedback && (
        <div className="rounded-md bg-emerald-50 p-3 text-xs font-medium text-emerald-800 border border-emerald-200 dark:bg-emerald-950/30 dark:border-emerald-800 dark:text-emerald-300">
          {deductionFeedback}
        </div>
      )}

      {error && <ErrorAlert message={error.message} />}
      {download.error && <ErrorAlert message={download.error.message} />}

      <section className="overflow-x-auto rounded-lg border glass-strong">
        {data && (
          <div className="flex flex-wrap items-center justify-between border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">
            <span>
              <strong className="font-semibold text-foreground">{data.title}</strong> · {data.periodLabel}
            </span>
            <span>{data.employees.length} karyawan</span>
          </div>
        )}
        {isPending && !error && <SkeletonLines />}
        {data?.employees.length === 0 && <EmptyState message="Tidak ada data pada periode ini." />}
        {data && data.employees.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left label-caps text-muted-foreground">
                {columns.map((column) => (
                  <th key={column.header} className={`px-3 py-2 ${column.numeric ? 'text-right' : ''}`}>
                    {column.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.employees.map((employee) => (
                <tr key={employee.employeeNumber} className="hover:bg-muted/40 transition-colors">
                  {columns.map((column) => (
                    <td key={column.header} className={`px-3 py-2.5 ${column.numeric ? 'text-right tabular-nums' : ''}`}>
                      {column.cell(employee)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* Ringkasan Cabang (Format AS-1) */}
      {data?.branchSummaries && data.branchSummaries.length > 0 && (
        <div className="rounded-lg border bg-card p-4">
          <h4 className="text-sm font-semibold mb-3">Ringkasan Upah per Cabang</h4>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {data.branchSummaries.map((b) => (
              <div key={b.branchName} className="rounded-md border p-3 bg-muted/30">
                <p className="text-xs text-muted-foreground">{b.branchName}</p>
                <p className="text-lg font-bold text-foreground">{formatRupiah(b.totalAmount)}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
