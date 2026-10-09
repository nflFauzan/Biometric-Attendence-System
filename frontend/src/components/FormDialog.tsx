import { useState, type FormEvent } from 'react'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'

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

const selectClass =
  'h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm shadow-xs focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50'

/** Form generik dalam dialog. Error per field dari backend ditampilkan di bawah field-nya. */
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
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex flex-col gap-4">
          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          {fields.map((field) => (
            <div key={field.name} className="flex flex-col gap-1.5">
              {field.type === 'checkbox' ? (
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={Boolean(values[field.name])}
                    onChange={(e) => set(field.name, e.target.checked)}
                  />
                  {field.label}
                </label>
              ) : (
                <>
                  <Label htmlFor={field.name}>
                    {field.label}
                    {field.required && <span className="text-destructive">*</span>}
                  </Label>
                  {field.type === 'select' ? (
                    <select
                      id={field.name}
                      className={selectClass}
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
                      placeholder={field.placeholder}
                      value={String(values[field.name] ?? '')}
                      onChange={(e) => set(field.name, e.target.value)}
                    />
                  )}
                </>
              )}
              {fieldErrors[field.name] && <p className="text-xs text-destructive">{fieldErrors[field.name]}</p>}
            </div>
          ))}
          <DialogFooter>
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
