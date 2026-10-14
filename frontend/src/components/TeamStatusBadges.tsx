import { Badge } from '@/components/ui/badge'
import { TEAM_STATUS } from '@/lib/team'
import type { TeamDay } from '@/lib/types'

/** Status rekan kerja + penanda terlambat. Selalu berupa teks, tidak hanya warna (design-system §1). */
export function TeamStatusBadges({ day }: { day: TeamDay }) {
  const status = TEAM_STATUS[day.status]
  return (
    <span className="inline-flex flex-wrap gap-1.5">
      <Badge variant={status.tone}>{status.label}</Badge>
      {(day.lateSeconds ?? 0) > 0 && <Badge variant="warning">Terlambat</Badge>}
    </span>
  )
}
