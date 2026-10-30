import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import type { Tone } from '@/lib/attendance'
import { DatePicker } from '@/components/DatePickers'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { fieldClass, selectClass } from '@/components/FormDialog'
import { AttendanceSubNav } from '@/components/AttendanceSubNav'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { formatDate, formatDateTime, formatDuration, formatTime, localDateIso } from '@/lib/format'
import type { OvertimeRow, OvertimeStatus } from '@/lib/types'
import { useBranchOptions, useCategoryOptions } from '@/pages/options'

/** Sama dengan batas backend (ValidationRequest). */
const MAX_VALIDATE = 200

const STATUS_LABELS: Record<OvertimeStatus, string> = {
  BELUM_DIVALIDASI: 'Belum divalidasi',
  TERVALIDASI: 'Tervalidasi',
  PERLU_VALIDASI_ULANG: 'Perlu validasi ulang',
}

const STATUS_TONES: Record<OvertimeStatus, Tone> = {
  BELUM_DIVALIDASI: 'warning',
  TERVALIDASI: 'success',
  PERLU_VALIDASI_ULANG: 'danger',
}

/** Kandidat di bawah satu menit: menit penuhnya 0, jadi isian 0 berarti setuju seluruhnya (lihat Q-048). */
const SUB_MINUTE = 60

const keyOf = (row: OvertimeRow) => `${row.employeeId}-${row.date}`

/**
 * Menit awal isian: keputusan yang masih berlaku, selain itu menit penuh kandidat. Server memperlakukan menit penuh
 * (floor kandidat/60) sebagai setuju seluruhnya dan menyimpan kandidat apa adanya, termasuk sisa detiknya.
 */
function defaultMinutes(row: OvertimeRow): string {
  if (row.status === 'TERVALIDASI' && row.approvedSeconds !== null) {
    return String(Math.floor(row.approvedSeconds / 60))
  }
  return String(Math.floor(row.candidateSeconds / 60))
}

/**
 * Validasi lembur (FR-REV-003, K-140): kandidat dari rekap harian disetujui seluruhnya, sebagian, atau ditolak
 * (0 menit). Catatan wajib bila dikurangi atau ditolak; tidak ada pembulatan. Bila rekap berubah setelah divalidasi
 * (mis. koreksi jam pulang), status menjadi "Perlu validasi ulang" dan lembur itu belum dihitung.
 */
