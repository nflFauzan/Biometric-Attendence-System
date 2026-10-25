import { Ban, Check, Circle, Clock, Minus, X } from 'lucide-react'

import { formatDateTime } from '@/lib/format'
import { DECISION_METHOD_PHRASES } from '@/lib/leave'
import type { ApprovalStage, LeaveApproval, LeaveRequest } from '@/lib/types'
import { cn } from '@/lib/utils'

type StepState = 'done' | 'rejected' | 'active' | 'upcoming' | 'cancelled' | 'skipped'

interface Step {
  key: string
  label: string
  state: StepState
  detail?: string
  note?: string | null
}

function approvalDetail(a: LeaveApproval): string {
  const verdict = a.approved ? 'Disetujui' : 'Ditolak'
  const recorded = a.recordedBy !== a.decidedBy ? `, dicatat oleh ${a.recordedByName} (${formatDateTime(a.recordedAt)})` : ''
  return `${verdict} oleh ${a.decidedByName} · ${formatDateTime(a.decidedAt)} · ${DECISION_METHOD_PHRASES[a.method]}${recorded}`
}

function stageStep(request: LeaveRequest, stage: ApprovalStage, label: string): Step {
  const approval = request.approvals.find((a) => a.stage === stage)
  if (approval) {
    return { key: stage, label, state: approval.approved ? 'done' : 'rejected', detail: approvalDetail(approval), note: approval.note }
  }
  const owner = request.approvals.find((a) => a.stage === 'OWNER')
  if (stage === 'HR' && owner) {
    // Owner memutus langsung tanpa tahap HR (K-125).
    return { key: stage, label, state: 'skipped', detail: `${owner.approved ? 'Disetujui' : 'Ditolak'} langsung oleh Owner` }
  }
  if (request.status === 'DITOLAK' || request.status === 'DIBATALKAN') {
    // Rantai berhenti sebelum tahap ini: tidak akan pernah ditinjau.
    return { key: stage, label, state: 'skipped', detail: 'Dilewati' }
  }
  return { key: stage, label, state: request.status !== 'DRAFT' && request.nextStage === stage ? 'active' : 'upcoming' }
}

function buildSteps(request: LeaveRequest): Step[] {
  const submitted: Step = request.submittedAt
    ? { key: 'submitted', label: 'Diajukan', state: 'done', detail: `${request.submittedBy ?? 'Pemohon'} · ${formatDateTime(request.submittedAt)}` }
    : { key: 'submitted', label: 'Diajukan', state: 'upcoming', detail: 'Belum dikirim' }
  const steps: Step[] = [submitted, stageStep(request, 'HR', 'Ditinjau HR')]
  if (request.requiresOwnerApproval) {
    steps.push(stageStep(request, 'OWNER', 'Ditinjau Owner'))
  }
  if (request.status === 'DISETUJUI') {
    steps.push({ key: 'final', label: 'Disetujui', state: 'done' })
  } else if (request.status === 'DITOLAK') {
    steps.push({ key: 'final', label: 'Ditolak', state: 'rejected' })
  } else if (request.status === 'DIBATALKAN') {
    steps.push({
      key: 'final',
      label: 'Dibatalkan',
      state: 'cancelled',
      detail: request.cancelledAt ? `${request.cancelledBy ?? ''} · ${formatDateTime(request.cancelledAt)}` : undefined,
      note: request.cancelReason,
    })
  } else {
    steps.push({ key: 'final', label: 'Disetujui', state: 'upcoming' })
  }
  return steps
}

const MARKER: Record<StepState, { icon: typeof Check; className: string }> = {
  done: { icon: Check, className: 'bg-success-bg text-success' },
  rejected: { icon: X, className: 'bg-danger-bg text-danger' },
  active: { icon: Clock, className: 'bg-warning-bg text-warning' },
  cancelled: { icon: Ban, className: 'bg-steel-100 text-muted-foreground dark:bg-secondary' },
  skipped: { icon: Minus, className: 'bg-steel-100 text-muted-foreground dark:bg-secondary' },
  upcoming: { icon: Circle, className: 'bg-steel-100 text-muted-foreground dark:bg-secondary' },
}

/** Garis waktu persetujuan: Diajukan, Ditinjau HR, (Ditinjau Owner), hasil akhir. */
export function ApprovalTimeline({ request }: { request: LeaveRequest }) {
  const steps = buildSteps(request)
  return (
    <ol aria-label="Riwayat persetujuan" className="flex flex-col">
      {steps.map((step, index) => {
        const { icon: Icon, className } = MARKER[step.state]
        return (
          <li key={step.key} className="relative flex gap-3 pb-5 last:pb-0">
            {index < steps.length - 1 && <span aria-hidden="true" className="absolute top-8 bottom-0 left-4 w-px bg-border" />}
            <span className={cn('z-10 grid size-8 shrink-0 place-items-center rounded-full', className)}>
              <Icon aria-hidden="true" className="size-4" />
            </span>
            <div className="min-w-0 pt-1">
              <p className={cn('font-heading text-sm font-semibold', (step.state === 'upcoming' || step.state === 'skipped') && 'text-muted-foreground')}>{step.label}</p>
              {step.state === 'active' && <p className="text-xs font-semibold text-warning">Sedang ditinjau</p>}
              {step.detail && <p className="text-sm text-muted-foreground">{step.detail}</p>}
              {step.note && <p className="text-sm whitespace-pre-wrap text-muted-foreground">“{step.note}”</p>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
