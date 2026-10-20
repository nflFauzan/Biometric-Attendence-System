import { fieldClass } from '@/components/FormDialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

interface PasswordConfirmFieldProps {
  id: string
  value: string
  onChange: (value: string) => void
}

/** Konfirmasi ulang password akun sebelum tindakan berisiko pada mesin (K-098, spec §18.3). */
export function PasswordConfirmField({ id, value, onChange }: PasswordConfirmFieldProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id} className="font-semibold">
        Password Anda
      </Label>
      <Input
        id={id}
        type="password"
        autoComplete="current-password"
        className={fieldClass}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      <p className="text-xs text-muted-foreground">Masukkan lagi password akun Anda untuk melanjutkan.</p>
    </div>
  )
}
