import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, PencilLine } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { FormDialog, fieldClass, type FormValues } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { TimeCell } from '@/components/TimeCell'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { SCAN_FLAG_LABELS, dayStatus, heldScanLabel, reviewNotes } from '@/lib/attendance'
import { emptyToNull } from '@/lib/form'
import { addDays, formatDate, formatDateTime, formatDuration, formatTime, localDateIso } from '@/lib/format'
import type { DayRecord, EmployeeAttendance } from '@/lib/types'
import { useDashboardStream } from '@/lib/useDashboardStream'

/** Rekap per hari untuk satu karyawan, scan asli, dan koreksi dengan alasan wajib (K-059, TC-08). */
export function AttendanceEmployeePage() {
  useDashboardStream()
  const { id } = useParams()
  const [search] = useSearchParams()
  const initialTo = search.get('tanggal') ?? localDateIso()
  const [to, setTo] = useState(initialTo)
  const [from, setFrom] = useState(addDays(initialTo, -6))
  const [correcting, setCorrecting] = useState<DayRecord | null>(null)
  const queryClient = useQueryClient()
  const today = localDateIso()

  const { data, error, isPending } = useQuery({
    queryKey: ['/api/attendance', 'employee', id, from, to],
    queryFn: () => api<EmployeeAttendance>(`/api/attendance/employees/${id}?from=${from}&to=${to}`),
    enabled: Boolean(from && to),
  })

  const saveCorrection = async (values: FormValues) => {
    await api('/api/attendance/corrections', {
      method: 'POST',
      body: {
        employeeId: Number(id),
        date: correcting!.date,
        checkIn: emptyToNull(values.checkIn),
        checkOut: emptyToNull(values.checkOut),
        reason: values.reason,
      },
    })
    await queryClient.invalidateQueries({ queryKey: ['/api/attendance'] })
  }

  const days = data ? [...data.days].reverse() : []

  return (
    <div className="flex flex-col gap-5">
      <Link to="/absensi" className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-4" aria-hidden="true" />
        Absensi harian
      </Link>
      <PageHeader
        title={data ? data.fullName : 'Absensi karyawan'}
        description={
          data
            ? `${data.employeeNumber} · ${data.branchName} · PIN mesin ${data.devicePin ?? 'belum diisi'} · zona ${data.timezone}`
            : undefined
        }
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
      </div>

      {error && <ErrorAlert message={error.message} />}
      {isPending && <SkeletonLines />}

      <ul className="flex flex-col gap-3">
        {days.map((day) => {
          const status = dayStatus(day)
          const notes = day.ruleMissing ? [day.ruleMissing] : reviewNotes(day)
          return (
            <li key={day.date} className="rounded-lg border glass p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-semibold">{formatDate(day.date)}</p>
                  <Badge variant={status.tone}>{status.label}</Badge>
                </div>
                {day.date <= today && (
                  <Button variant="secondary" size="sm" onClick={() => setCorrecting(day)} aria-label={`Koreksi ${formatDate(day.date)}`}>
                    <PencilLine />
                    Koreksi
                  </Button>
                )}
              </div>

              <dl className="mt-3 grid grid-cols-2 gap-x-6 gap-y-2 text-sm sm:grid-cols-5">
                <div>
                  <dt className="label-caps text-muted-foreground">Masuk</dt>
                  <dd>
                    <TimeCell value={day.result?.checkIn ?? null} />
                  </dd>
                </div>
                <div>
                  <dt className="label-caps text-muted-foreground">Pulang</dt>
                  <dd>
                    <TimeCell value={day.result?.checkOut ?? null} />
                  </dd>
                </div>
                <div>
                  <dt className="label-caps text-muted-foreground">Terlambat</dt>
                  <dd className="tabular-nums">{formatDuration(day.result?.lateSeconds)}</dd>
                </div>
                <div>
                  <dt className="label-caps text-muted-foreground">Jam normal</dt>
                  <dd className="tabular-nums">{formatDuration(day.result?.regularWorkSeconds)}</dd>
                </div>
                <div>
                  <dt className="label-caps text-muted-foreground">Kandidat lembur</dt>
                  <dd className="tabular-nums">{formatDuration(day.result?.overtimeCandidateSeconds)}</dd>
                </div>
              </dl>

              <div className="mt-3 text-sm">
                <p className="label-caps text-muted-foreground">Scan asli ({day.scans.length})</p>
                {day.scans.length === 0 ? (
                  <p className="text-muted-foreground">Tidak ada scan.</p>
                ) : (
                  <ul className="mt-1 flex flex-wrap gap-2">
                    {day.scans.map((scan) => (
                      <li key={scan.id} className="rounded-md border bg-secondary/50 px-2 py-1">
                        <span className="font-semibold tabular-nums">{formatTime(scan.deviceLocalTime.slice(11))}</span>
                        <span className="text-xs text-muted-foreground"> · {scan.deviceName}</span>
                        {heldScanLabel(scan) && (
                          <Badge variant="warning" className="ml-1.5">
                            {heldScanLabel(scan)}
                          </Badge>
                        )}
                        {scan.flags.map((flag) => (
                          <span key={flag} className="block text-xs text-warning">
                            {SCAN_FLAG_LABELS[flag]}
                          </span>
                        ))}
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              {day.correction && (
                <p className="mt-3 rounded-md bg-warning-bg px-3 py-2 text-sm">
                  Dikoreksi oleh {day.correction.createdBy}: “{day.correction.reason}”
                </p>
              )}
              {notes.length > 0 && (
                <ul className="mt-2 text-xs text-warning">
                  {notes.map((note) => (
                    <li key={note}>{note}</li>
                  ))}
                </ul>
              )}
            </li>
          )
        })}
      </ul>

      <section className="rounded-lg border glass">
        <h2 className="border-b px-5 py-4 text-base font-semibold">Riwayat koreksi</h2>
        {data?.corrections.length === 0 && <EmptyState message="Belum ada koreksi pada rentang ini." />}
        <ul className="divide-y">
          {data?.corrections.map((c) => (
            <li key={c.id} className="flex flex-col gap-1 px-5 py-3 text-sm">
              <p className="flex flex-wrap items-center gap-2 font-semibold">
                {formatDate(c.workDate)} · masuk {formatTime(c.checkIn)} · pulang {formatTime(c.checkOut)}
                {c.supersededAt && <Badge variant="neutral">Digantikan</Badge>}
              </p>
              <p>“{c.reason}”</p>
              <p className="text-xs text-muted-foreground">
                {c.createdBy} · {formatDateTime(c.createdAt)}
              </p>
            </li>
          ))}
        </ul>
      </section>

      {correcting && (
        <FormDialog
          open
          title={`Koreksi absensi ${formatDate(correcting.date)}`}
          description="Scan asli dari mesin tidak berubah. Koreksi tersimpan terpisah, menggantikan koreksi sebelumnya, dan tercatat di audit log."
          fields={[
            { name: 'checkIn', label: 'Jam masuk', type: 'time', hint: 'Kosongkan bila jam masuk dari scan sudah benar.' },
            { name: 'checkOut', label: 'Jam pulang', type: 'time', hint: 'Kosongkan bila jam pulang dari scan sudah benar.' },
            { name: 'reason', label: 'Alasan koreksi', required: true, placeholder: 'mis. Lupa scan pulang, dikonfirmasi atasan' },
          ]}
          initialValues={{
            checkIn: correcting.correction?.checkIn?.slice(0, 5) ?? '',
            checkOut: correcting.correction?.checkOut?.slice(0, 5) ?? '',
            reason: '',
          }}
          onClose={() => setCorrecting(null)}
          onSubmit={saveCorrection}
        />
      )}
    </div>
  )
}
