import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
    <div className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle>Ganti password</CardTitle>
          {me?.mustChangePassword && (
            <p className="text-sm text-muted-foreground">Anda wajib mengganti password sebelum melanjutkan.</p>
          )}
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="current">Password lama</Label>
              <Input id="current" type="password" value={current} onChange={(e) => setCurrent(e.target.value)} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="next">Password baru (min. 8 karakter)</Label>
              <Input id="next" type="password" value={next} onChange={(e) => setNext(e.target.value)} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="confirm">Ulangi password baru</Label>
              <Input id="confirm" type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            </div>
            <Button type="submit">Simpan</Button>
            {!me?.mustChangePassword && (
              <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
                Batal
              </Button>
            )}
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
