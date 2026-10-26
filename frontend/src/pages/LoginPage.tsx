import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { Wordmark } from '@/components/Brand'
import { ErrorAlert } from '@/components/feedback'
import { fieldClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'
import { appendNext, safeNextPath } from '@/lib/safeNext'
import { homePathFor } from '@/navigation'

/** Halaman login bergaya Steel Premium dengan satu kartu glass (docs/design-system.md §7). */
export function LoginPage() {
  const { login, sessionExpired } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNextPath(params.get('next'))
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const me = await login(username, password)
      navigate(me.mustChangePassword ? appendNext('/ganti-password', next) : (next ?? homePathFor(me.role)), {
        replace: true,
      })
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Tidak dapat terhubung ke server')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="relative flex min-h-dvh flex-col overflow-hidden bg-steel-950 bg-[linear-gradient(150deg,var(--color-steel-700)_0%,var(--color-steel-800)_45%,var(--color-steel-950)_100%)] text-white">
      <div aria-hidden="true" className="absolute inset-x-0 top-0 h-1 bg-gold-500" />
      <div
        aria-hidden="true"
        className="motif-spandek pointer-events-none absolute inset-y-0 right-0 w-full text-white/[0.07] [mask-image:linear-gradient(to_left,black,transparent_70%)] lg:w-1/2"
      />

      <div className="relative mx-auto grid w-full max-w-6xl flex-1 items-center gap-10 px-4 py-10 md:px-8 lg:grid-cols-2 lg:gap-16">
        <div className="flex flex-col gap-5">
          <Wordmark tone="dark" />
          <div className="hidden flex-col gap-3 lg:flex">
            <h1 className="text-4xl leading-tight font-extrabold">
              Absensi dan penggajian <span className="text-gold-500">tiga cabang</span> dalam satu tempat.
            </h1>
            <p className="max-w-md text-steel-200">
              Pantau kehadiran dari mesin sidik jari, koreksi scan, dan susun gaji karyawan kantor maupun supir/kenek.
            </p>
          </div>
        </div>

        <div className="w-full max-w-md justify-self-center glass-strong rounded-xl border p-6 text-foreground sm:p-8 lg:justify-self-end">
          <h2 className="text-2xl font-bold text-steel-800 dark:text-foreground">Masuk</h2>
          <p className="mt-1 text-sm text-muted-foreground">Gunakan username dan password dari Finance & Accounting.</p>
          <form onSubmit={handleSubmit} className="mt-6 flex flex-col gap-4">
            {sessionExpired && !error && (
              <p role="status" className="rounded-md border border-info/25 bg-info-bg px-3 py-2 text-sm text-info">
                Sesi Anda sudah berakhir. Silakan masuk kembali.
              </p>
            )}
            {error && <ErrorAlert message={error} />}
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="username" className="font-semibold">
                Username
              </Label>
              <Input
                id="username"
                autoComplete="username"
                autoCapitalize="none"
                className={fieldClass}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password" className="font-semibold">
                Password
              </Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                className={fieldClass}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <Button type="submit" size="lg" className="mt-2 w-full" disabled={submitting}>
              {submitting ? 'Memproses…' : 'Masuk'}
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}
