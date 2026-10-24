import { useQuery } from '@tanstack/react-query'
import { CircleAlert } from 'lucide-react'
import { useParams } from 'react-router'

import { Wordmark } from '@/components/Brand'
import { SkeletonLines } from '@/components/feedback'
import { LeaveStatusBadge } from '@/components/leave/LeaveStatusBadge'
import { ThemeToggle } from '@/components/ThemeToggle'
import { formatDateTime } from '@/lib/format'
import { DECISION_METHOD_PHRASES, formatLeaveRange } from '@/lib/leave'
import type { PublicLetter } from '@/lib/types'

class VerifyError extends Error {}

/**
 * Memakai fetch polos, bukan helper api: halaman ini publik, jadi 401 tidak boleh memicu pengalihan ke login
 * dan tidak perlu cookie sesi maupun CSRF.
 */
async function fetchLetter(token: string): Promise<PublicLetter> {
  let response: Response
  try {
    response = await fetch(`/api/public/letters/${encodeURIComponent(token)}`, { headers: { Accept: 'application/json' } })
  } catch {
    throw new VerifyError('Tidak dapat terhubung ke server. Coba lagi.')
  }
  if (response.status === 404) throw new VerifyError('Surat tidak ditemukan. Periksa kembali kode QR atau link.')
  if (response.status === 429) throw new VerifyError('Terlalu banyak permintaan, coba lagi sebentar.')
  if (!response.ok) throw new VerifyError('Surat tidak dapat diverifikasi saat ini. Coba lagi nanti.')
  return (await response.json()) as PublicLetter
}

/** Halaman verifikasi surat yang dibuka dari QR di PDF; hanya data minimal, tanpa alasan atau lampiran. */
export function VerifyLetterPage() {
  const { token = '' } = useParams()
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/public/letters', token],
    queryFn: () => fetchLetter(token),
    retry: false,
  })

  return (
    <div className="min-h-dvh bg-background text-foreground">
      <div aria-hidden="true" className="h-1 bg-gold-500" />
      <header className="mx-auto flex w-full max-w-xl items-center justify-between gap-3 px-4 py-4">
        <Wordmark />
        <ThemeToggle />
      </header>
      <main className="mx-auto flex w-full max-w-xl flex-col gap-5 px-4 pb-10">
        <h1 className="font-heading text-2xl font-bold">Verifikasi Surat</h1>

        {isPending && <SkeletonLines count={5} />}

        {error && (
          <div role="alert" className="flex items-start gap-3 rounded-lg border border-danger/30 bg-danger-bg p-4 text-danger">
            <CircleAlert aria-hidden="true" className="mt-0.5 size-5 shrink-0" />
            <p className="text-sm font-semibold">{error.message}</p>
          </div>
        )}

        {data && <LetterDetail letter={data} />}
      </main>
    </div>
  )
}

function LetterDetail({ letter }: { letter: PublicLetter }) {
  return (
    <section className="flex flex-col gap-5 rounded-lg border p-5 glass-strong">
      <div className="flex flex-col gap-3">
        <p className="label-caps text-muted-foreground">Nomor surat</p>
        <p className="font-heading text-xl font-bold tabular-nums">{letter.letterNumber}</p>
        <div>
          <LeaveStatusBadge status={letter.status} size="lg" />
        </div>
      </div>

      <dl className="grid gap-4 sm:grid-cols-2">
        <Row label="Nama">{letter.employeeName}</Row>
        <Row label="Cabang">{letter.branchName}</Row>
        <Row label="Jenis">{letter.leaveTypeName}</Row>
        <Row label="Tanggal">{formatLeaveRange(letter)}</Row>
      </dl>

      <div>
        <h2 className="label-caps mb-2 text-muted-foreground">Persetujuan</h2>
        {letter.approvals.length === 0 ? (
          <p className="text-sm text-muted-foreground">Belum ada persetujuan.</p>
        ) : (
          <ul aria-label="Persetujuan" className="flex flex-col divide-y">
            {letter.approvals.map((a) => (
              <li key={a.stage} className="py-2.5 text-sm">
                <p className="font-semibold">
                  {a.stage === 'HR' ? 'HR/Finance' : 'Owner'} · {a.approved ? 'Disetujui' : 'Ditolak'}
                </p>
                <p className="text-muted-foreground">
                  {a.decidedByName} · {formatDateTime(a.decidedAt)} · {DECISION_METHOD_PHRASES[a.method]}
                </p>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function Row({ label, children }: { label: string; children: string }) {
  return (
    <div>
      <dt className="label-caps text-muted-foreground">{label}</dt>
      <dd className="mt-1 font-semibold break-words">{children}</dd>
    </div>
  )
}
