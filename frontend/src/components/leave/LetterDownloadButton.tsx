import { Download } from 'lucide-react'
import { useState } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import type { LeaveMode } from '@/lib/leave'
import { saveLetter } from '@/lib/leaveLetter'
import type { LeaveRequest } from '@/lib/types'

/** Unduh PDF surat yang dibuat server. Tersedia untuk pemilik dan Owner/HR. */
export function LetterDownloadButton({ mode, request }: { mode: LeaveMode; request: Pick<LeaveRequest, 'id'> }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const download = async () => {
    setBusy(true)
    setError(null)
    try {
      await saveLetter(mode, request)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Gagal mengunduh surat. Coba lagi.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Button variant="outline" className="min-h-11" disabled={busy} onClick={() => void download()}>
        <Download />
        {busy ? 'Menyiapkan…' : 'Unduh PDF'}
      </Button>
      {error && <ErrorAlert message={error} className="w-full" />}
    </>
  )
}
