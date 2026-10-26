import type { ReactNode } from 'react'

import type { WizardForm } from '@/components/leave/wizard/form'
import { formatLeaveRange, inclusiveDays, LETTER_FIELDS } from '@/lib/leave'
import type { LeaveRequest, LeaveType, LetterTemplate } from '@/lib/types'

interface StepReviewProps {
  form: WizardForm
  type: LeaveType | undefined
  template: LetterTemplate
  request: LeaveRequest
}

/** Langkah 4: ringkasan isian sebelum dikirim. Tombol aksi ada di halaman wizard. */
export function StepReview({ form, type, template, request }: StepReviewProps) {
  const config = LETTER_FIELDS[template]
  const days = inclusiveDays(form.startDate, form.endDate)
  return (
    <section aria-labelledby="wizard-ringkasan" className="flex flex-col gap-4 rounded-xl border p-5 glass-strong">
      <h2 id="wizard-ringkasan" className="font-heading text-lg font-bold text-steel-800 dark:text-foreground">
        Ringkasan pengajuan
      </h2>
      <dl className="grid gap-x-6 gap-y-4 sm:grid-cols-2">
        <Row label="Jenis">{type?.name ?? request.leaveTypeName}</Row>
        <Row label="Tanggal">
          {form.startDate && form.endDate ? formatLeaveRange({ startDate: form.startDate, endDate: form.endDate }) : '-'}
        </Row>
        <Row label="Jumlah hari">{days != null ? `${days} hari` : '-'}</Row>
        <Row label="Lampiran">{request.attachments.length === 0 ? 'Belum ada' : `${request.attachments.length} file`}</Row>
        <Row label={config.reasonLabel} wide>
          <span className="whitespace-pre-wrap">{form.reason || '-'}</span>
        </Row>
        {config.fields.map((f) => (
          <Row key={f.key} label={f.label}>
            {form.extra[f.key] || '-'}
          </Row>
        ))}
      </dl>
      <p className="text-sm text-muted-foreground">
        {type?.requiresOwnerApproval ? 'Setelah dikirim, pengajuan ditinjau Finance & Accounting lalu Owner.' : 'Setelah dikirim, pengajuan ditinjau Finance & Accounting.'}{' '}
        Periksa isian, lalu pilih Kirim atau simpan sebagai draft.
      </p>
    </section>
  )
}

function Row({ label, wide, children }: { label: string; wide?: boolean; children: ReactNode }) {
  return (
    <div className={wide ? 'sm:col-span-2' : undefined}>
      <dt className="label-caps text-muted-foreground">{label}</dt>
      <dd className="mt-1">{children}</dd>
    </div>
  )
}
