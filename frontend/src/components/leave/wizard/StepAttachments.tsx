import { useQueryClient } from '@tanstack/react-query'
import { Paperclip, Upload } from 'lucide-react'
import { useRef, useState } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { SignatureCard } from '@/components/signature/SignatureCard'
import { Button } from '@/components/ui/button'
import { api, ApiError, apiUpload } from '@/lib/api'
import { ATTACHMENT_ACCEPT, attachmentProblem, formatFileSize, MAX_ATTACHMENTS } from '@/lib/leave'
import type { LeaveRequest } from '@/lib/types'

const BASE = '/api/me/leave-requests'

/** Langkah 3: lampiran (boleh foto dari kamera HP) dan tanda tangan yang dipakai di surat. */
export function StepAttachments({ request }: { request: LeaveRequest }) {
  const queryClient = useQueryClient()
  const input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const full = request.attachments.length >= MAX_ATTACHMENTS

  const upload = async (file: File | undefined) => {
    if (!file) return
    const problem = attachmentProblem(file)
    if (problem) {
      setError(problem)
      return
    }
    setUploading(true)
    setError(null)
    try {
      await apiUpload(`${BASE}/${request.id}/attachments`, file)
      const fresh = await api<LeaveRequest>(`${BASE}/${request.id}`)
      queryClient.setQueryData([BASE, 'detail', String(request.id)], fresh)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Gagal mengunggah. Coba lagi.')
    } finally {
      setUploading(false)
      if (input.current) input.current.value = ''
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="wizard-lampiran" className="flex flex-col gap-3">
        <div>
          <h2 id="wizard-lampiran" className="font-heading text-lg font-bold text-steel-800 dark:text-foreground">
            Lampiran
          </h2>
          <p className="text-sm text-muted-foreground">
            {request.requiresDocument
              ? `Jenis ${request.leaveTypeName} wajib surat pendukung.`
              : 'Surat pendukung bersifat opsional untuk jenis ini.'}{' '}
            PDF, JPG, atau PNG, maks. 5 MB, maks. {MAX_ATTACHMENTS} file.
          </p>
        </div>
        {error && <ErrorAlert message={error} />}
        {request.attachments.length === 0 ? (
          <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">Belum ada lampiran.</p>
        ) : (
          <ul className="divide-y rounded-lg border glass-strong">
            {request.attachments.map((a) => (
              <li key={a.id} className="flex items-center gap-3 px-4 py-3">
                <Paperclip className="size-4 shrink-0 text-primary" />
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold">{a.originalName}</p>
                  <p className="text-xs text-muted-foreground">{formatFileSize(a.sizeBytes)}</p>
                </div>
              </li>
            ))}
          </ul>
        )}
        <Button type="button" variant="outline" className="w-fit" disabled={uploading || full} onClick={() => input.current?.click()}>
          <Upload />
          {uploading ? 'Mengunggah…' : 'Unggah atau foto surat'}
        </Button>
        <input
          ref={input}
          type="file"
          accept={ATTACHMENT_ACCEPT}
          className="sr-only"
          aria-label="Pilih file lampiran"
          onChange={(e) => void upload(e.target.files?.[0])}
        />
      </section>

      <div className="flex flex-col gap-2">
        <p className="text-sm text-muted-foreground">
          Tanda tangan dipakai di surat. Atur di sini, atau lewati bila sudah tersimpan di profil.
        </p>
        <SignatureCard />
      </div>
    </div>
  )
}
