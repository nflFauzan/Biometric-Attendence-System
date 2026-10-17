import { CheckCircle2, Copy, KeyRound } from 'lucide-react'
import { useCallback, useRef, useState } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { fieldClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { buildSetupCode, defaultServerUrl, normalizeServerUrl } from '@/lib/setupCode'
import type { Device, IssuedKey } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { useDashboardStream, type StreamEvent } from '@/lib/useDashboardStream'

interface SetupCodeDialogProps {
  device: Device
  issuedKey: IssuedKey
  onClose: () => void
}

/**
 * Kode pengaturan alat rakitan yang hanya tampil sekali (K-088). Kode disusun di browser dari kunci dan alamat
 * server yang dijangkau mesin, lalu ditempel di halaman pengaturan mesin lewat HP (docs/protokol-alat-rakitan.md §6).
 */
export function SetupCodeDialog({ device, issuedKey, onClose }: SetupCodeDialogProps) {
  const [server, setServer] = useState(() => defaultServerUrl(issuedKey))
  const [copied, setCopied] = useState(false)
  const [manualCopy, setManualCopy] = useState(false)
  const [connected, setConnected] = useState(false)
  const codeRef = useRef<HTMLTextAreaElement>(null)

  const onEvent = useCallback(
    (event: StreamEvent) => {
      if (event.type === 'DEVICE_CONNECTED' && event.deviceId === device.id) {
        setConnected(true)
      }
    },
    [device.id],
  )
  useDashboardStream(onEvent)

  const normalized = normalizeServerUrl(server)
  const code = normalized ? buildSetupCode(normalized, device.serialNumber, issuedKey.deviceKey) : null

  const copy = async () => {
    if (!code) {
      return
    }
    try {
      // Tidak tersedia jika web dibuka lewat http:// ke IP LAN (bukan secure context).
      if (!navigator.clipboard) {
        throw new Error('clipboard tidak tersedia')
      }
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setManualCopy(false)
    } catch {
      // Kunci hanya tampil sekali: jangan pernah menampilkan "Tersalin" jika kode belum benar-benar tersalin.
      setCopied(false)
      setManualCopy(true)
      codeRef.current?.focus()
      codeRef.current?.select()
    }
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">Kode pengaturan {device.name}</DialogTitle>
          <DialogDescription>
            Nyalakan mesin, sambungkan HP ke hotspot <span className="font-mono">BAS-Setup-…</span> yang tampil di layar
            mesin, lalu tempel kode ini di halaman pengaturan.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <p className="flex items-center gap-2 text-sm">
            <KeyRound className="size-4 text-muted-foreground" aria-hidden="true" />
            ID mesin <span className="font-mono font-semibold">{device.serialNumber}</span>
          </p>

          <div className="flex flex-col gap-1.5">
            <Label htmlFor="setup-server" className="font-semibold">
              Alamat server
            </Label>
            <Input
              id="setup-server"
              className={fieldClass}
              value={server}
              onChange={(e) => {
                setServer(e.target.value)
                setCopied(false)
                setManualCopy(false)
              }}
            />
            <p className="text-xs text-muted-foreground">
              Alamat yang dijangkau mesin, mis. IP laptop di hotspot HP + port 8888.
            </p>
          </div>

          {code ? (
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="setup-code" className="font-semibold">
                Kode pengaturan
              </Label>
              <textarea
                ref={codeRef}
                id="setup-code"
                readOnly
                rows={4}
                value={code}
                onFocus={(e) => e.currentTarget.select()}
                className="w-full resize-none rounded-md border border-input bg-card px-3 py-2 font-mono text-xs break-all"
              />
              <Button type="button" variant="secondary" onClick={copy} className="self-start">
                <Copy />
                {copied ? 'Tersalin' : 'Salin kode'}
              </Button>
              {manualCopy && (
                <p role="status" className="text-sm text-warning">
                  Tidak bisa menyalin otomatis. Kode sudah dipilih: tekan Ctrl+C, atau tekan lama lalu Salin di HP.
                </p>
              )}
            </div>
          ) : (
            <ErrorAlert message="Alamat harus diawali http:// atau https://" />
          )}

          <p className="rounded-md border border-warning/30 bg-warning-bg p-3 text-sm text-warning">
            Kode ini hanya tampil sekali. Jika hilang, buat kode baru.
          </p>

          {connected && (
            <p role="status" className="flex items-center gap-2 text-sm font-semibold text-success">
              <CheckCircle2 className="size-4" aria-hidden="true" />
              Mesin {device.name} terhubung
            </p>
          )}
        </div>

        <DialogFooter className="mt-2">
          <Button type="button" onClick={onClose}>
            Selesai
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