export function OvertimePage() {
  const queryClient = useQueryClient()
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()
  const [to, setTo] = useState(localDateIso())
  const [from, setFrom] = useState(`${localDateIso().slice(0, 8)}01`)
  const [branchId, setBranchId] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [minutes, setMinutes] = useState<Record<string, string>>({})
  const [notes, setNotes] = useState<Record<string, string>>({})
  const [inputError, setInputError] = useState<string | null>(null)
  const params = new URLSearchParams({
    from,
    to,
    ...(branchId ? { branchId } : {}),
    ...(categoryId ? { categoryId } : {}),
  })
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/overtime', from, to, branchId, categoryId],
    queryFn: () => api<OvertimeRow[]>(`/api/overtime?${params}`),
    enabled: Boolean(from && to),
  })

  // "Pilih semua" hanya mencakup yang masih perlu keputusan; baris tervalidasi bisa dicentang satu per satu.
  const pendingKeys = (data ?? []).filter((row) => row.status !== 'TERVALIDASI').map(keyOf)
  const allSelected = pendingKeys.length > 0 && pendingKeys.every((key) => selected.includes(key))
  const toggleAll = () =>
    setSelected((current) =>
      allSelected
        ? current.filter((key) => !pendingKeys.includes(key))
        : [...current, ...pendingKeys.filter((key) => !current.includes(key))],
    )
  // Pilihan yang tidak lagi ada di daftar (mis. setelah filter berubah) tidak ikut terkirim.
  const chosen = (data ?? []).filter((row) => selected.includes(keyOf(row)))
  const tooMany = chosen.length > MAX_VALIDATE
  const toggle = (key: string) =>
    setSelected((current) => (current.includes(key) ? current.filter((v) => v !== key) : [...current, key]))
  const minutesOf = (row: OvertimeRow) => minutes[keyOf(row)] ?? defaultMinutes(row)
  const noteOf = (row: OvertimeRow) => notes[keyOf(row)] ?? row.note ?? ''

  const mutation = useMutation({
    mutationFn: (rows: OvertimeRow[]) =>
      api<OvertimeRow[]>('/api/overtime/validations', {
        method: 'POST',
        body: {
          items: rows.map((row) => ({
            employeeId: row.employeeId,
            workDate: row.date,
            approvedMinutes: Number(minutesOf(row)),
            expectedCandidateSeconds: row.candidateSeconds,
            note: noteOf(row).trim(),
          })),
        },
      }),
    onSuccess: () => {
      setSelected([])
      setMinutes({})
      setNotes({})
      void queryClient.invalidateQueries({ queryKey: ['/api/overtime'] })
    },
  })

  /** Menit harus bilangan bulat ≥ 0; 1.5 atau kosong tidak dikirim agar tidak terpotong diam-diam. */
  const submit = () => {
    if (chosen.some((row) => !/^\d+$/.test(minutesOf(row).trim()))) {
      setInputError('Lembur disetujui harus bilangan bulat menit')
      return
    }
    setInputError(null)
    mutation.mutate(chosen)
  }

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Validasi Lembur"
        description="Kandidat lembur dari rekap absensi. Setujui seluruhnya, kurangi, atau tolak dengan 0 menit; catatan wajib bila dikurangi atau ditolak. Lembur dihitung apa adanya, tanpa pembulatan."
      />

      <AttendanceSubNav />

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
        <Button disabled={chosen.length === 0 || tooMany || mutation.isPending} onClick={submit}>
          Validasi terpilih
        </Button>
        {tooMany && (
          <p role="status" className="w-full text-sm font-semibold">
            Maksimal {MAX_VALIDATE} lembur sekali validasi. Kurangi pilihan.
          </p>
        )}
      </div>

      {error && <ErrorAlert message={error.message} />}
      {inputError && <ErrorAlert message={inputError} />}
      {mutation.error && <ErrorAlert message={mutation.error.message} />}
      <section className="overflow-x-auto rounded-lg border glass-strong">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.length} hari</p>}
        {isPending && <SkeletonLines />}
        {data?.length === 0 && <EmptyState message="Tidak ada kandidat lembur pada periode ini." />}
        {data && data.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left label-caps text-muted-foreground">
                <th className="w-10 px-4 py-2">
                  <input
                    type="checkbox"
                    className="size-4 accent-steel-600"
                    aria-label="Pilih semua lembur yang belum tervalidasi"
                    checked={allSelected}
                    disabled={pendingKeys.length === 0}
                    onChange={toggleAll}
                  />
                </th>
                <th className="px-3 py-2">Tanggal</th>
                <th className="px-3 py-2">Karyawan</th>
                <th className="px-3 py-2">Jam pulang</th>
                <th className="px-3 py-2">Kandidat</th>
                <th className="px-3 py-2">Disetujui (menit)</th>
                <th className="px-3 py-2">Catatan</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Validator</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.map((row) => {
                const key = keyOf(row)
                const who = `${row.fullName} ${row.date}`
                return (
                  <tr key={key}>
                    <td className="px-4 py-2.5">
                      <input
                        type="checkbox"
                        className="size-4 accent-steel-600"
                        aria-label={`Pilih lembur ${who}`}
                        checked={selected.includes(key)}
                        onChange={() => toggle(key)}
                      />
                    </td>
                    <td className="px-3 py-2.5 whitespace-nowrap tabular-nums">{formatDate(row.date)}</td>
                    <td className="px-3 py-2.5 font-semibold">
                      {row.fullName}
                      <span className="block text-xs font-normal text-muted-foreground">
                        {row.branchName}
                        {row.categoryName ? ` · ${row.categoryName}` : ''}
                      </span>
                    </td>
                    <td className="px-3 py-2.5 tabular-nums">{formatTime(row.checkOut)}</td>
                    <td className="px-3 py-2.5 tabular-nums">
                      {row.candidateSeconds < SUB_MINUTE ? (
                        <>
                          <span className="whitespace-nowrap">&lt; 1 menit</span>
                          {/* Q-048: 0 menit = menit penuh kandidat, jadi kandidat di bawah 1 menit tidak bisa ditolak. */}
                          <span className="block max-w-40 text-xs text-muted-foreground">
                            Kurang dari 1 menit, hanya bisa disetujui seluruhnya.
                          </span>
                        </>
                      ) : (
                        <span className="whitespace-nowrap">{formatDuration(row.candidateSeconds)}</span>
                      )}
                    </td>
                    <td className="px-3 py-2.5">
                      <Input
                        type="number"
                        inputMode="numeric"
                        min={0}
                        max={Math.floor(row.candidateSeconds / 60)}
                        className={`${fieldClass} w-24 tabular-nums`}
                        aria-label={`Menit disetujui ${who}`}
                        value={minutesOf(row)}
                        onChange={(e) => setMinutes((current) => ({ ...current, [key]: e.target.value }))}
                      />
                    </td>
                    <td className="px-3 py-2.5">
                      <Input
                        className={`${fieldClass} min-w-48`}
                        maxLength={1000}
                        aria-label={`Catatan ${who}`}
                        placeholder="Wajib bila dikurangi"
                        value={noteOf(row)}
                        onChange={(e) => setNotes((current) => ({ ...current, [key]: e.target.value }))}
                      />
                    </td>
                    <td className="px-3 py-2.5">
                      <Badge variant={STATUS_TONES[row.status]}>{STATUS_LABELS[row.status]}</Badge>
                      {row.status === 'TERVALIDASI' && row.approvedSeconds === 0 && (
                        <span className="block text-xs font-semibold text-danger">Ditolak</span>
                      )}
                      {row.status === 'PERLU_VALIDASI_ULANG' && row.approvedSeconds !== null && (
                        <span className="block text-xs text-muted-foreground">
                          Sebelumnya {formatDuration(row.approvedSeconds)}
                        </span>
                      )}
                    </td>
                    <td className="px-3 py-2.5">
                      {row.validatedByName ?? '—'}
                      {row.validatedAt && (
                        <span className="block text-xs text-muted-foreground">{formatDateTime(row.validatedAt)}</span>
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </section>
    </div>
  )
}
