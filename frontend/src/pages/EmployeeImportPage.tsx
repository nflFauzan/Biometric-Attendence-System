import { useMutation } from '@tanstack/react-query'
import { AlertTriangle, CheckCircle2, Download, FileSpreadsheet } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'

import { ErrorAlert } from '@/components/feedback'
import { selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api, apiForm, ApiError } from '@/lib/api'
import { localDateIso } from '@/lib/format'
import { ROLE_LABELS, type ImportCommitResult, type ImportPreview, type ImportPreviewRow, type Role } from '@/lib/types'

const USERNAME_DOMAIN = '@takka.id'

const ROLE_OPTIONS: Role[] = ['EMPLOYEE', 'HR_FINANCE', 'OWNER']

const NO_ACCOUNT = 'Belum dibuat'

/** Masalah yang bisa dicek di browser; sisanya diperiksa ulang server saat simpan. */
function clientErrors(row: ImportPreviewRow): string[] {
  if (row.accountStatus === NO_ACCOUNT) return []
  const username = row.username?.trim() ?? ''
  if (!username) return ['Username wajib diisi']
  if (!username.toLowerCase().endsWith(USERNAME_DOMAIN)) return [`Username harus berakhiran ${USERNAME_DOMAIN}`]
  return []
}

function problems(row: ImportPreviewRow): string[] {
  return [...row.errors, ...clientErrors(row)]
}

function firstOfMonth(): string {
  return localDateIso().slice(0, 8) + '01'
}

function toCsv(result: ImportCommitResult): string {
  const quote = (value: string) => `"${value.replace(/"/g, '""')}"`
  const lines = [['ID', 'Nama', 'Username', 'Password awal', 'Role']]
  result.accounts.forEach((a) => lines.push([a.employeeNumber, a.fullName, a.username, a.initialPassword, ROLE_LABELS[a.role]]))
  return lines.map((line) => line.map(quote).join(',')).join('\r\n')
}

