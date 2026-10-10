import { AlertCircle } from 'lucide-react'
import type { ReactNode } from 'react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { cn } from '@/lib/utils'

/** Pesan error yang tetap tampil sampai masalahnya selesai (docs/design-system.md §6.7). */
export function ErrorAlert({ message, className }: { message: string; className?: string }) {
  return (
    <Alert variant="destructive" className={cn('border-danger/25 bg-danger-bg text-danger', className)}>
      <AlertCircle />
      <AlertDescription className="text-danger">{message}</AlertDescription>
    </Alert>
  )
}

/** Keadaan kosong: ikon motif spandek, satu kalimat, dan satu aksi (§6.13). */
export function EmptyState({ message, action }: { message: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 px-4 py-12 text-center">
      <span aria-hidden="true" className="relative block h-12 w-16 overflow-hidden rounded-md bg-steel-100">
        <span className="absolute inset-x-0 top-0 h-1 bg-gold-500" />
        <span className="motif-spandek absolute inset-x-2 top-2.5 bottom-2 text-steel-300 [background-size:8px_100%]" />
      </span>
      <p className="text-sm text-muted-foreground">{message}</p>
      {action}
    </div>
  )
}

/** Placeholder berbentuk baris selama data dimuat. */
export function SkeletonLines({ count = 3 }: { count?: number }) {
  return (
    <div className="flex flex-col gap-3 p-4" aria-busy="true" aria-label="Memuat">
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="h-9 animate-pulse rounded-md bg-steel-100 dark:bg-secondary" />
      ))}
    </div>
  )
}
