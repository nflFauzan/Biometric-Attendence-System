import { useQueryClient } from '@tanstack/react-query'
import { MessageCircle, Pencil, RotateCcw, Send, Share2, Trash2, Undo2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { ConfirmDialog } from '@/components/ConfirmDialog'
import { ErrorAlert } from '@/components/feedback'
import { LetterDownloadButton } from '@/components/leave/LetterDownloadButton'
import { Button } from '@/components/ui/button'
import { api, ApiError } from '@/lib/api'
import { isPendingStatus } from '@/lib/leave'
import { shareLetter } from '@/lib/leaveLetter'
import type { LeaveRequest } from '@/lib/types'
import { buildWhatsAppUrl } from '@/lib/whatsapp'

const BASE = '/api/me/leave-requests'

/** Tombol aksi karyawan di detail pengajuan sendiri, menurut status (spec bagian 11). */
export function MyLeaveActions({ request, onWithdraw }: { request: LeaveRequest; onWithdraw: () => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [busy, setBusy] = useState<'submit' | 'delete' | 'share' | 'resubmit' | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const failed = (e: unknown) => setError(e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')

  const submit = async () => {
    setBusy('submit')
    setError(null)
    try {
      const sent = await api<LeaveRequest>(`${BASE}/${request.id}/submit`, { method: 'POST' })
      queryClient.setQueryData([BASE, 'detail', String(request.id)], sent)
      void queryClient.invalidateQueries({ queryKey: [BASE] })
      void queryClient.invalidateQueries({ queryKey: ['/api/dashboard/summary'] })
    } catch (e) {
      failed(e)
    } finally {
      setBusy(null)
    }
  }

  const remove = async () => {
    setBusy('delete')
    setError(null)
    try {
      await api<void>(`${BASE}/${request.id}`, { method: 'DELETE' })
      navigate('/izin-saya', { replace: true })
      queryClient.removeQueries({ queryKey: [BASE, 'detail', String(request.id)] })
      void queryClient.invalidateQueries({ queryKey: [BASE] })
    } catch (e) {
      failed(e)
      setConfirmDelete(false)
    } finally {
      setBusy(null)
    }
  }

  // Ajukan ulang (K-126): server membuat draft baru dari pengajuan yang ditolak, lalu wizard dibuka untuk dicek.
  const resubmit = async () => {
    setBusy('resubmit')
    setError(null)
    try {
      const created = await api<LeaveRequest>(`${BASE}/${request.id}/resubmit`, { method: 'POST' })
      void queryClient.invalidateQueries({ queryKey: [BASE] })
      navigate(`/izin-saya/${created.id}/ubah`)
    } catch (e) {
      failed(e)
    } finally {
      setBusy(null)
    }
  }

  const share = async () => {
    setBusy('share')
    setError(null)
    setNotice(null)
    try {
      const result = await shareLetter('mine', request)
      if (result === 'downloaded') setNotice('Browser ini tidak bisa berbagi file, jadi PDF diunduh. Lampirkan ke WhatsApp secara manual.')
    } catch (e) {
      failed(e)
    } finally {
      setBusy(null)
    }
  }

  const whatsapp = () => window.open(buildWhatsAppUrl(request, window.location.origin), '_blank')

  const draft = request.status === 'DRAFT'
  const pending = isPendingStatus(request.status)

  return (
    <>
      <div className="flex flex-wrap gap-2">
        {draft && (
          <>
            <Button asChild variant="outline" className="min-h-11">
              <Link to={`/izin-saya/${request.id}/ubah`}>
                <Pencil />
                Ubah
              </Link>
            </Button>
            <Button variant="outline" className="min-h-11 text-danger" disabled={busy != null} onClick={() => setConfirmDelete(true)}>
              <Trash2 />
              Hapus
            </Button>
            <Button className="min-h-11" disabled={busy != null} onClick={() => void submit()}>
              <Send />
              {busy === 'submit' ? 'Mengirim…' : 'Kirim'}
            </Button>
          </>
        )}
        {pending && (
          <>
            <Button className="min-h-11" onClick={whatsapp}>
              <MessageCircle />
              Kirim ke WhatsApp
            </Button>
            <Button variant="outline" className="min-h-11" disabled={busy != null} onClick={() => void share()}>
              <Share2 />
              {busy === 'share' ? 'Menyiapkan…' : 'Bagikan PDF'}
            </Button>
            <Button variant="ghost" className="min-h-11" onClick={onWithdraw}>
              <Undo2 />
              Tarik pengajuan
            </Button>
          </>
        )}
        {request.status === 'DITOLAK' && (
          <Button className="min-h-11" disabled={busy != null} onClick={() => void resubmit()}>
            <RotateCcw />
            {busy === 'resubmit' ? 'Menyiapkan…' : 'Ajukan ulang'}
          </Button>
        )}
        <LetterDownloadButton mode="mine" request={request} />
      </div>
      {error && <ErrorAlert message={error} className="w-full" />}
      {notice && <p className="w-full text-sm text-muted-foreground">{notice}</p>}
      <ConfirmDialog
        open={confirmDelete}
        title="Hapus draft?"
        description="Draft dan lampirannya dihapus permanen dan tidak bisa dikembalikan."
        busy={busy === 'delete'}
        onConfirm={() => void remove()}
        onCancel={() => setConfirmDelete(false)}
      />
    </>
  )
}
