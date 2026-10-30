import { useMutation, useQuery } from '@tanstack/react-query'
import { Download, Loader2 } from 'lucide-react'
import { useState } from 'react'

import { DatePicker } from '@/components/DatePickers'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { api, apiBlob } from '@/lib/api'
import { saveBlob } from '@/lib/download'
import { localDateIso } from '@/lib/format'
import type { ReportEmployee, ReportModel, ReportSlug } from '@/lib/types'
import { useBranchOptions, useCategoryOptions, useEmployeeOptions } from '@/pages/options'

const REPORTS: { slug: ReportSlug; label: string }[] = [
  { slug: 'rekap-absensi', label: 'Rekap Absensi' },
  { slug: 'rekap-lembur', label: 'Rekap Lembur Tervalidasi' },
  { slug: 'detail-absensi', label: 'Detail Absensi' },
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
}

/**
 * Laporan absensi: pratinjau ringkas per karyawan dari JSON, dan unduhan Excel/PDF dari model yang sama.
 * Isi lengkap (kisi harian, jam masuk/pulang) ada di berkas unduhan.
 */
export function ReportsPage() {
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()
  const employeeOptions = useEmployeeOptions()
  const [slug, setSlug] = useState<ReportSlug>('rekap-absensi')
  const [to, setTo] = useState(localDateIso())
  const [from, setFrom] = useState(`${localDateIso().slice(0, 8)}01`)
  const [branchId, setBranchId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [employeeId, setEmployeeId] = useState('')
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
  const columns = COLUMNS[slug]

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Laporan"
        description="Pratinjau laporan absensi per karyawan, lalu unduh sebagai Excel atau PDF. Isi lengkap (kisi harian) ada di berkas unduhan."
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex min-w-56 flex-col gap-1.5">
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
        <div className="flex min-w-48 flex-col gap-1.5">
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
    </div>
  )
}
