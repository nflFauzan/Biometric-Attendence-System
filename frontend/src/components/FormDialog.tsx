import { AlertCircle } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'
import { dialogSheetClass } from '@/lib/ui'

export type FieldValue = string | boolean

export interface FieldDef {
  name: string
  label: string
  type?: 'text' | 'password' | 'date' | 'select' | 'checkbox'
  options?: { value: string; label: string }[]
  required?: boolean
  placeholder?: string
}

export type FormValues = Record<string, FieldValue>

interface FormDialogProps {
  open: boolean
  title: string
  fields: FieldDef[]
  initialValues: FormValues
  onClose: () => void
  onSubmit: (values: FormValues) => Promise<unknown>
}

/** Tinggi 44px di HP agar mudah disentuh, 40px di desktop (docs/design-system.md §6.2). */
export const fieldClass = 'h-11 rounded-md border-input bg-card px-3 text-base md:h-10 md:text-sm'

const selectClass =
  'h-11 w-full rounded-md border border-input bg-card px-3 text-base outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 md:h-10 md:text-sm'

/** Form generik dalam dialog (bottom sheet di HP). Error per field dari backend ditampilkan di bawah field-nya. */
export function FormDialog({ open, title, fields, initialValues, onClose, onSubmit }: FormDialogProps) {
  const [values, setValues] = useState<FormValues>(initialValues)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  const set = (name: string, value: FieldValue) => setValues((current) => ({ ...current, [name]: value }))

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    setFieldErrors({})
    try {
      await onSubmit(values)
      onClose()
    } catch (e) {
      if (e instanceof ApiError) {
        setError(e.message)
        setFieldErrors(e.fieldErrors)
      } else {
        setError('Terjadi kesalahan. Coba lagi.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">{title}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {error && <ErrorAlert message={error} />}
          {fields.map((field) => {
            const fieldError = fieldErrors[field.name]
            const errorId = `${field.name}-error`
            return (
              <div key={field.name} className="flex flex-col gap-1.5">
                {field.type === 'checkbox' ? (
                  <label className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md border bg-steel-50 px-3 text-sm font-semibold dark:bg-secondary">
                    <input
                      type="checkbox"
                      className="size-4 accent-steel-600"
                      checked={Boolean(values[field.name])}
                      onChange={(e) => set(field.name, e.target.checked)}
                    />
                    {field.label}
                  </label>
                ) : (
                  <>
                    <Label htmlFor={field.name} className="font-semibold">
                      {field.label}
                      {field.required && <span className="font-normal text-muted-foreground">(wajib)</span>}
                    </Label>
                    {field.type === 'select' ? (
                      <select
                        id={field.name}
                        className={selectClass}
                        aria-invalid={Boolean(fieldError)}
                        aria-describedby={fieldError ? errorId : undefined}
                        value={String(values[field.name] ?? '')}
                        onChange={(e) => set(field.name, e.target.value)}
                      >
                        <option value="">— Pilih —</option>
                        {field.options?.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    ) : (
                      <Input
                        id={field.name}
                        type={field.type ?? 'text'}
                        className={fieldClass}
                        placeholder={field.placeholder}
                        aria-invalid={Boolean(fieldError)}
                        aria-describedby={fieldError ? errorId : undefined}
                        value={String(values[field.name] ?? '')}
                        onChange={(e) => set(field.name, e.target.value)}
                      />
                    )}
                  </>
                )}
                {fieldError && (
                  <p id={errorId} className="flex items-center gap-1.5 text-xs text-danger">
                    <AlertCircle className="size-3.5 shrink-0" />
                    {fieldError}
                  </p>
                )}
              </div>
            )
          })}
          <DialogFooter className="mt-2 gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Batal
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? 'Menyimpan…' : 'Simpan'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
