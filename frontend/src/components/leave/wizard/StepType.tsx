import { Check, FileText, Users } from 'lucide-react'

import { SkeletonLines } from '@/components/feedback'
import type { LeaveType } from '@/lib/types'
import { cn } from '@/lib/utils'

interface StepTypeProps {
  types: LeaveType[] | undefined
  value: number | null
  onChange: (id: number) => void
}

/** Langkah 1: satu pilihan besar per jenis aktif (target sentuh >= 56 px). */
export function StepType({ types, value, onChange }: StepTypeProps) {
  if (!types) {
    return <SkeletonLines count={3} />
  }
  if (types.length === 0) {
    return (
      <p className="rounded-lg border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
        Belum ada jenis izin yang aktif. Hubungi HR.
      </p>
    )
  }
  return (
    <div role="radiogroup" aria-label="Jenis izin" className="flex flex-col gap-3">
      {types.map((t) => {
        const selected = t.id === value
        return (
          <button
            key={t.id}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(t.id)}
            className={cn(
              'flex min-h-14 w-full items-start gap-3 rounded-xl border p-4 text-left transition-colors active:scale-[0.98] motion-reduce:active:scale-100',
              'outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50',
              selected ? 'border-primary bg-steel-100 dark:bg-accent' : 'glass-strong hover:bg-accent/60',
            )}
          >
            <span
              aria-hidden="true"
              className={cn(
                'mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border',
                selected ? 'border-primary bg-primary text-primary-foreground' : 'border-steel-300',
              )}
            >
              {selected && <Check className="size-3.5" />}
            </span>
            <span className="flex min-w-0 flex-col gap-1">
              <span className="font-heading text-base font-semibold">{t.name}</span>
              {t.description && <span className="text-sm text-muted-foreground">{t.description}</span>}
              {(t.requiresOwnerApproval || t.requiresDocument) && (
                <span className="mt-1 flex flex-wrap gap-2">
                  {t.requiresOwnerApproval && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-steel-100 px-2.5 py-0.5 text-xs font-semibold text-steel-800 dark:bg-secondary dark:text-foreground">
                      <Users className="size-3.5" />
                      Butuh persetujuan HR &amp; Owner
                    </span>
                  )}
                  {t.requiresDocument && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-warning-bg px-2.5 py-0.5 text-xs font-semibold text-warning">
                      <FileText className="size-3.5" />
                      Wajib surat
                    </span>
                  )}
                </span>
              )}
            </span>
          </button>
        )
      })}
    </div>
  )
}
