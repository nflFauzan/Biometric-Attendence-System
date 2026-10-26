import { useQuery } from '@tanstack/react-query'
import { Paperclip } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { fieldClass, selectClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api, ApiError, apiUpload } from '@/lib/api'
import { localDateIso } from '@/lib/format'
import {
  ATTACHMENT_ACCEPT,
  attachmentProblem,
  formatFileSize,
  leaveApiBase,
  LETTER_FIELDS,
  type LetterFieldKey,
  type LeaveMode,
} from '@/lib/leave'
import type { LeaveRequest, LeaveType } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { useEmployeeOptions } from '@/pages/options'

interface LeaveRequestFormProps {
  open: boolean
  mode: LeaveMode
  onClose: () => void
  /** Dipanggil setelah pengajuan (dan lampiran bila ada) tersimpan. */
  onCreated: (request: LeaveRequest) => void
}

/**
 * Form pengajuan izin/cuti hari penuh (K-071). Karyawan mengajukan untuk dirinya sendiri; Owner/HR memilih
 * karyawan (K-073). Surat buatan sendiri bisa langsung dilampirkan (K-078); surat juga bisa ditambah nanti.
 */
export function LeaveRequestForm({ open, mode, onClose, onCreated }: LeaveRequestFormProps) {
  const { data: types } = useQuery({
    queryKey: ['/api/leave-types'],
    queryFn: () => api<LeaveType[]>('/api/leave-types'),
    enabled: open,
  })
  const activeTypes = (types ?? []).filter((t) => t.active)
  const today = localDateIso()
  const [employeeId, setEmployeeId] = useState('')
  const [leaveTypeId, setLeaveTypeId] = useState('')
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(today)
  const [reason, setReason] = useState('')
  const [extra, setExtra] = useState<Partial<Record<LetterFieldKey, string>>>({})
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  const selectedType = activeTypes.find((t) => String(t.id) === leaveTypeId)
  const template = LETTER_FIELDS[selectedType?.letterTemplate ?? 'UMUM']
  const fileProblem = file ? attachmentProblem(file) : null

  const reset = () => {
    setEmployeeId('')
    setLeaveTypeId('')
    setStartDate(today)
    setEndDate(today)
    setReason('')
    setExtra({})
    setFile(null)
    setError(null)
    setFieldErrors({})
  }

  const close = () => {
    reset()
    onClose()
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (fileProblem) {
      setError(fileProblem)
      return
    }
    setSaving(true)
    setError(null)
    setFieldErrors({})
    let created: LeaveRequest | null = null
    try {
      created = await api<LeaveRequest>(leaveApiBase(mode), {
        method: 'POST',
        body: {
          employeeId: mode === 'manager' && employeeId ? Number(employeeId) : null,
          leaveTypeId: leaveTypeId ? Number(leaveTypeId) : null,
          startDate,
          endDate,
          reason,
          // Hanya isian milik templat jenis yang dipilih; kewajibannya divalidasi server saat dikirim.
          ...Object.fromEntries(template.fields.flatMap((f) => (extra[f.key]?.trim() ? [[f.key, extra[f.key]!.trim()]] : []))),
        },
      })
      if (file) {
        await apiUpload(`${leaveApiBase(mode)}/${created.id}/attachments`, file)
      }
      onCreated(created)
      reset()
    } catch (e) {
      if (created) {
        // Pengajuan sudah tersimpan; hanya surat yang gagal. Arahkan ke detail agar bisa diunggah ulang.
        onCreated(created)
        reset()
        return
      }
      if (e instanceof ApiError) {
        setError(e.message)
        setFieldErrors(e.fieldErrors)
      } else {
        setError('Terjadi kesalahan. Coba lagi.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && close()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">
            {mode === 'manager' ? 'Catat izin atas nama karyawan' : 'Ajukan izin / cuti'}
          </DialogTitle>
          <DialogDescription>
            Untuk hari penuh. Setelah dikirim, Owner dan Finance & Accounting langsung mendapat notifikasi untuk menyetujui.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {error && <ErrorAlert message={error} />}

          {mode === 'manager' && <EmployeeSelect value={employeeId} onChange={setEmployeeId} error={fieldErrors.employeeId} />}

          <Field label="Jenis izin / cuti" htmlFor="leaveTypeId" required error={fieldErrors.leaveTypeId}>
            <select
              id="leaveTypeId"
              className={selectClass}
              value={leaveTypeId}
              onChange={(e) => setLeaveTypeId(e.target.value)}
              required
            >
              <option value="">Pilih…</option>
              {activeTypes.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                  {t.requiresDocument ? ' (wajib surat)' : ''}
                </option>
              ))}
            </select>
          </Field>
          {selectedType?.description && <p className="-mt-2 text-xs text-muted-foreground">{selectedType.description}</p>}

          <div className="grid grid-cols-2 gap-3">
            <Field label="Tanggal mulai" htmlFor="startDate" required error={fieldErrors.startDate}>
              <Input
                id="startDate"
                type="date"
                className={fieldClass}
                value={startDate}
                onChange={(e) => {
                  setStartDate(e.target.value)
                  if (endDate < e.target.value) {
                    setEndDate(e.target.value)
                  }
                }}
                required
              />
            </Field>
            <Field label="Tanggal selesai" htmlFor="endDate" required error={fieldErrors.endDate}>
              <Input
                id="endDate"
                type="date"
                className={fieldClass}
                value={endDate}
                min={startDate}
                onChange={(e) => setEndDate(e.target.value)}
                required
              />
            </Field>
          </div>

          <Field label={template.reasonLabel} htmlFor="reason" required error={fieldErrors.reason}>
            <textarea
              id="reason"
              className="min-h-24 rounded-md border border-input bg-card px-3 py-2 text-base md:text-sm"
              value={reason}
              maxLength={1000}
              onChange={(e) => setReason(e.target.value)}
              required
            />
          </Field>

          {template.fields.map((f) => (
            <Field key={f.key} label={f.label} htmlFor={f.key} required={f.required} error={fieldErrors[f.key]}>
              <Input
                id={f.key}
                type={f.inputMode === 'tel' ? 'tel' : 'text'}
                inputMode={f.inputMode}
                className={fieldClass}
                value={extra[f.key] ?? ''}
                onChange={(e) => setExtra((current) => ({ ...current, [f.key]: e.target.value }))}
              />
            </Field>
          ))}

          <Field
            label={selectedType?.requiresDocument ? 'Surat pendukung (wajib sebelum disetujui)' : 'Surat pendukung (opsional)'}
            htmlFor="attachment"
            hint="PDF, JPG, atau PNG, maks. 5 MB. Bisa juga ditambahkan nanti selama pengajuan masih menunggu."
          >
            <label
              htmlFor="attachment"
              className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border border-dashed bg-secondary/50 px-3 py-2 text-sm dark:bg-secondary"
            >
              <Paperclip className="size-4 shrink-0 text-primary" />
              <span className="min-w-0 truncate">
                {file ? `${file.name} · ${formatFileSize(file.size)}` : 'Pilih file dari perangkat…'}
              </span>
            </label>
            <input
              id="attachment"
              type="file"
              accept={ATTACHMENT_ACCEPT}
              className="sr-only"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
            {fileProblem && <p className="text-xs text-danger">{fileProblem}</p>}
          </Field>

          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={close}>
              Batal
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Mengirim…' : mode === 'manager' ? 'Simpan' : 'Kirim pengajuan'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function EmployeeSelect({ value, onChange, error }: { value: string; onChange: (value: string) => void; error?: string }) {
  const options = useEmployeeOptions()
  return (
    <Field label="Karyawan" htmlFor="employeeId" required error={error}>
      <select id="employeeId" className={selectClass} value={value} onChange={(e) => onChange(e.target.value)} required>
        <option value="">Pilih…</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </Field>
  )
}

function Field({
  label,
  htmlFor,
  required,
  hint,
  error,
  children,
}: {
  label: string
  htmlFor: string
  required?: boolean
  hint?: string
  error?: string
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={htmlFor}>
        {label}
        {required && <span className="font-normal text-muted-foreground">(wajib)</span>}
      </Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && <p className="text-xs text-danger">{error}</p>}
    </div>
  )
}