function downloadCsv(result: ImportCommitResult) {
  const url = URL.createObjectURL(new Blob([toCsv(result)], { type: 'text/csv;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = 'akun-karyawan.csv'
  link.click()
  URL.revokeObjectURL(url)
}

const th = 'px-3 py-2 font-semibold'
const td = 'px-3 py-2'

/**
 * Impor karyawan dari template Excel client (K-130), hanya Owner: pilih file → pratinjau dan ubah username/role →
 * simpan sekaligus (semua atau tidak sama sekali) → daftar password awal yang hanya ditampilkan sekali (K-131).
 */
export function EmployeeImportPage() {
  const [file, setFile] = useState<File | null>(null)
  const [startDate, setStartDate] = useState(firstOfMonth)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [rows, setRows] = useState<ImportPreviewRow[]>([])
  const [result, setResult] = useState<ImportCommitResult | null>(null)

  const commitMutation = useMutation({
    mutationFn: () =>
      api<ImportCommitResult>('/api/employee-imports', { method: 'POST', body: { startDate: preview!.startDate, rows } }),
    onSuccess: setResult,
    onError: (error) => {
      const serverRows = error instanceof ApiError ? (error.problem?.rows as ImportPreviewRow[] | undefined) : undefined
      if (serverRows) setRows(serverRows)
    },
  })

  const previewMutation = useMutation({
    mutationFn: () => {
      const form = new FormData()
      form.append('file', file!)
      form.append('startDate', startDate)
      return apiForm<ImportPreview>('/api/employee-imports/preview', form)
    },
    onSuccess: (data) => {
      setPreview(data)
      setRows(data.rows)
      commitMutation.reset()
    },
  })

  /** Ubahan Owner menghapus masalah lama baris itu; server memeriksa ulang saat simpan. */
  const updateRow = (rowNumber: number, patch: Partial<ImportPreviewRow>) =>
    setRows((current) => current.map((r) => (r.rowNumber === rowNumber ? { ...r, ...patch, errors: [] } : r)))

  const problemCount = rows.filter((r) => problems(r).length > 0).length

  const back = (
    <Button asChild variant="outline">
      <Link to="/karyawan">Kembali ke daftar</Link>
    </Button>
  )

  if (result) {
    return (
      <div className="flex flex-col gap-5">
        <PageHeader title="Impor karyawan selesai" actions={back} />
        <p role="status" className="flex items-center gap-2 text-sm font-semibold">
          <CheckCircle2 className="size-5 text-success" aria-hidden="true" />
          {result.employeesCreated} karyawan dan {result.accountsCreated} akun web dibuat.
        </p>
        <p className="flex items-start gap-2 rounded-lg bg-warning-bg px-3 py-3 text-sm text-warning">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Password ini hanya ditampilkan sekali. Bagikan ke masing-masing karyawan; mereka wajib menggantinya saat login
          pertama.
        </p>
        <div>
          <Button variant="outline" onClick={() => downloadCsv(result)}>
            <Download />
            Unduh CSV
          </Button>
        </div>
        <section className="overflow-x-auto rounded-lg border glass-strong">
          <table className="w-full min-w-[640px] text-sm">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className={th}>ID</th>
                <th className={th}>Nama</th>
                <th className={th}>Username</th>
                <th className={th}>Password awal</th>
                <th className={th}>Role</th>
              </tr>
            </thead>
            <tbody>
              {result.accounts.map((a) => (
                <tr key={a.username} className="border-b last:border-0">
                  <td className={`${td} font-mono`}>{a.employeeNumber}</td>
                  <td className={td}>{a.fullName}</td>
                  <td className={`${td} font-mono`}>{a.username}</td>
                  <td className={`${td} font-mono`}>{a.initialPassword}</td>
                  <td className={td}>{ROLE_LABELS[a.role]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Impor karyawan"
        description="Unggah template Data Karyawan dari client. Data karyawan dan akun web dibuat sekaligus setelah pratinjau diperiksa."
        actions={back}
      />

      <form
        className="flex flex-col gap-4 rounded-lg border p-4 glass-strong md:flex-row md:items-start"
        onSubmit={(e) => {
          e.preventDefault()
          if (file) previewMutation.mutate()
        }}
      >
        <div className="flex flex-1 flex-col gap-2">
          <Label htmlFor="import-file" className="font-semibold">
            File Excel (.xlsx)
          </Label>
          <Input
            id="import-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="import-start" className="font-semibold">
            Tanggal mulai dihitung
          </Label>
          <Input id="import-start" type="date" value={startDate} required onChange={(e) => setStartDate(e.target.value)} />
          <p className="text-xs text-muted-foreground">Tanggal masuk semua karyawan impor diisi tanggal ini.</p>
        </div>
        <Button type="submit" className="md:mt-7" disabled={!file || !startDate || previewMutation.isPending}>
          <FileSpreadsheet />
          Pratinjau
        </Button>
      </form>

      {previewMutation.error && <ErrorAlert message={previewMutation.error.message} />}

      {preview && (
        <>
          <p className="text-sm text-muted-foreground">
            {rows.length} baris · {problemCount} bermasalah · tanggal mulai {preview.startDate}
          </p>
          {commitMutation.error && <ErrorAlert message={commitMutation.error.message} />}
          <section className="overflow-x-auto rounded-lg border glass-strong">
            <table className="w-full min-w-[1040px] text-sm">
              <thead>
                <tr className="border-b text-left text-muted-foreground">
                  <th className={th}>Baris</th>
                  <th className={th}>ID</th>
                  <th className={th}>Nama</th>
                  <th className={th}>Cabang</th>
                  <th className={th}>Kategori</th>
                  <th className={th}>Jabatan</th>
                  <th className={th}>Username</th>
                  <th className={th}>Role</th>
                  <th className={th}>Status</th>
                  <th className={th}>Masalah</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const rowProblems = problems(r)
                  const noAccount = r.accountStatus === NO_ACCOUNT
                  return (
                    <tr key={r.rowNumber} className={`border-b last:border-0 ${rowProblems.length > 0 ? 'bg-destructive/5' : ''}`}>
                      <td className={`${td} font-mono text-muted-foreground`}>{r.rowNumber}</td>
                      <td className={`${td} font-mono`}>{r.employeeNumber ?? '—'}</td>
                      <td className={td}>{r.fullName ?? '—'}</td>
                      <td className={td}>{r.branchName ?? '—'}</td>
                      <td className={td}>{r.categoryName ?? '—'}</td>
                      <td className={td}>{r.position ?? '—'}</td>
                      <td className={td}>
                        <Input
                          aria-label={`Username baris ${r.rowNumber}`}
                          className="min-w-[200px] font-mono"
                          value={r.username ?? ''}
                          disabled={noAccount}
                          onChange={(e) => updateRow(r.rowNumber, { username: e.target.value })}
                        />
                      </td>
                      <td className={td}>
                        <select
                          aria-label={`Role baris ${r.rowNumber}`}
                          className={selectClass}
                          value={r.role ?? 'EMPLOYEE'}
                          disabled={noAccount}
                          onChange={(e) => updateRow(r.rowNumber, { role: e.target.value as Role })}
                        >
                          {ROLE_OPTIONS.map((role) => (
                            <option key={role} value={role}>
                              {ROLE_LABELS[role]}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className={td}>
                        <Badge variant={r.accountStatus === 'Aktif' ? 'success' : 'neutral'}>{r.accountStatus ?? '—'}</Badge>
                      </td>
                      <td className={`${td} text-destructive`}>
                        {rowProblems.length > 0 && (
                          <ul className="flex flex-col gap-1">
                            {rowProblems.map((p) => (
                              <li key={p}>{p}</li>
                            ))}
                          </ul>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </section>
          <div>
            <Button disabled={problemCount > 0 || rows.length === 0 || commitMutation.isPending} onClick={() => commitMutation.mutate()}>
              Simpan {rows.length} karyawan
            </Button>
          </div>
        </>
      )}
    </div>
  )
}
