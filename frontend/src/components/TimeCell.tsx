import { Badge } from '@/components/ui/badge'
import { formatTime } from '@/lib/format'
import type { TimeValue } from '@/lib/types'

/** Jam masuk/pulang beserta sumbernya: dari scan asli atau dari koreksi (K-019). */
export function TimeCell({ value }: { value: TimeValue | null }) {
  if (!value) {
    return <span className="text-muted-foreground">—</span>
  }
  return (
    <span className="inline-flex items-center gap-2">
      <span className="font-semibold tabular-nums">{formatTime(value.time)}</span>
      {value.source === 'KOREKSI' && <Badge variant="warning">Koreksi</Badge>}
    </span>
  )
}
