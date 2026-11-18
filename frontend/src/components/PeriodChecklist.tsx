import { AlertTriangle, ArrowRight, CheckCircle2, ListChecks } from 'lucide-react'
import { Link } from 'react-router'

import { Button } from '@/components/ui/button'
import { formatCount, hasOutstanding, type FixCount, type FixCounts, type FixFilter } from '@/lib/fixCounts'
import { formatDate } from '@/lib/format'

interface Item {
  key: string
  count: FixCount
  /** Teks bila masih ada yang belum beres; `{n}` diganti jumlahnya. */
  pending: string
  done: string
  failed: string
  action: string
  to: string
}

/** Alamat tab Absensi dengan periode yang sama; cabang/kategori hanya untuk data yang bisa disaring. */
function absensiLink(tab: string, filter: FixFilter, scoped: boolean) {
  const params = new URLSearchParams({ tab, from: filter.from, to: filter.to })
  if (scoped && filter.branchId) params.set('branchId', filter.branchId)
  if (scoped && filter.categoryId) params.set('categoryId', filter.categoryId)
  return `/absensi?${params}`
}

/**
 * Daftar cek sebelum unduh laporan (K-145): memperingatkan, tidak mengunci. Setiap baris membawa periode
 * yang sama ke tab Absensi agar pengguna langsung sampai ke datanya.
 */
export function PeriodChecklist({ filter, counts }: { filter: FixFilter; counts: FixCounts }) {
  const items: Item[] = [
    {
      key: 'hari',
      count: counts.reviewDays,
      pending: '{n} hari bermasalah',
      done: 'Tidak ada hari bermasalah',
      failed: 'Gagal memuat hari bermasalah',
      action: 'Bereskan',
      to: absensiLink('bereskan', filter, true),
    },
    {
      key: 'lembur',
      count: counts.overtimePending,
      pending: '{n} lembur belum divalidasi',
      done: 'Semua lembur sudah divalidasi',
      failed: 'Gagal memuat lembur',
      action: 'Validasi',
      to: absensiLink('lembur', filter, true),
    },
    {
      key: 'tertahan',
      count: counts.heldPending,
      pending: '{n} scan dari cabang lain belum ditinjau',
      done: 'Tidak ada scan dari cabang lain',
      failed: 'Gagal memuat scan dari cabang lain',
      action: 'Tinjau',
      to: absensiLink('bereskan', filter, false),
    },
    {
      key: 'tak-dikenal',
      count: counts.unmatched,
      pending: '{n} scan sidik jari tak dikenal',
      done: 'Tidak ada scan sidik jari tak dikenal',
      failed: 'Gagal memuat scan sidik jari tak dikenal',
      action: 'Lihat',
      to: absensiLink('bereskan', filter, false),
    },
  ]
  const allClear = !counts.isPending && !hasOutstanding(counts)

  return (
    <section aria-labelledby="daftar-cek" className="rounded-lg border glass">
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b px-5 py-3.5">
        <h2 id="daftar-cek" className="flex items-center gap-2 text-base font-semibold">
          <ListChecks className="size-4 text-muted-foreground" aria-hidden="true" />
          Sebelum unduh, bereskan dulu
        </h2>
        {filter.from && filter.to && (
          <p className="text-sm text-muted-foreground tabular-nums">
            {formatDate(filter.from)} s/d {formatDate(filter.to)}
          </p>
        )}
      </div>
      {allClear ? (
        <p className="flex items-center gap-2 px-5 py-3.5 text-sm font-semibold text-success">
          <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
          Semua beres. Laporan siap diunduh.
        </p>
      ) : (
        <ul className="divide-y">
          {items.map((item) => (
            <ChecklistRow key={item.key} item={item} />
          ))}
        </ul>
      )}
    </section>
  )
}

function ChecklistRow({ item }: { item: Item }) {
  const { count } = item
  if (count.error) {
    return (
      <li className="flex items-center gap-2 px-5 py-3 text-sm font-semibold text-warning">
        <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
        {item.failed}
      </li>
    )
  }
  if (count.count === null) {
    return (
      <li className="px-5 py-3 text-sm text-muted-foreground" aria-busy="true">
        Memeriksa…
      </li>
    )
  }
  if (count.count === 0) {
    return (
      <li className="flex items-center gap-2 px-5 py-3 text-sm text-success">
        <CheckCircle2 className="size-4 shrink-0" aria-hidden="true" />
        {item.done}
      </li>
    )
  }
  return (
    <li className="flex flex-wrap items-center justify-between gap-3 px-5 py-2.5">
      <span className="flex items-center gap-2 text-sm font-semibold text-warning">
        <AlertTriangle className="size-4 shrink-0" aria-hidden="true" />
        {item.pending.replace('{n}', formatCount(count))}
      </span>
      <Button variant="secondary" size="sm" asChild>
        <Link to={item.to}>
          {item.action}
          <ArrowRight aria-hidden="true" />
        </Link>
      </Button>
    </li>
  )
}
