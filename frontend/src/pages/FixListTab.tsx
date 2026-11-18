import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import type { ReactNode } from 'react'

import { PeriodFilter, usePeriodFilter } from '@/components/PeriodFilter'
import { Badge } from '@/components/ui/badge'
import { formatCount, useFixCounts, type FixCount } from '@/lib/fixCounts'
import { HeldScansSection } from '@/pages/HeldScansPage'
import { ReviewQueueSection } from '@/pages/ReviewQueuePage'
import { UnmatchedScansSection } from '@/pages/UnmatchedScansPage'

/**
 * Tab "Perlu Dibereskan" (K-145): semua yang harus diputuskan sebelum gaji dihitung, dengan satu saringan periode.
 * Isi tiap bagian adalah halaman lama yang dipakai ulang.
 */
export function FixListTab() {
  const filter = usePeriodFilter()
  const counts = useFixCounts(filter)
  const allBranchesNote = filter.branchId ? 'Semua cabang. Data ini belum bisa disaring per cabang.' : undefined

  return (
    <div className="flex flex-col gap-6">
      <PeriodFilter showCategory />

      <FixSection
        id="hari-bermasalah"
        title="Hari bermasalah"
        count={counts.reviewDays}
        description="Jam tidak lengkap, urutan scan janggal, hadir saat izin atau libur, atau aturan kerja belum diatur. Buka rinciannya untuk mengoreksi."
      >
        <ReviewQueueSection filter={filter} />
      </FixSection>

      <FixSection
        id="scan-cabang-lain"
        title="Scan dari cabang lain"
        count={counts.heldPending}
        description="Scan karyawan di cabang yang tidak diizinkan. Belum masuk rekap sampai Anda memutuskan."
        note={[filter.heldFrom ? 'Yang belum ditinjau dari semua tanggal.' : null, allBranchesNote].filter(Boolean).join(' ') || undefined}
      >
        <HeldScansSection from={filter.heldFrom ?? filter.from} to={filter.to} />
      </FixSection>

      <FixSection
        id="scan-tak-dikenal"
        title="Scan sidik jari tak dikenal"
        count={counts.unmatched}
        description="Scan dari PIN mesin yang belum diisi di data karyawan. Belum masuk rekap."
        note={allBranchesNote}
      >
        <UnmatchedScansSection from={filter.from} to={filter.to} />
      </FixSection>
    </div>
  )
}

function FixSection({
  id,
  title,
  count,
  description,
  note,
  children,
}: {
  id: string
  title: string
  count: FixCount
  description: string
  note?: string
  children: ReactNode
}) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <h2 id={id} className="text-base font-semibold text-steel-800 dark:text-foreground">
            {title}
          </h2>
          <CountBadge count={count} />
        </div>
        <p className="max-w-[70ch] text-sm text-muted-foreground">{description}</p>
        {note && <p className="text-xs font-semibold text-muted-foreground">{note}</p>}
      </div>
      {children}
    </section>
  )
}

function CountBadge({ count }: { count: FixCount }) {
  if (count.error) {
    return <Badge variant="warning">Gagal dimuat</Badge>
  }
  if (count.count === null) {
    return null
  }
  if (count.count === 0) {
    return (
      <Badge variant="success">
        <CheckCircle2 aria-hidden="true" />
        Beres
      </Badge>
    )
  }
  return (
    <Badge variant="warning">
      <AlertTriangle aria-hidden="true" />
      {formatCount(count)} belum beres
    </Badge>
  )
}
