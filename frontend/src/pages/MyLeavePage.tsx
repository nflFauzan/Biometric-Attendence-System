import { useQuery } from '@tanstack/react-query'
import { ChevronRight, Paperclip, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { LeaveRequestForm } from '@/components/LeaveRequestForm'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { formatLeaveRange, LEAVE_STATUS_LABELS, LEAVE_STATUS_TONES } from '@/lib/leave'
import type { LeaveRequest } from '@/lib/types'

/** Pengajuan izin/cuti milik karyawan yang login (K-073). */
export function MyLeavePage() {
  const navigate = useNavigate()
  const [formOpen, setFormOpen] = useState(false)
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/me/leave-requests'],
    queryFn: () => api<LeaveRequest[]>('/api/me/leave-requests'),
  })

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Izin & Cuti Saya"
        description="Ajukan izin atau cuti, lampirkan surat bila perlu, lalu pantau statusnya di sini. Surat pengajuan bisa dicetak atau disimpan sebagai PDF."
        actions={
          <Button onClick={() => setFormOpen(true)}>
            <Plus />
            Ajukan izin
          </Button>
        }
      />

      {error && <ErrorAlert message={error.message} />}

      <section className="overflow-hidden rounded-lg border glass-strong">
        {isPending && <SkeletonLines />}
        {data?.length === 0 && (
          <EmptyState
            message="Belum ada pengajuan."
            action={
              <Button variant="outline" onClick={() => setFormOpen(true)}>
                Ajukan izin pertama
              </Button>
            }
          />
        )}
        <ul className="divide-y">
          {data?.map((r) => (
            <li key={r.id}>
              <Link to={`/izin-saya/${r.id}`} className="flex items-center justify-between gap-3 px-4 py-4 hover:bg-accent/60 md:px-5">
                <div className="min-w-0">
                  <p className="font-semibold">
                    {r.leaveTypeName} · {formatLeaveRange(r)}
                  </p>
                  <p className="truncate text-sm text-muted-foreground">{r.reason}</p>
                  <p className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                    Diajukan {formatDateTime(r.submittedAt)}
                    {r.attachments.length > 0 && (
                      <span className="inline-flex items-center gap-1">
                        <Paperclip className="size-3.5" />
                        {r.attachments.length}
                      </span>
                    )}
                    {r.requiresDocument && r.attachments.length === 0 && r.status === 'MENUNGGU' && (
                      <span className="text-warning">Surat wajib belum diunggah</span>
                    )}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <Badge variant={LEAVE_STATUS_TONES[r.status]}>{LEAVE_STATUS_LABELS[r.status]}</Badge>
                  <ChevronRight className="size-4 text-muted-foreground" />
                </div>
              </Link>
            </li>
          ))}
        </ul>
      </section>

      <LeaveRequestForm
        open={formOpen}
        mode="mine"
        onClose={() => setFormOpen(false)}
        onCreated={(created) => {
          setFormOpen(false)
          navigate(`/izin-saya/${created.id}`)
        }}
      />
    </div>
  )
}
