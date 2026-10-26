import { AlertTriangle, Calculator, CheckCircle2, Info } from 'lucide-react'
import { useState, type FormEvent } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { DatePicker, TimePicker } from '@/components/DatePickers'
import { fieldClass, selectClass } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError, api } from '@/lib/api'
import { parseScanTimes } from '@/lib/form'
import { formatDate, formatDuration, formatTime, localDateIso } from '@/lib/format'
import type { AttendanceStatus, CalculationFlag, DailyPreview, DayType, TimeValue } from '@/lib/types'
import { useEmployeeOptions } from '@/pages/options'

const STATUS_LABELS: Record<AttendanceStatus, string> = {
  LENGKAP: 'Lengkap',
  TIDAK_LENGKAP: 'Tidak lengkap — perlu ditinjau',
  TIDAK_HADIR: 'Tidak hadir',
  TIDAK_ADA_SCAN: 'Tidak ada scan',
  IZIN: 'Izin',
}

const DAY_TYPE_LABELS: Record<DayType, string> = {
  HARI_KERJA: 'Hari kerja',
  BUKAN_HARI_KERJA: 'Bukan hari kerja',
  HARI_LIBUR: 'Hari libur',
}

const FLAG_LABELS: Record<CalculationFlag, string> = {
  ISTIRAHAT_BELUM_DIATUR: 'Jam istirahat belum diatur, jadi jam kerja normal belum dikurangi istirahat.',
  PERLU_TINJAUAN_HARI_LIBUR:
    'Hadir di hari libur atau bukan hari kerja. Hanya total kehadiran yang dihitung; perlakuan lemburnya belum ditetapkan.',
  URUTAN_TIDAK_WAJAR: 'Jam pulang tidak setelah jam masuk.',
  // Simulasi tidak memakai data izin; label ini hanya melengkapi tipe.
  HADIR_SAAT_IZIN: 'Ada scan padahal izin disetujui.',
  IZIN_MENUNGGU: 'Ada pengajuan izin yang belum diputus.',
  ADA_SCAN_TERTAHAN: 'Ada scan tertahan karena cabang yang belum ditinjau.',
}

function timeValue(value: TimeValue | null) {
  if (!value) {
    return <span className="text-muted-foreground">—</span>
  }
  return (
    <span className="flex items-center justify-end gap-2">
      <span className="font-semibold tabular-nums">{formatTime(value.time)}</span>
      <Badge variant={value.source === 'KOREKSI' ? 'warning' : 'neutral'}>{value.source === 'KOREKSI' ? 'Koreksi' : 'Scan'}</Badge>
    </span>
  )
}

/**
 * Simulasi rekap harian dengan aturan yang berlaku, untuk memeriksa pengaturan aturan sebelum scan asli dari
 * mesin tersedia (Fase 3). Tidak menyimpan apa pun.
 */
