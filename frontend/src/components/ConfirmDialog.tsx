import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { dialogSheetClass } from '@/lib/ui'

interface ConfirmDialogProps {
  open: boolean
  title: string
  description: string
  confirmLabel?: string
  busy?: boolean
  /** Teks tombol saat sibuk; bawaan cocok untuk penghapusan. */
  busyLabel?: string
  onConfirm: () => void
  onCancel: () => void
}

/** Konfirmasi tindakan yang sulit dibatalkan, sebagai dialog (bukan window.confirm). */
export function ConfirmDialog({ open, title, description, confirmLabel = 'Ya, hapus', busy = false, busyLabel = 'Menghapus…', onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && !busy && onCancel()}>
      <DialogContent role="alertdialog" className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" disabled={busy} onClick={onCancel}>
            Batal
          </Button>
          <Button type="button" variant="destructive" disabled={busy} onClick={onConfirm}>
            {busy ? busyLabel : confirmLabel}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
