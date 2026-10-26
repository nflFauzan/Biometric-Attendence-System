import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, X } from 'lucide-react'
import { Popover } from 'radix-ui'
import { useCallback, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { Link } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { LeaveStatusBadge } from '@/components/leave/LeaveStatusBadge'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { DECISION_METHOD_LABELS, formatLeaveRange } from '@/lib/leave'
import type { PendingLeaveSummary, RecordedDecision } from '@/lib/types'
import { useDashboardStream, type StreamEvent } from '@/lib/useDashboardStream'

const TOAST_MS = 10_000
const SEEN_KEY_PREFIX = 'absensi.recordedSeen'
const SEEN_MAX = 200

// Per pengguna: penanda dari akun lain di browser yang sama tidak boleh menyembunyikan notifikasi akun ini.
function seenKey(username: string | undefined): string {
  return `${SEEN_KEY_PREFIX}.${username ?? ''}`
}

function readSeen(key: string): Set<number> {
  try {
    const raw = window.localStorage.getItem(key)
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return new Set(Array.isArray(parsed) ? parsed.filter((v): v is number => typeof v === 'number') : [])
  } catch {
    return new Set()
  }
}

function writeSeen(key: string, seen: Set<number>) {
  try {
    window.localStorage.setItem(key, JSON.stringify([...seen].slice(-SEEN_MAX)))
  } catch {
    // penyimpanan tidak tersedia: item dianggap belum dilihat pada kunjungan berikutnya
  }
}

function recordedText(item: RecordedDecision): string {
  return `${item.recorderName} mencatat ${item.approved ? 'persetujuan' : 'penolakan'} Anda (${DECISION_METHOD_LABELS[item.method]})`
}

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
  const { me } = useAuth()
  const isOwner = me?.role === 'OWNER'
  const [open, setOpen] = useState(false)
  const [toasts, setToasts] = useState<Toast[]>([])
  const storageKey = seenKey(me?.username)
  const [seen, setSeen] = useState<Set<number>>(() => readSeen(storageKey))
  const queryClient = useQueryClient()
  const { data } = useQuery({
    queryKey: ['/api/leave-requests', 'pending'],
    queryFn: () => api<PendingLeaveSummary>('/api/leave-requests/pending'),
  })

  const onEvent = useCallback(
    (event: StreamEvent) => {
      if (event.type !== 'LEAVE' || !event.requestId) {
        return
      }
      if (event.action === 'OWNER_DECISION_RECORDED') {
        void queryClient.invalidateQueries({ queryKey: ['/api/leave-requests', 'pending'] })
      }
      const who = event.employeeName ?? 'Karyawan'
      const what = event.leaveType ?? 'izin'
      let text: string | null = null
      if (event.action === 'SUBMITTED') {
        text = `${who} mengajukan ${what}`
      } else if (event.action === 'STAGE_APPROVED' && isOwner) {
        text = `Pengajuan ${what} ${who} menunggu persetujuan Anda`
      } else if (event.action === 'OWNER_DECISION_RECORDED' && isOwner) {
        // Hanya Owner yang perlu tahu keputusannya dicatat orang lain; bila keliru, Owner membatalkan pengajuan.
        text = `HR mencatat keputusan Anda untuk ${what} ${who}`
      }
      if (text) {
        const toast = { key: Date.now() + Math.random(), requestId: event.requestId, text }
        setToasts((current) => [...current.slice(-2), toast])
      }
    },
    [isOwner, queryClient],
  )
  useDashboardStream(onEvent)

  const dismiss = useCallback((key: number) => setToasts((current) => current.filter((t) => t.key !== key)), [])
  const count = data?.count ?? 0
  const recorded = data?.recordedForOwner ?? []
  const unseen = recorded.filter((item) => !seen.has(item.approvalId)).length
  const badge = count + unseen

  // Item dianggap sudah dilihat saat popover dibuka (dan ditutup, bila ada yang masuk selagi terbuka).
  const changeOpen = (next: boolean) => {
    setOpen(next)
    if (recorded.length === 0) {
      return
    }
    const merged = new Set(seen)
    recorded.forEach((item) => merged.add(item.approvalId))
    setSeen(merged)
    writeSeen(storageKey, merged)
  }

  return (
    <>
      <Popover.Root open={open} onOpenChange={changeOpen}>
        <Popover.Trigger asChild>
          <Button
            variant="ghost"
            size="icon"
            className="relative"
            aria-label={
              unseen > 0 ? `${badge} notifikasi izin` : count > 0 ? `${count} pengajuan izin menunggu persetujuan` : 'Notifikasi izin'
            }
          >
            <Bell />
            {badge > 0 && (
              <span className="absolute -top-0.5 -right-0.5 grid min-w-5 place-items-center rounded-full bg-danger px-1 text-[11px] leading-5 font-bold text-white tabular-nums">
                {badge > 99 ? '99+' : badge}
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
                    <span className="flex items-center justify-between gap-2">
                      <span className="font-semibold">{item.employeeName}</span>
                      <LeaveStatusBadge status={item.status} />
                    </span>
                    <span className="text-sm">
                      {item.leaveTypeName} · {formatLeaveRange(item)}
                    </span>
                    <span className="text-xs text-muted-foreground">
                      {item.branchName} · {item.submittedAt ? `diajukan ${formatDateTime(item.submittedAt)}` : 'draft'}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
            {recorded.length > 0 && (
              <>
                <p className="border-t border-b px-4 py-2 text-sm font-semibold">Keputusan Anda yang dicatat Finance & Accounting</p>
                <ul className="max-h-60 divide-y overflow-y-auto">
                  {recorded.map((item) => (
                    <li key={item.approvalId}>
                      <Link
                        to={`/izin/${item.requestId}`}
                        onClick={() => setOpen(false)}
                        className="flex flex-col gap-0.5 px-4 py-3 hover:bg-accent/60"
                      >
                        <span className="flex items-start justify-between gap-2">
                          <span className="text-sm font-semibold">{recordedText(item)}</span>
                          <LeaveStatusBadge status={item.status} />
                        </span>
                        <span className="text-sm">
                          {item.employeeName} · {item.leaveTypeName ?? 'Izin'}
                        </span>
                        <span className="text-xs text-muted-foreground">{formatDateTime(item.recordedAt)}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </>
            )}
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
        <p className="text-sm font-semibold">Pembaruan pengajuan izin</p>
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
