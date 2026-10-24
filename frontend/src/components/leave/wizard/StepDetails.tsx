import type { ReactNode } from 'react'

import { fieldClass } from '@/components/FormDialog'
import type { WizardForm } from '@/components/leave/wizard/form'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { inclusiveDays, LETTER_FIELDS } from '@/lib/leave'
import type { LetterTemplate } from '@/lib/types'

interface StepDetailsProps {
  form: WizardForm
  template: LetterTemplate
  /** Kunci: reason, startDate, endDate, atau kunci isian surat. */
  errors: Record<string, string>
  onChange: (patch: Partial<WizardForm>) => void
}

const textareaClass =
  'min-h-24 w-full rounded-md border border-input bg-card px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 md:text-sm'

/** Langkah 2: tanggal dan isian surat sesuai templat jenis terpilih. Label di atas isian, galat di bawahnya. */
export function StepDetails({ form, template, errors, onChange }: StepDetailsProps) {
  const config = LETTER_FIELDS[template]
  const days = inclusiveDays(form.startDate, form.endDate)
  const reversed = !!form.startDate && !!form.endDate && days == null
  const endError = errors.endDate ?? (reversed ? 'Tanggal selesai harus sama atau setelah tanggal mulai' : undefined)

  return (
    <div className="flex flex-col gap-5">
      <div className="grid gap-4 sm:grid-cols-2">
        <Field id="startDate" label="Tanggal mulai" required error={errors.startDate}>
          <Input
            id="startDate"
            type="date"
            className={fieldClass}
            value={form.startDate}
            aria-invalid={!!errors.startDate}
            onChange={(e) => onChange({ startDate: e.target.value })}
          />
        </Field>
        <Field id="endDate" label="Tanggal selesai" required error={endError}>
          <Input
            id="endDate"
            type="date"
            className={fieldClass}
            value={form.endDate}
            min={form.startDate || undefined}
            aria-invalid={!!endError}
            onChange={(e) => onChange({ endDate: e.target.value })}
          />
        </Field>
      </div>
      <p className="text-sm text-muted-foreground">
        Jumlah hari:{' '}
        {days != null ? (
          <>
            <span className="font-semibold text-foreground">{days} hari</span> (hari kalender, termasuk tanggal mulai dan selesai)
          </>
        ) : (
          'pilih tanggal mulai dan selesai'
        )}
      </p>

      <Field id="reason" label={config.reasonLabel} required error={errors.reason}>
        <textarea
          id="reason"
          className={textareaClass}
          value={form.reason}
          maxLength={1000}
          aria-invalid={!!errors.reason}
          onChange={(e) => onChange({ reason: e.target.value })}
        />
      </Field>

      {config.fields.map((f) => (
        <Field key={f.key} id={f.key} label={f.label} required={f.required} error={errors[f.key]}>
          <Input
            id={f.key}
            className={fieldClass}
            inputMode={f.inputMode}
            type={f.inputMode === 'tel' ? 'tel' : 'text'}
            value={form.extra[f.key]}
            aria-invalid={!!errors[f.key]}
            onChange={(e) => onChange({ extra: { ...form.extra, [f.key]: e.target.value } })}
          />
        </Field>
      ))}
    </div>
  )
}

function Field({ id, label, required, error, children }: { id: string; label: string; required?: boolean; error?: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>
        {label}
        <span className="font-normal text-muted-foreground">{required ? ' (wajib)' : ' (opsional)'}</span>
      </Label>
      {children}
      {error && (
        <p role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
