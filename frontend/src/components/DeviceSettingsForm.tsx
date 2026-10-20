import { type FormEvent, useState } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { fieldClass, selectClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'
import type { DeviceSettingsValues, NameDisplay } from '@/lib/types'

const NAME_DISPLAY: { value: NameDisplay; label: string }[] = [
  { value: 'FULL', label: 'Nama lengkap' },
  { value: 'FIRST', label: 'Nama depan' },
  { value: 'PIN', label: 'PIN saja' },
]

type NumberField = 'brightness' | 'buzzerVolume' | 'duplicateNoticeSeconds' | 'heartbeatSeconds'

/** Batas dari spec §18.1; server tetap memvalidasi ulang. */
const NUMBER_FIELDS: { name: NumberField; label: string; min: number; max: number; hint?: string }[] = [
  { name: 'brightness', label: 'Kecerahan layar (%)', min: 10, max: 100 },
  { name: 'buzzerVolume', label: 'Volume buzzer (%)', min: 0, max: 100, hint: '0 = buzzer mati.' },
  {
    name: 'duplicateNoticeSeconds',
    label: 'Jeda "Sudah tercatat" (detik)',
    min: 0,
    max: 600,
    hint: 'Scan ulang dalam jeda ini hanya diberi tahu "Sudah tercatat". 0 = mati.',
  },
  {
    name: 'heartbeatSeconds',
    label: 'Heartbeat (detik)',
    min: 2,
    max: 20,
    hint: 'Seberapa sering mesin menghubungi server. Paling lama 20 detik.',
  },
]

interface DeviceSettingsFormProps {
  idPrefix: string
  initial: DeviceSettingsValues
  submitLabel: string
  onSubmit: (values: DeviceSettingsValues) => Promise<void>
}

/** Form pengaturan ringan (tampilan & suara) satu mesin atau semua mesin. */
export function DeviceSettingsForm({ idPrefix, initial, submitLabel, onSubmit }: DeviceSettingsFormProps) {
  // Angka disimpan sebagai teks selama diketik, agar isian boleh kosong sementara.
  const [values, setValues] = useState<Omit<DeviceSettingsValues, NumberField> & Record<NumberField, string>>({
    headerText: initial.headerText,
    greeting: initial.greeting,
    nameDisplay: initial.nameDisplay,
    brightness: String(initial.brightness),
    buzzerVolume: String(initial.buzzerVolume),
    duplicateNoticeSeconds: String(initial.duplicateNoticeSeconds),
    heartbeatSeconds: String(initial.heartbeatSeconds),
  })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const set = (name: keyof typeof values, value: string) => setValues((current) => ({ ...current, [name]: value }))

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await onSubmit({
        headerText: values.headerText,
        greeting: values.greeting,
        nameDisplay: values.nameDisplay,
        brightness: Number(values.brightness),
        buzzerVolume: Number(values.buzzerVolume),
        duplicateNoticeSeconds: Number(values.duplicateNoticeSeconds),
        heartbeatSeconds: Number(values.heartbeatSeconds),
      })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="flex flex-col gap-3" onSubmit={submit}>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor={`${idPrefix}-header`} className="font-semibold">
          Judul layar
        </Label>
        <Input
          id={`${idPrefix}-header`}
          className={fieldClass}
          maxLength={32}
          value={values.headerText}
          onChange={(e) => set('headerText', e.target.value)}
        />
        <p className="text-xs text-muted-foreground">Kosong = nama cabang. Maksimal 32 karakter.</p>
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor={`${idPrefix}-greeting`} className="font-semibold">
          Sapaan
        </Label>
        <Input
          id={`${idPrefix}-greeting`}
          className={fieldClass}
          maxLength={64}
          required
          value={values.greeting}
          onChange={(e) => set('greeting', e.target.value)}
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor={`${idPrefix}-name`} className="font-semibold">
          Nama di layar
        </Label>
        <select
          id={`${idPrefix}-name`}
          className={selectClass}
          value={values.nameDisplay}
          onChange={(e) => set('nameDisplay', e.target.value)}
        >
          {NAME_DISPLAY.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        {NUMBER_FIELDS.map((field) => (
          <div key={field.name} className="flex flex-col gap-1.5">
            <Label htmlFor={`${idPrefix}-${field.name}`} className="font-semibold">
              {field.label}
            </Label>
            <Input
              id={`${idPrefix}-${field.name}`}
              type="number"
              inputMode="numeric"
              className={fieldClass}
              min={field.min}
              max={field.max}
              required
              value={values[field.name]}
              onChange={(e) => set(field.name, e.target.value)}
            />
            <p className="text-xs text-muted-foreground">
              {field.min}–{field.max}. {field.hint}
            </p>
          </div>
        ))}
      </div>
      {error && <ErrorAlert message={error} />}
      <div>
        <Button type="submit" disabled={saving}>
          {saving ? 'Menyimpan…' : submitLabel}
        </Button>
      </div>
    </form>
  )
}
