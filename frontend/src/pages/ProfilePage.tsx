import { useQuery } from '@tanstack/react-query'

import { ErrorAlert, SkeletonLines } from '@/components/feedback'
import { SignatureCard } from '@/components/signature/SignatureCard'
import { api } from '@/lib/api'
import { formatDate, initials } from '@/lib/format'
import type { Employee } from '@/lib/types'

/** Profil karyawan. Dipakai terutama dari HP, jadi teks dan jarak dibuat besar (docs/design-system.md §3). */
export function ProfilePage() {
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/me/employee'],
    queryFn: () => api<Employee>('/api/me/employee'),
  })

  const rows: [string, string | null | undefined][] = [
    ['No. karyawan', data?.employeeNumber],
    ['Cabang', data?.branchName],
    ['Kategori', data?.categoryName],
    ['Jabatan', data?.position],
    ['No. HP', data?.phone],
    ['Tanggal masuk', data?.joinDate ? formatDate(data.joinDate) : null],
  ]

  return (
    <div className="mx-auto flex w-full max-w-xl flex-col gap-4">
      <h1 className="sr-only">Profil Saya</h1>
      {error && <ErrorAlert message={error.message} />}
      <section className="overflow-hidden rounded-xl border glass-strong">
        <div className="relative h-20 bg-steel-800">
          <div aria-hidden="true" className="motif-spandek absolute inset-0 text-white/[0.08]" />
          <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-1 bg-gold-500" />
        </div>
        <div className="-mt-9 flex flex-col gap-1 px-5 pb-5">
          <span className="grid size-[72px] place-items-center rounded-full border-4 border-card bg-steel-600 font-heading text-2xl font-bold text-white">
            {initials(data?.fullName)}
          </span>
          {isPending && <SkeletonLines count={2} />}
          {data && (
            <>
              <p className="mt-2 font-heading text-xl font-bold text-steel-800 dark:text-foreground">{data.fullName}</p>
              <p className="text-muted-foreground">{data.position ?? data.categoryName}</p>
            </>
          )}
        </div>
        {data && (
          <dl className="divide-y border-t">
            {rows.map(([label, value]) => (
              <div key={label} className="flex items-center justify-between gap-4 px-5 py-3.5">
                <dt className="text-muted-foreground">{label}</dt>
                <dd className="text-right font-semibold">{value ?? '—'}</dd>
              </div>
            ))}
          </dl>
        )}
      </section>
      <SignatureCard />
    </div>
  )
}
