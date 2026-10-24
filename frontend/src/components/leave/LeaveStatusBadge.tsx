import { Ban, CheckCircle2, Clock, FilePen, XCircle, type LucideIcon } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { LEAVE_STATUS_LABELS, LEAVE_STATUS_TONES } from '@/lib/leave'
import type { LeaveStatus } from '@/lib/types'
import { cn } from '@/lib/utils'

const ICONS: Record<LeaveStatus, LucideIcon> = {
  DRAFT: FilePen,
  PERLU_TINJAU_HR: Clock,
  PERLU_TINJAU_OWNER: Clock,
  DISETUJUI: CheckCircle2,
  DITOLAK: XCircle,
  DIBATALKAN: Ban,
}

/** Lencana status izin: selalu ikon + teks agar tidak hanya mengandalkan warna. */
export function LeaveStatusBadge({ status, size = 'sm' }: { status: LeaveStatus; size?: 'sm' | 'lg' }) {
  const Icon = ICONS[status]
  return (
    <Badge
      variant={LEAVE_STATUS_TONES[status]}
      className={cn(size === 'lg' && 'gap-1.5 px-3 py-1 text-sm [&>svg]:size-4', status === 'DIBATALKAN' && 'line-through')}
    >
      <Icon aria-hidden="true" />
      {LEAVE_STATUS_LABELS[status]}
    </Badge>
  )
}
