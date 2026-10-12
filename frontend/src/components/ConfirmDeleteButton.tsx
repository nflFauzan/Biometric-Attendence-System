import { Trash2 } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'

interface ConfirmDeleteButtonProps {
  /** Nama objek untuk label aksesibel, mis. "hari libur 1 Jan 2027". */
  label: string
  onConfirm: () => Promise<unknown>
  onError: (message: string) => void
}

/** Hapus dua langkah tanpa dialog browser: klik "Hapus", lalu "Ya, hapus" atau "Batal". */
export function ConfirmDeleteButton({ label, onConfirm, onError }: ConfirmDeleteButtonProps) {
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)

  if (!confirming) {
    return (
      <Button variant="ghost" size="sm" className="text-danger" aria-label={`Hapus ${label}`} onClick={() => setConfirming(true)}>
        <Trash2 />
        Hapus
      </Button>
    )
  }

  const confirm = async () => {
    setBusy(true)
    try {
      await onConfirm()
    } catch (e) {
      onError(e instanceof Error ? e.message : 'Gagal menghapus')
      setConfirming(false)
    } finally {
      setBusy(false)
    }
  }

  return (
    <span className="flex items-center gap-1">
      <Button variant="destructive" size="sm" disabled={busy} onClick={confirm}>
        {busy ? 'Menghapus…' : 'Ya, hapus'}
      </Button>
      <Button variant="ghost" size="sm" disabled={busy} onClick={() => setConfirming(false)}>
        Batal
      </Button>
    </span>
  )
}
