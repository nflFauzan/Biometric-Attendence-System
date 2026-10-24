import { useQueryClient } from '@tanstack/react-query'
import { Paperclip } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { selectClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, apiForm } from '@/lib/api'
import { localDateIso } from '@/lib/format'
import { ATTACHMENT_ACCEPT, attachmentProblem, DECISION_METHOD_LABELS, formatFileSize, letterNumber } from '@/lib/leave'
import type { DecisionMethod, LeaveRequest } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'

const METHODS: DecisionMethod[] = ['WHATSAPP', 'LISAN', 'TERTULIS']

function nowLocal(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${localDateIso(d)}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * HR mencatat keputusan Owner yang diberikan di luar web (spec bagian 6). Bukti wajib untuk WhatsApp dan Tertulis.
 * Server memeriksa ulang semua aturan (waktu tidak di masa depan, tidak sebelum tahap HR, dan seterusnya).
 */
export function OwnerDecisionDialog({ request, onClose, onDone }: { request: LeaveRequest; onClose: () => void; onDone: () => void }) {
  const queryClient = useQueryClient()
  const [approved, setApproved] = useState(true)
  const [method, setMethod] = useState<DecisionMethod>('WHATSAPP')
  const [decidedAt, setDecidedAt] = useState(nowLocal)
  const [note, setNote] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const evidenceRequired = method === 'WHATSAPP' || method === 'TERTULIS'
  const fileProblem = file ? attachmentProblem(file) : null

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (evidenceRequired && !file) {
      setError('Bukti wajib diunggah')
      return
    }
    if (fileProblem) {
      setError(fileProblem)
      return
    }
    setSaving(true)
    setError(null)
    try {
      const form = new FormData()
      form.append('decision', new Blob([JSON.stringify({ approved, method, decidedAt, note })], { type: 'application/json' }))
      if (file) {
        form.append('evidence', file)
      }
      await apiForm(`/api/leave-requests/${request.id}/owner-decision`, form)
      void queryClient.invalidateQueries({ queryKey: ['/api/leave-requests'] })
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">Catat keputusan Owner</DialogTitle>
          <DialogDescription>
            Untuk {letterNumber(request.id)}. Isi sesuai jawaban Owner di luar web. Anda tercatat sebagai pencatat, dan Owner mendapat notifikasi.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="flex flex-col gap-4">
          {error && <ErrorAlert message={error} />}

          <fieldset className="flex flex-col gap-1.5">
            <legend className="mb-1.5 text-sm font-medium">Keputusan</legend>
            <div className="grid grid-cols-2 gap-2">
              {([true, false] as const).map((value) => (
                <label
                  key={String(value)}
                  className="flex min-h-11 cursor-pointer items-center gap-2 rounded-md border bg-card px-3 text-sm has-[:checked]:border-steel-600 has-[:checked]:font-semibold"
                >
                  <input type="radio" name="approved" checked={approved === value} onChange={() => setApproved(value)} />
                  {value ? 'Setuju' : 'Tolak'}
                </label>
              ))}
            </div>
          </fieldset>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="method">Cara keputusan</Label>
            <select id="method" className={selectClass} value={method} onChange={(e) => setMethod(e.target.value as DecisionMethod)}>
              {METHODS.map((m) => (
                <option key={m} value={m}>
                  {DECISION_METHOD_LABELS[m]}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="decidedAt">Waktu keputusan Owner</Label>
            <Input id="decidedAt" type="datetime-local" className="h-11" value={decidedAt} onChange={(e) => setDecidedAt(e.target.value)} required />
            <p className="text-xs text-muted-foreground">Tidak boleh di masa depan dan tidak boleh sebelum tahap HR.</p>
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="note">
              Catatan <span className="font-normal text-muted-foreground">(wajib)</span>
            </Label>
            <textarea
              id="note"
              className="min-h-24 rounded-md border border-input bg-card px-3 py-2 text-base md:text-sm"
              value={note}
              maxLength={1000}
              required
              onChange={(e) => setNote(e.target.value)}
            />
          </div>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="evidence">
              Bukti <span className="font-normal text-muted-foreground">{evidenceRequired ? '(wajib)' : '(opsional)'}</span>
            </Label>
            <label
              htmlFor="evidence"
              className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border border-dashed bg-secondary/50 px-3 py-2 text-sm dark:bg-secondary"
            >
              <Paperclip className="size-4 shrink-0 text-primary" />
              <span className="min-w-0 truncate">{file ? `${file.name} · ${formatFileSize(file.size)}` : 'Pilih tangkapan layar atau dokumen…'}</span>
            </label>
            <input id="evidence" type="file" accept={ATTACHMENT_ACCEPT} className="sr-only" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            <p className="text-xs text-muted-foreground">PDF, JPG, atau PNG, maks. 5 MB. Hanya HR dan Owner yang bisa membukanya.</p>
          </div>

          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
              Kembali
            </Button>
            <Button type="submit" className="min-h-11" disabled={saving}>
              {saving ? 'Menyimpan…' : 'Simpan keputusan'}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
