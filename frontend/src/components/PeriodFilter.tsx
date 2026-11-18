import { DatePicker } from '@/components/DatePickers'
import { selectClass } from '@/components/FormDialog'
import { Label } from '@/components/ui/label'
import { ALL_DATES_FROM, type FixFilter } from '@/lib/fixCounts'
import { localDateIso } from '@/lib/format'
import { useSearchParamState } from '@/lib/useSearchParamState'
import { useBranchOptions, useCategoryOptions } from '@/pages/options'

/** Bawaan periode: tanggal 1 bulan berjalan sampai hari ini (sama dengan bawaan antrean tinjau dan lembur). */
export function defaultPeriod() {
  const today = localDateIso()
  return { from: `${today.slice(0, 8)}01`, to: today }
}

/** Periode dan saringan dari query string, dipakai bersama oleh tab Absensi dan tautan daftar cek gaji (K-145). */
export function usePeriodFilter(): FixFilter {
  const defaults = defaultPeriod()
  const [from] = useSearchParamState('from', defaults.from)
  const [to] = useSearchParamState('to', defaults.to)
  const [branchId] = useSearchParamState('branchId', '')
  const [categoryId] = useSearchParamState('categoryId', '')
  // Tanpa "Dari" pilihan pengguna, scan tertahan yang belum ditinjau diambil dari semua tanggal, sama dengan angka di
  // Beranda; scan lama yang belum diputuskan tidak boleh tampak "Beres".
  const heldFrom = from === defaults.from ? ALL_DATES_FROM : undefined
  return { from, to, branchId: branchId || undefined, categoryId: categoryId || undefined, heldFrom }
}

/** Saringan periode yang ditulis ke URL, agar tab lain dan tautan dari Gaji & Laporan membuka periode yang sama. */
export function PeriodFilter({ showCategory = false }: { showCategory?: boolean }) {
  const defaults = defaultPeriod()
  const [from, setFrom] = useSearchParamState('from', defaults.from)
  const [to, setTo] = useSearchParamState('to', defaults.to)
  const [branchId, setBranchId] = useSearchParamState('branchId', '')
  const [categoryId, setCategoryId] = useSearchParamState('categoryId', '')
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()

  return (
    <div className="flex flex-wrap items-end gap-4">
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="dari" className="font-semibold">
          Dari
        </Label>
        <DatePicker id="dari" context="Dari tanggal" value={from} max={to || undefined} onChange={setFrom} />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="sampai" className="font-semibold">
          Sampai
        </Label>
        <DatePicker id="sampai" context="Sampai tanggal" value={to} min={from || undefined} onChange={setTo} />
      </div>
      <div className="flex min-w-48 flex-col gap-1.5">
        <Label htmlFor="cabang" className="font-semibold">
          Cabang
        </Label>
        <select id="cabang" className={selectClass} value={branchId} onChange={(e) => setBranchId(e.target.value)}>
          <option value="">Semua cabang</option>
          {branchOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      {showCategory && (
        <div className="flex min-w-48 flex-col gap-1.5">
          <Label htmlFor="kategori" className="font-semibold">
            Kategori
          </Label>
          <select id="kategori" className={selectClass} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="">Semua kategori</option>
            {categoryOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      )}
    </div>
  )
}
