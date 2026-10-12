import { useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'

import { ConfirmDeleteButton } from '@/components/ConfirmDeleteButton'
import { CrudPage } from '@/components/CrudPage'
import { ErrorAlert } from '@/components/feedback'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { Holiday } from '@/lib/types'

const ENDPOINT = '/api/holidays'

const WEEKDAYS = ['Minggu', 'Senin', 'Selasa', 'Rabu', 'Kamis', 'Jumat', 'Sabtu']

/** "2026-12-25" → "Jumat". Dihitung dari tanggal UTC agar tidak bergeser karena zona waktu browser. */
function weekday(iso: string): string {
  return WEEKDAYS[new Date(`${iso}T00:00:00Z`).getUTCDay()]
}

/** Kalender libur untuk semua cabang (K-051), dikelola Owner dan HR/Finance (K-049). */
export function HolidaysPage() {
  const queryClient = useQueryClient()
  const [year, setYear] = useState(() => new Date().getFullYear())
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const remove = async (holiday: Holiday) => {
    setDeleteError(null)
    await api(`${ENDPOINT}/${holiday.id}`, { method: 'DELETE' })
    await queryClient.invalidateQueries({ queryKey: [ENDPOINT] })
  }

  return (
    <CrudPage<Holiday>
      title="Kalender Libur"
      description="Hari libur berlaku untuk semua cabang. Kehadiran pada hari libur ditandai perlu tinjauan; perlakuan lemburnya belum ditetapkan."
      endpoint={ENDPOINT}
      listParams={{ year: String(year) }}
      toolbar={
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-2" role="group" aria-label="Pilih tahun">
            <Button variant="outline" size="icon" aria-label="Tahun sebelumnya" onClick={() => setYear(year - 1)}>
              <ChevronLeft />
            </Button>
            <span className="min-w-16 text-center font-heading text-lg font-semibold tabular-nums" aria-live="polite">
              {year}
            </span>
            <Button variant="outline" size="icon" aria-label="Tahun berikutnya" onClick={() => setYear(year + 1)}>
              <ChevronRight />
            </Button>
          </div>
          {deleteError && <ErrorAlert message={deleteError} />}
        </div>
      }
      columns={[
        { header: 'Nama', cell: (h) => h.name, role: 'primary' },
        { header: 'Tanggal', cell: (h) => formatDate(h.date) },
        { header: 'Hari', cell: (h) => weekday(h.date) },
      ]}
      fields={() => [
        { name: 'date', label: 'Tanggal', type: 'date', required: true },
        { name: 'name', label: 'Nama', required: true, placeholder: 'mis. Hari Raya Idulfitri' },
      ]}
      emptyValues={{ date: '', name: '' }}
      toValues={(h) => ({ date: h.date, name: h.name })}
      toBody={(v) => ({ date: v.date || null, name: v.name })}
      rowActions={(h) => (
        <ConfirmDeleteButton label={`hari libur ${formatDate(h.date)}`} onConfirm={() => remove(h)} onError={setDeleteError} />
      )}
    />
  )
}
