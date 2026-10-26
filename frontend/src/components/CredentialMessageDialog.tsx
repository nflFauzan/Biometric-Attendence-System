import { AlertTriangle, Check, Copy, Send } from 'lucide-react'
import { useRef, useState } from 'react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { credentialMessage, type CredentialKind } from '@/lib/credentials'
import { dialogSheetClass } from '@/lib/ui'
import { whatsAppSendUrl } from '@/lib/whatsapp'

export interface CredentialMessage {
  kind: CredentialKind
  name: string
  username: string
  password: string
  phone?: string | null
}

/**
 * Pesan kredensial siap kirim setelah akun dibuat atau password direset (K-133). Password hanya ada di memori
 * halaman: tidak disimpan di browser dan tidak dikirim lagi ke server.
 */
export function CredentialMessageDialog({ message, onClose }: { message: CredentialMessage; onClose: () => void }) {
  const text = credentialMessage({ ...message, origin: window.location.origin })
  const [copied, setCopied] = useState(false)
  const box = useRef<HTMLTextAreaElement>(null)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
    } catch {
      // Clipboard tidak tersedia (mis. bukan HTTPS): pilih teksnya agar bisa disalin manual.
      box.current?.select()
    }
  }

  return (
    <Dialog open onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">
            {message.kind === 'reset' ? 'Password direset' : 'Akun dibuat'}
          </DialogTitle>
          <DialogDescription>Kirim kredensial ini ke {message.name}.</DialogDescription>
        </DialogHeader>
        <p className="flex items-start gap-2 rounded-lg bg-warning-bg px-3 py-3 text-sm text-warning">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Password ini hanya ditampilkan sekali. Untuk mengirim ulang, reset password.
        </p>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="credential-message" className="font-semibold">
            Pesan untuk karyawan
          </Label>
          <textarea
            id="credential-message"
            ref={box}
            readOnly
            rows={12}
            value={text}
            className="w-full resize-none rounded-md border border-input bg-card px-3 py-2 font-mono text-[13px] leading-relaxed"
            onFocus={(e) => e.currentTarget.select()}
          />
          {copied && (
            <p role="status" className="flex items-center gap-1.5 text-xs text-success">
              <Check className="size-3.5" aria-hidden="true" />
              Pesan disalin
            </p>
          )}
        </div>
        <DialogFooter className="mt-2 gap-2">
          <Button type="button" variant="outline" onClick={copy}>
            <Copy />
            Salin pesan
          </Button>
          <Button asChild>
            <a href={whatsAppSendUrl(text, message.phone)} target="_blank" rel="noreferrer">
              <Send />
              Kirim WA
            </a>
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
