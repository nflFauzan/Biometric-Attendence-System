import { useQuery } from '@tanstack/react-query'
import { ChevronRight, Paperclip, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { LeaveStatusBadge } from '@/components/leave/LeaveStatusBadge'
import { PageHeader } from '@/components/PageHeader'
import { buttonVariants } from '@/components/ui/button'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { formatLeaveRange, isPendingStatus } from '@/lib/leave'
import type { LeaveRequest } from '@/lib/types'
import { cn } from '@/lib/utils'

type Tab = 'berjalan' | 'draft' | 'selesai'

const TABS: { key: Tab; label: string; empty: string; matches: (r: LeaveRequest) => boolean }[] = [
  { key: 'berjalan', label: 'Berjalan', empty: 'Belum ada pengajuan berjalan', matches: (r) => isPendingStatus(r.status) },
  { key: 'draft', label: 'Draft', empty: 'Belum ada draft', matches: (r) => r.status === 'DRAFT' },
  { key: 'selesai', label: 'Selesai', empty: 'Belum ada pengajuan selesai', matches: (r) => !isPendingStatus(r.status) && r.status !== 'DRAFT' },
]

/** Pengajuan izin/cuti milik karyawan yang login (K-073), dikelompokkan per tab. */
export function MyLeavePage() {
  const [tab, setTab] = useState<Tab>('berjalan')
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/me/leave-requests'],
    queryFn: () => api<LeaveRequest[]>('/api/me/leave-requests'),
  })
  const active = TABS.find((t) => t.key === tab)!
  const rows = data?.filter(active.matches)

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Izin & Cuti Saya"
        description="Ajukan izin atau cuti, lampirkan surat bila perlu, lalu pantau statusnya di sini. Surat pengajuan bisa diunduh atau dibagikan sebagai PDF."
        actions={
          <Link to="/izin-saya/baru" className={cn(buttonVariants(), 'min-h-11')}>
            <Plus />
            Ajukan izin
          </Link>
        }
      />

      <div role="tablist" aria-label="Status pengajuan" className="flex gap-1 rounded-full border p-1 glass-strong sm:w-fit">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            role="tab"
            id={`tab-${t.key}`}
            aria-selected={tab === t.key}
            aria-controls="panel-izin"
            onClick={() => setTab(t.key)}
            className={cn(
              'min-h-11 flex-1 rounded-full px-5 font-heading text-sm font-semibold transition-colors outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 sm:flex-none',
              tab === t.key ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {error && <ErrorAlert message={error.message} />}

      <section id="panel-izin" role="tabpanel" aria-labelledby={`tab-${tab}`} className="overflow-hidden rounded-lg border glass-strong">
        {isPending && <SkeletonLines />}
        {rows?.length === 0 && (
          <EmptyState
            message={active.empty}
            action={
              tab === 'berjalan' ? (
                <Link to="/izin-saya/baru" className={cn(buttonVariants({ variant: 'outline' }), 'min-h-11')}>
                  Buat pengajuan baru
                </Link>
              ) : undefined
            }
          />
        )}
        <ul className="divide-y">
          {rows?.map((r) => (
            <li key={r.id}>
              <Link to={`/izin-saya/${r.id}`} className="flex min-h-14 items-center justify-between gap-3 px-4 py-4 hover:bg-accent/60 md:px-5">
                <div className="min-w-0">
                  <p className="font-semibold">
                    {r.leaveTypeName} · {formatLeaveRange(r)}
                  </p>
                  {r.reason && <p className="truncate text-sm text-muted-foreground">{r.reason}</p>}
                  <p className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                    {r.submittedAt ? `Diajukan ${formatDateTime(r.submittedAt)}` : 'Belum dikirim'}
                    {r.attachments.length > 0 && (
                      <span className="inline-flex items-center gap-1">
                        <Paperclip className="size-3.5" />
                        {r.attachments.length}
                      </span>
                    )}
                    {r.requiresDocument && r.attachments.length === 0 && (isPendingStatus(r.status) || r.status === 'DRAFT') && (
                      <span className="text-warning">Surat wajib belum diunggah</span>
                    )}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <LeaveStatusBadge status={r.status} />
                  <ChevronRight className="size-4 text-muted-foreground" />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
