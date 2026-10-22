import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { fieldClass, selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { REVIEW_STATUS_LABELS } from '@/lib/attendance'
import { addDays, formatDateTime, formatLocalDateTime, localDateIso } from '@/lib/format'
import type { HeldScan, ReviewStatus } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'

type Decision = 'DITERIMA' | 'DIABAIKAN'
type StatusFilter = ReviewStatus | ''

/** Sama dengan batas backend (ReviewRequest). */
const MAX_REVIEW = 200

const DECISION_TITLES: Record<Decision, string> = {
  DITERIMA: 'Masukkan ke rekap',
  DIABAIKAN: 'Abaikan',
}

const STATUS_TONES: Record<ReviewStatus, 'warning' | 'success' | 'neutral'> = {
  BELUM_DITINJAU: 'warning',
  DITERIMA: 'success',
  DIABAIKAN: 'neutral',
}

/**
 * Scan dari karyawan di cabang yang tidak diizinkan (K-103). Scan tertahan tidak masuk rekap sampai
 * ditinjau: "Masukkan ke rekap" menghitungnya, "Abaikan" membiarkannya di luar rekap. Scan asli tidak berubah.
 */
export function HeldScansPage() {
  const queryClient = useQueryClient()
  const [to, setTo] = useState(localDateIso())
  const [from, setFrom] = useState(addDays(localDateIso(), -29))
  const [status, setStatus] = useState<StatusFilter>('BELUM_DITINJAU')
  const [selected, setSelected] = useState<number[]>([])
  const [pending, setPending] = useState<{ decision: Decision; ids: number[] } | null>(null)

  const { data, error, isPending } = useQuery({
    queryKey: ['/api/attendance', 'held', from, to, status],
    queryFn: () =>
      api<HeldScan[]>(`/api/attendance/held-scans?from=${from}&to=${to}${status ? `&status=${status}` : ''}`),
    enabled: Boolean(from && to),
  })

  const toggle = (id: number) =>
    setSelected((current) => (current.includes(id) ? current.filter((v) => v !== id) : [...current, id]))
  const allIds = data?.map((row) => row.scan.id) ?? []
  const allSelected = allIds.length > 0 && allIds.every((id) => selected.includes(id))
  // Pilihan yang tidak lagi ada di daftar (mis. setelah filter berubah) tidak ikut terkirim.
  const chosen = selected.filter((id) => allIds.includes(id))
  const tooMany = chosen.length > MAX_REVIEW

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Scan Tertahan"
        description="Scan karyawan di cabang yang tidak diizinkan. Scan tetap tersimpan dan belum masuk rekap sampai ditinjau."
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="dari" className="font-semibold">
            Dari
          </Label>
          <Input id="dari" type="date" className={fieldClass} value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="sampai" className="font-semibold">
            Sampai
          </Label>
          <Input id="sampai" type="date" className={fieldClass} value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="status" className="font-semibold">
            Status
          </Label>
          <select
            id="status"
            className={selectClass}
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as StatusFilter)
              setSelected([])
            }}
          >
            <option value="BELUM_DITINJAU">Belum ditinjau</option>
            <option value="DITERIMA">Diterima</option>
            <option value="DIABAIKAN">Diabaikan</option>
            <option value="">Semua</option>
          </select>
        </div>
        <div className="flex gap-2">
          <Button disabled={chosen.length === 0 || tooMany} onClick={() => setPending({ decision: 'DITERIMA', ids: chosen })}>
            Masukkan ke rekap
          </Button>
          <Button variant="outline" disabled={chosen.length === 0 || tooMany} onClick={() => setPending({ decision: 'DIABAIKAN', ids: chosen })}>
            Abaikan
          </Button>
        </div>
        {tooMany && (
          <p role="status" className="w-full text-sm font-semibold">
            Maksimal {MAX_REVIEW} scan sekali tinjau. Kurangi pilihan.
          </p>
        )}
      </div>

      {error && <ErrorAlert message={error.message} />}
      <section className="overflow-x-auto rounded-lg border glass-strong">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.length} scan</p>}
        {isPending && <SkeletonLines />}
        {data?.length === 0 && <EmptyState message="Tidak ada scan tertahan pada rentang ini." />}
        {data && data.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b text-left label-caps text-muted-foreground">
                <th className="w-10 px-4 py-2">
                  <input
                    type="checkbox"
                    className="size-4 accent-steel-600"
                    aria-label="Pilih semua scan"
                    checked={allSelected}
                    onChange={() => setSelected(allSelected ? [] : allIds)}
                  />
                </th>
                <th className="px-3 py-2">Waktu scan</th>
                <th className="px-3 py-2">Karyawan</th>
                <th className="px-3 py-2">Cabang penempatan</th>
                <th className="px-3 py-2">Mesin</th>
                <th className="px-3 py-2">Status</th>
                <th className="px-3 py-2">Alasan</th>
                <th className="px-3 py-2">Peninjau</th>
                <th className="px-3 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y">
              {data.map((row) => (
                <tr key={row.scan.id}>
                  <td className="px-4 py-2.5">
                    <input
                      type="checkbox"
                      className="size-4 accent-steel-600"
                      aria-label={`Pilih scan ${row.employeeName ?? row.scan.devicePin}`}
                      checked={selected.includes(row.scan.id)}
                      onChange={() => toggle(row.scan.id)}
                    />
                  </td>
                  <td className="px-3 py-2.5 tabular-nums">{formatLocalDateTime(row.scan.deviceLocalTime)}</td>
                  <td className="px-3 py-2.5 font-semibold">{row.employeeName ?? `PIN ${row.scan.devicePin}`}</td>
                  <td className="px-3 py-2.5">{row.placementBranchName ?? '—'}</td>
                  <td className="px-3 py-2.5">
                    {row.scan.deviceName}
                    <span className="block text-xs text-muted-foreground">{row.deviceBranchName ?? '—'}</span>
                  </td>
                  <td className="px-3 py-2.5">
                    <Badge variant={STATUS_TONES[row.status]}>{REVIEW_STATUS_LABELS[row.status]}</Badge>
                  </td>
                  <td className="px-3 py-2.5">{row.reason ?? '—'}</td>
                  <td className="px-3 py-2.5">
                    {row.decidedBy ?? '—'}
                    {row.decidedAt && <span className="block text-xs text-muted-foreground">{formatDateTime(row.decidedAt)}</span>}
                  </td>
                  <td className="px-3 py-2.5 whitespace-nowrap">
                    <Button
                      size="sm"
                      variant="outline"
                      aria-label={`Masukkan ke rekap scan ${row.employeeName ?? row.scan.devicePin}`}
                      onClick={() => setPending({ decision: 'DITERIMA', ids: [row.scan.id] })}
                    >
                      Masukkan ke rekap
                    </Button>{' '}
                    <Button
                      size="sm"
                      variant="ghost"
                      aria-label={`Abaikan scan ${row.employeeName ?? row.scan.devicePin}`}
                      onClick={() => setPending({ decision: 'DIABAIKAN', ids: [row.scan.id] })}
                    >
                      Abaikan
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {pending && (
        <ReviewDialog
          key={`${pending.decision}-${pending.ids.join(',')}`}
          decision={pending.decision}
          scanIds={pending.ids}
          onClose={() => setPending(null)}
          onDone={() => {
            setSelected([])
            // Penerimaan mengubah rekap, jadi semua kueri absensi dan ringkasan dashboard dimuat ulang.
            void queryClient.invalidateQueries({ queryKey: ['/api/attendance'] })
            void queryClient.invalidateQueries({ queryKey: ['/api/dashboard/summary'] })
          }}
        />
      )}
    </div>
  )
}

function ReviewDialog({
  decision,
  scanIds,
  onClose,
  onDone,
}: {
  decision: Decision
  scanIds: number[]
  onClose: () => void
  onDone: () => void
}) {
  const [reason, setReason] = useState('')
  const mutation = useMutation({
    mutationFn: () =>
      api<HeldScan[]>('/api/attendance/held-scans/review', {
        method: 'POST',
        body: { scanIds, decision, reason: reason.trim() },
      }),
    onSuccess: () => {
      onDone()
      onClose()
    },
  })

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">{DECISION_TITLES[decision]}</DialogTitle>
          <DialogDescription>
            {decision === 'DITERIMA'
              ? `${scanIds.length} scan akan dihitung di rekap absensi.`
              : `${scanIds.length} scan tetap di luar rekap.`}{' '}
            Keputusan tercatat di audit log.
          </DialogDescription>
        </DialogHeader>
        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault()
            if (reason.trim()) mutation.mutate()
          }}
        >
          {mutation.error && <ErrorAlert message={mutation.error.message} />}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="alasan" className="font-semibold">
              Alasan <span className="font-normal text-muted-foreground">(wajib)</span>
            </Label>
            <Input
              id="alasan"
              className={fieldClass}
              maxLength={500}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          <DialogFooter className="mt-2 gap-2">
            <Button type="button" variant="outline" onClick={onClose}>
              Batal
            </Button>
            <Button type="submit" disabled={!reason.trim() || mutation.isPending}>
              {mutation.isPending ? 'Menyimpan…' : 'Simpan'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
