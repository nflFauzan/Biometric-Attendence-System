import { CheckCircle2, X } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'

import { Button } from '@/components/ui/button'
import { useDashboardStream, type StreamEvent } from '@/lib/useDashboardStream'

const TOAST_MS = 10_000

interface Toast {
  key: number
  text: string
}

/**
 * Pop-up "Mesin … baru saja terhubung" untuk Owner dan HR/Finance saat alat rakitan pertama kali terhubung dengan
 * kunci barunya (K-099), lewat SSE yang sama dengan dashboard.
 */
export function DeviceNotifications() {
  const [toasts, setToasts] = useState<Toast[]>([])

  const onEvent = useCallback((event: StreamEvent) => {
    if (event.type === 'DEVICE_CONNECTED') {
      const toast = { key: Date.now() + Math.random(), text: `Mesin ${event.deviceName ?? ''} baru saja terhubung` }
      setToasts((current) => [...current.slice(-2), toast])
    }
  }, [])
  useDashboardStream(onEvent)

  const dismiss = useCallback((key: number) => setToasts((current) => current.filter((t) => t.key !== key)), [])

  // Lewat portal agar posisi fixed tidak terkunci ke header (lihat LeaveNotifications).
  return createPortal(
    <div
      aria-live="polite"
      className="pointer-events-none fixed top-20 right-4 z-50 flex w-[min(22rem,calc(100vw-2rem))] flex-col gap-2"
    >
      {toasts.map((toast) => (
        <ToastCard key={toast.key} toast={toast} onDismiss={dismiss} />
      ))}
    </div>,
    document.body,
  )
}

function ToastCard({ toast, onDismiss }: { toast: Toast; onDismiss: (key: number) => void }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(toast.key), TOAST_MS)
    return () => window.clearTimeout(timer)
  }, [onDismiss, toast.key])

  return (
    <div role="status" className="pointer-events-auto flex items-start gap-3 rounded-lg border border-l-4 border-l-success glass-strong p-4">
      <CheckCircle2 className="mt-0.5 size-5 shrink-0 text-success" />
      <p className="min-w-0 flex-1 text-sm font-semibold">{toast.text}</p>
      <Button variant="ghost" size="icon" className="-mt-1 -mr-2 size-8" aria-label="Tutup" onClick={() => onDismiss(toast.key)}>
        <X />
      </Button>
    </div>
  )
}
