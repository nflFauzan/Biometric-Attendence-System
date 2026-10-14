import { useQuery } from '@tanstack/react-query'
import { Bell, X } from 'lucide-react'
import { Popover } from 'radix-ui'
import { useCallback, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { formatLeaveRange } from '@/lib/leave'
import type { PendingLeaveSummary } from '@/lib/types'
import { useDashboardStream, type StreamEvent } from '@/lib/useDashboardStream'

const TOAST_MS = 10_000

interface Toast {
  key: number
  requestId: number
  text: string
}

/**
 * Notifikasi izin/cuti untuk Owner dan HR/Finance (K-081): lonceng berisi pengajuan yang menunggu, dan pop-up
 * dalam hitungan detik saat karyawan mengirim pengajuan baru (lewat SSE).
 */
export function LeaveNotifications() {
  const [open, setOpen] = useState(false)
  const [toasts, setToasts] = useState<Toast[]>([])
  const { data } = useQuery({
    queryKey: ['/api/leave-requests', 'pending'],
    queryFn: () => api<PendingLeaveSummary>('/api/leave-requests/pending'),
  })

  const onEvent = useCallback((event: StreamEvent) => {
    if (event.type === 'LEAVE' && event.action === 'SUBMITTED' && event.requestId) {
      const toast = {
        key: Date.now() + Math.random(),
        requestId: event.requestId,
        text: `${event.employeeName ?? 'Karyawan'} mengajukan ${event.leaveType ?? 'izin'}`,
      }
      setToasts((current) => [...current.slice(-2), toast])
    }
  }, [])
  useDashboardStream(onEvent)

  const dismiss = useCallback((key: number) => setToasts((current) => current.filter((t) => t.key !== key)), [])
  const count = data?.count ?? 0

  return (
    <>
      <Popover.Root open={open} onOpenChange={setOpen}>
        <Popover.Trigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="relative"
            aria-label={count > 0 ? `${count} pengajuan izin menunggu persetujuan` : 'Notifikasi izin'}
          >
            <Bell />
            {count > 0 && (
              <span className="absolute -top-0.5 -right-0.5 grid min-w-5 place-items-center rounded-full bg-danger px-1 text-[11px] leading-5 font-bold text-white tabular-nums">
                {count > 99 ? '99+' : count}
              </span>
            )}
          </Button>
        </Popover.Trigger>
        <Popover.Portal>
          <Popover.Content
            align="end"
            sideOffset={8}
            className="z-50 w-[min(22rem,calc(100vw-2rem))] rounded-lg border glass-strong text-card-foreground"
          >
            <div className="flex items-center justify-between border-b px-4 py-3">
              <p className="font-semibold">Izin menunggu persetujuan</p>
              <span className="text-sm text-muted-foreground tabular-nums">{count}</span>
            </div>
            {count === 0 && <p className="px-4 py-6 text-center text-sm text-muted-foreground">Tidak ada pengajuan yang menunggu.</p>}
            <ul className="max-h-80 divide-y overflow-y-auto">
              {data?.items.map((item) => (
                <li key={item.id}>
                  <Link
                    to={`/izin/${item.id}`}
                    onClick={() => setOpen(false)}
                    className="flex flex-col gap-0.5 px-4 py-3 hover:bg-accent/60"
                  >
                    <span className="font-semibold">{item.employeeName}</span>
                    <span className="text-sm">
                      {item.leaveTypeName} · {formatLeaveRange(item)}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {item.branchName} · diajukan {formatDateTime(item.submittedAt)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            <Link
              to="/izin"
              onClick={() => setOpen(false)}
              className="block border-t px-4 py-3 text-center text-sm font-semibold text-primary hover:bg-accent/60"
            >
              Buka halaman Izin &amp; Cuti
            </Link>
          </Popover.Content>
        </Popover.Portal>
      </Popover.Root>

      {/* Lewat portal: header memakai backdrop-blur yang membuat elemen fixed di dalamnya ikut terkunci ke header. */}
      {createPortal(
        <div
          aria-live="polite"
          className="pointer-events-none fixed right-4 bottom-24 z-50 flex w-[min(22rem,calc(100vw-2rem))] flex-col gap-2 md:bottom-6"
        >
          {toasts.map((toast) => (
            <ToastCard key={toast.key} toast={toast} onDismiss={dismiss} />
          ))}
        </div>,
        document.body,
      )}
    </>
  )
}

function ToastCard({ toast, onDismiss }: { toast: Toast; onDismiss: (key: number) => void }) {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(toast.key), TOAST_MS)
    return () => window.clearTimeout(timer)
  }, [onDismiss, toast.key])
  const close = () => onDismiss(toast.key)

  return (
    <div role="status" className="pointer-events-auto flex items-start gap-3 rounded-lg border border-l-4 border-l-gold-500 glass-strong p-4">
      <Bell className="mt-0.5 size-5 shrink-0 text-primary" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">Pengajuan izin baru</p>
        <p className="text-sm">{toast.text}</p>
        <Link to={`/izin/${toast.requestId}`} onClick={close} className="mt-1 inline-block text-sm font-semibold text-primary underline">
          Tinjau sekarang
        </Link>
      </div>
      <button type="button" onClick={close} aria-label="Tutup notifikasi" className="text-muted-foreground hover:text-foreground">
        <X className="size-4" />
      </button>
    </div>
  )
}
