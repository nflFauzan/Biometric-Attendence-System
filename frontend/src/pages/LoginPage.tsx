import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { AppBackdrop } from '@/components/AppBackdrop'
import { Wordmark } from '@/components/Brand'
import { ErrorAlert } from '@/components/feedback'
import { fieldClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api'
import { appendNext, safeNextPath } from '@/lib/safeNext'
import { homePathFor } from '@/navigation'

/**
 * Halaman login dengan kartu kaca mengambang 21st.dev (gw-panel gw-glass):
 * Menampilkan ambient wallpaper backdrop, panel kaca ber-rounded 28px,
 * dan field masukan berbatas halus semi-transparan.
 */
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
    <div className="relative flex min-h-dvh flex-col items-center justify-center overflow-hidden p-4 sm:p-6 lg:p-8">
      {/* Dynamic ambient wallpaper backdrop */}
      <AppBackdrop className="fixed inset-0 pointer-events-none -z-10" />

      {/* Top gold accent bar */}
      <div aria-hidden="true" className="fixed inset-x-0 top-0 z-50 h-1 bg-gold-500" />

      <div className="relative z-10 mx-auto grid w-full max-w-5xl items-center gap-10 lg:grid-cols-2 lg:gap-16">
        {/* Sisi Kiri: Branding & Pengantar */}
        <div className="flex flex-col gap-6 text-white drop-shadow-sm">
          <Wordmark tone="dark" />
          <div className="hidden flex-col gap-3 lg:flex">
            <h1 className="text-4xl font-extrabold tracking-tight leading-tight">
              Absensi dan penggajian <span className="text-gold-400">tiga cabang</span> dalam satu ekosistem kaca.
            </h1>
            <p className="max-w-md text-sm sm:text-base text-white/80 leading-relaxed">
              Pantau kehadiran otomatis dari mesin sidik jari X401, kelola lembur, dan proses gaji secara transparan serta presisi.
            </p>
          </div>
        </div>

        {/* Sisi Kanan: Floating Glass Login Panel 21st.dev */}
        <div className="gw-panel gw-glass w-full max-w-md justify-self-center rounded-[28px] border border-white/20 p-7 sm:p-9 shadow-2xl backdrop-blur-2xl text-foreground lg:justify-self-end">
          <div className="mb-6">
            <h2 className="text-2xl font-bold tracking-tight text-foreground">Masuk</h2>
            <p className="mt-1 text-xs sm:text-sm text-muted-foreground">
              Gunakan kredensial akun dari Finance & Accounting.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {sessionExpired && !error && (
              <p role="status" className="rounded-xl border border-info/30 bg-info-bg/70 px-3.5 py-2.5 text-xs sm:text-sm text-info backdrop-blur-md">
                Sesi Anda sudah berakhir. Silakan masuk kembali.
              </p>
            )}

            {error && <ErrorAlert message={error} />}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="username" className="text-xs font-semibold text-foreground/90">
                Username
              </Label>
              <Input
                id="username"
                autoComplete="username"
                autoCapitalize="none"
                className={`${fieldClass} rounded-xl border-white/20 bg-white/10 dark:bg-white/5 backdrop-blur-sm focus-visible:ring-gold-400`}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="password" className="text-xs font-semibold text-foreground/90">
                Password
              </Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                className={`${fieldClass} rounded-xl border-white/20 bg-white/10 dark:bg-white/5 backdrop-blur-sm focus-visible:ring-gold-400`}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            <Button
              type="submit"
              size="lg"
              className="mt-3 w-full rounded-2xl bg-primary font-semibold text-primary-foreground shadow-lg transition-transform hover:scale-[1.01] active:scale-[0.99]"
              disabled={submitting}
            >
              {submitting ? 'Memproses…' : 'Masuk'}
            </Button>
          </form>
        </div>
      </div>
    </div>
  )
}
