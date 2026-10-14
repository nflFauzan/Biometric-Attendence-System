import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { Wordmark } from '@/components/Brand'
import { ErrorAlert } from '@/components/feedback'
import { fieldClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, api } from '@/lib/api'
import { homePathFor } from '@/navigation'

export function ChangePasswordPage() {
  const { me, refresh } = useAuth()
  const navigate = useNavigate()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    if (next !== confirm) {
      setError('Konfirmasi password baru tidak sama')
      return
    }
    try {
      await api('/api/auth/change-password', { method: 'POST', body: { currentPassword: current, newPassword: next } })
      await refresh()
      navigate(me ? homePathFor(me.role) : '/', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? Object.values(e.fieldErrors)[0] ?? e.message : 'Terjadi kesalahan')
    }
  }

  return (
    <div className="flex min-h-dvh flex-col items-center bg-background px-4 py-10">
      <div aria-hidden="true" className="fixed inset-x-0 top-0 h-1 bg-gold-500" />
      <Wordmark className="mb-8" />
      <div className="w-full max-w-md rounded-xl border glass-strong p-6 sm:p-8">
        <h1 className="text-2xl font-bold text-steel-800 dark:text-foreground">Ganti password</h1>
        {me?.mustChangePassword && (
          <p className="mt-3 rounded-md border border-warning/25 bg-warning-bg px-3 py-2 text-sm text-warning">
            Anda wajib mengganti password sebelum melanjutkan.
          </p>
        )}
        <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
          {error && <ErrorAlert message={error} />}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="current" className="font-semibold">
              Password lama
            </Label>
            <Input id="current" type="password" autoComplete="current-password" className={fieldClass} value={current} onChange={(e) => setCurrent(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="next" className="font-semibold">
              Password baru (min. 8 karakter)
            </Label>
            <Input id="next" type="password" autoComplete="new-password" className={fieldClass} value={next} onChange={(e) => setNext(e.target.value)} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="confirm" className="font-semibold">
              Ulangi password baru
            </Label>
            <Input id="confirm" type="password" autoComplete="new-password" className={fieldClass} value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </div>
          <Button type="submit" size="lg" className="mt-2 w-full">
            Simpan
          </Button>
          {!me?.mustChangePassword && (
            <Button type="button" variant="ghost" className="w-full" onClick={() => navigate(-1)}>
              Batal
            </Button>
          )}
        </form>
      </div>
    </div>
  )
}
