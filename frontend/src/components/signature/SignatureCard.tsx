import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImageUp, PenLine, Trash2 } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'

import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorAlert, SkeletonLines } from '@/components/feedback'
import { SignaturePad } from '@/components/signature/SignaturePad'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import {
  SIGNATURE_STATUS_KEY,
  deleteSignature,
  fetchSignatureImage,
  fetchSignatureStatus,
  uploadSignature,
} from '@/lib/signature'

const MAX_BYTES = 1024 * 1024

function messageOf(error: unknown): string {
  if (error instanceof ApiError && error.status === 404) {
    return 'Akun Anda belum terhubung ke data pengguna, jadi tanda tangan belum bisa diatur. Hubungi Finance & Accounting.'
  }
  return error instanceof Error ? error.message : 'Terjadi kesalahan.'
}

/** Kartu tanda tangan di Profil: dipakai pada surat izin yang diajukan karyawan. */
export function SignatureCard() {
  const queryClient = useQueryClient()
  const fileInput = useRef<HTMLInputElement>(null)
  const [drawing, setDrawing] = useState(false)
  const [localError, setLocalError] = useState<string | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  const status = useQuery({ queryKey: SIGNATURE_STATUS_KEY, queryFn: fetchSignatureStatus })
  const exists = status.data?.exists === true
  const image = useQuery({
    queryKey: ['/api/me/signature', status.data?.updatedAt],
    queryFn: fetchSignatureImage,
    enabled: exists,
  })

  useEffect(() => {
    if (!image.data) {
      setPreviewUrl(null)
      return
    }
    const url = URL.createObjectURL(image.data)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [image.data])

  const refresh = () => queryClient.invalidateQueries({ queryKey: SIGNATURE_STATUS_KEY })
  const save = useMutation({
    mutationFn: uploadSignature,
    onSuccess: async () => {
      setDrawing(false)
      setLocalError(null)
      await refresh()
    },
  })
  const remove = useMutation({
    mutationFn: deleteSignature,
    onSuccess: refresh,
    onSettled: () => setConfirmingDelete(false),
  })

  const pickFile = (file: File | undefined) => {
    if (!file) return
    setLocalError(null)
    if (!['image/png', 'image/jpeg'].includes(file.type)) {
      setLocalError('Berkas harus berupa gambar PNG atau JPG.')
      return
    }
    if (file.size > MAX_BYTES) {
      setLocalError('Ukuran gambar maksimal 1 MB.')
      return
    }
    save.mutate(file)
  }

  const failure = status.error ?? save.error ?? remove.error
  // Gagal memuat gambar pratinjau: tampilkan pesannya agar kotak tidak terus bertuliskan "Memuat".
  const error = localError ?? (failure ? messageOf(failure) : null) ?? (image.error ? image.error.message : null)
  const busy = save.isPending || remove.isPending

  return (
    <section aria-labelledby="tanda-tangan" className="flex flex-col gap-4 rounded-xl border p-5 glass-strong">
      <div>
        <h2 id="tanda-tangan" className="font-heading text-lg font-bold text-steel-800 dark:text-foreground">
          Tanda tangan
        </h2>
        <p className="text-sm text-muted-foreground">Dicantumkan pada surat izin yang Anda ajukan atau setujui.</p>
      </div>

      {error && <ErrorAlert message={error} />}
      {status.isPending && <SkeletonLines count={2} />}

      {status.data && !drawing && (
        <>
          {exists ? (
            <div className="flex flex-col gap-1">
              <div className="grid h-[120px] place-items-center rounded-lg border bg-white p-2">
                {image.error ? (
                  <span className="text-sm text-muted-foreground">Gambar tidak bisa ditampilkan.</span>
                ) : previewUrl ? (
                  <img src={previewUrl} alt="Tanda tangan Anda" className="max-h-full max-w-full object-contain" />
                ) : (
                  <span className="text-sm text-muted-foreground">Memuat gambar...</span>
                )}
              </div>
              {status.data.updatedAt && (
                <p className="text-xs text-muted-foreground">Diperbarui {formatDateTime(status.data.updatedAt)}</p>
              )}
            </div>
          ) : (
            <p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
              Belum ada tanda tangan.
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={() => setDrawing(true)} disabled={busy}>
              <PenLine />
              Gambar
            </Button>
            <Button type="button" variant="outline" onClick={() => fileInput.current?.click()} disabled={busy}>
              <ImageUp />
              Unggah gambar
            </Button>
            {exists && (
              <Button type="button" variant="outline" onClick={() => setConfirmingDelete(true)} disabled={busy}>
                <Trash2 />
                Hapus
              </Button>
            )}
          </div>
          <input
            ref={fileInput}
            type="file"
            accept="image/png,image/jpeg"
            className="sr-only"
            aria-label="Berkas tanda tangan"
            onChange={(event) => {
              pickFile(event.target.files?.[0])
              event.target.value = ''
            }}
          />
        </>
      )}

      {drawing && (
        <>
          <SignaturePad onSave={(blob) => save.mutate(blob)} saving={save.isPending} />
          <Button type="button" variant="ghost" className="w-fit" onClick={() => setDrawing(false)} disabled={save.isPending}>
            Batal
          </Button>
        </>
      )}
      <ConfirmDialog
        open={confirmingDelete}
        title="Hapus tanda tangan?"
        description="Surat yang Anda ajukan berikutnya tidak akan memuat tanda tangan sampai Anda mengatur yang baru."
        busy={remove.isPending}
        onConfirm={() => remove.mutate()}
        onCancel={() => setConfirmingDelete(false)}
      />
    </section>
  )
}