export function CalculationPreviewPage() {
  const employees = useEmployeeOptions()
  const [employeeId, setEmployeeId] = useState('')
  const [date, setDate] = useState(localDateIso())
  const [scans, setScans] = useState('08:00, 17:00')
  const [correctionIn, setCorrectionIn] = useState('')
  const [correctionOut, setCorrectionOut] = useState('')
  const [result, setResult] = useState<DailyPreview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    const scanTimes = parseScanTimes(scans)
    if (!employeeId) {
      setError('Pilih karyawan terlebih dahulu')
      return
    }
    if (scanTimes === null) {
      setError('Jam scan tidak valid. Gunakan format 08:00, pisahkan dengan koma.')
      return
    }
    setBusy(true)
    try {
      setResult(
        await api<DailyPreview>('/api/calculation/daily-preview', {
          method: 'POST',
          body: {
            employeeId: Number(employeeId),
            date,
            scanTimes,
            correctionCheckIn: correctionIn || null,
            correctionCheckOut: correctionOut || null,
          },
        }),
      )
    } catch (e) {
      setResult(null)
      setError(e instanceof ApiError ? e.message : 'Tidak dapat terhubung ke server')
    } finally {
      setBusy(false)
    }
  }

  const r = result?.result
  const rows: [string, string][] = r
    ? [
        ['Keterlambatan', formatDuration(r.lateSeconds)],
        ['Jam kerja normal', formatDuration(r.regularWorkSeconds)],
        ['Pulang lebih awal', formatDuration(r.earlyLeaveSeconds)],
        ['Kandidat lembur', formatDuration(r.overtimeCandidateSeconds)],
        ['Total kehadiran', formatDuration(r.totalPresenceSeconds)],
      ]
    : []

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Simulasi Perhitungan"
        description="Coba hitung rekap satu hari memakai aturan yang berlaku. Hasilnya tidak disimpan; scan asli dari mesin baru tersedia di Fase 3."
      />

      <div className="grid gap-4 lg:grid-cols-5">
        <form onSubmit={submit} className="flex flex-col gap-4 rounded-lg border glass-strong p-5 lg:col-span-2">
          {error && <ErrorAlert message={error} />}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="sim-employee" className="font-semibold">
              Karyawan
            </Label>
            <select id="sim-employee" className={selectClass} value={employeeId} onChange={(e) => setEmployeeId(e.target.value)}>
              <option value="">— Pilih —</option>
              {employees.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="sim-date" className="font-semibold">
              Tanggal
            </Label>
            <DatePicker id="sim-date" context="Tanggal simulasi" value={date} onChange={setDate} />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="sim-scans" className="font-semibold">
              Jam scan
            </Label>
            <Input
              id="sim-scans"
              className={fieldClass}
              value={scans}
              onChange={(e) => setScans(e.target.value)}
              aria-describedby="sim-scans-hint"
            />
            <p id="sim-scans-hint" className="text-xs text-muted-foreground">
              Pisahkan dengan koma, mis. 08:00, 12:01, 17:30. Kosongkan untuk mensimulasikan tidak ada scan.
            </p>
          </div>
          <fieldset className="grid grid-cols-2 gap-3">
            <legend className="mb-1.5 text-sm font-semibold">Koreksi (opsional)</legend>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="sim-correction-in" className="text-sm">
                Jam masuk
              </Label>
              <TimePicker id="sim-correction-in" context="Koreksi jam masuk" clearable value={correctionIn} onChange={setCorrectionIn} />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="sim-correction-out" className="text-sm">
                Jam pulang
              </Label>
              <TimePicker id="sim-correction-out" context="Koreksi jam pulang" clearable value={correctionOut} onChange={setCorrectionOut} />
            </div>
          </fieldset>
          <Button type="submit" disabled={busy}>
            <Calculator />
            {busy ? 'Menghitung…' : 'Hitung'}
          </Button>
        </form>

        <section className="rounded-lg border glass lg:col-span-3" aria-labelledby="hasil-simulasi" aria-live="polite">
          <h2 id="hasil-simulasi" className="border-b px-5 py-3.5 text-base font-semibold">
            Hasil
          </h2>
          {!r && <p className="px-5 py-6 text-sm text-muted-foreground">Isi form lalu tekan Hitung.</p>}
          {r && result && (
            <div className="flex flex-col gap-4 py-4">
              <div className="flex flex-wrap items-center gap-2 px-5">
                <p className="font-semibold">
                  {result.rule.employeeName} · {formatDate(r.date)}
                </p>
                <Badge variant={r.status === 'LENGKAP' ? 'success' : 'warning'}>
                  {r.status === 'LENGKAP' ? <CheckCircle2 /> : <AlertTriangle />}
                  {STATUS_LABELS[r.status]}
                </Badge>
                <Badge variant={r.dayType === 'HARI_KERJA' ? 'neutral' : 'info'}>{DAY_TYPE_LABELS[r.dayType]}</Badge>
              </div>
              <dl className="divide-y border-y">
                <div className="flex items-center justify-between gap-4 px-5 py-2.5">
                  <dt className="text-sm text-muted-foreground">Scan asli</dt>
                  <dd className="text-right text-sm tabular-nums">
                    {r.scanTimes.length ? r.scanTimes.map(formatTime).join(', ') : 'tidak ada'}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-4 px-5 py-2.5">
                  <dt className="text-sm text-muted-foreground">Jam masuk</dt>
                  <dd className="text-sm">{timeValue(r.checkIn)}</dd>
                </div>
                <div className="flex items-center justify-between gap-4 px-5 py-2.5">
                  <dt className="text-sm text-muted-foreground">Jam pulang</dt>
                  <dd className="text-sm">{timeValue(r.checkOut)}</dd>
                </div>
                {rows.map(([label, value]) => (
                  <div key={label} className="flex items-center justify-between gap-4 px-5 py-2.5">
                    <dt className="text-sm text-muted-foreground">{label}</dt>
                    <dd className="text-sm font-semibold tabular-nums">{value}</dd>
                  </div>
                ))}
              </dl>
              {r.flags.length > 0 && (
                <ul className="flex flex-col gap-2 px-5">
                  {r.flags.map((flag) => (
                    <li key={flag} className="flex gap-2 rounded-md border border-warning/25 bg-warning-bg px-3 py-2 text-sm text-warning">
                      <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
                      {FLAG_LABELS[flag]}
                    </li>
                  ))}
                </ul>
              )}
              <p className="flex gap-2 px-5 text-xs text-muted-foreground">
                <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
                Durasi tidak dibulatkan. Kandidat lembur belum diverifikasi dan belum dinominalkan. Zona waktu:{' '}
                {result.timezone}.
              </p>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
