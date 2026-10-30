import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, CalendarClock, Lock, Pencil, Plus, Scale } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { useAuth } from '@/auth/AuthContext'
import { ConfirmDeleteButton } from '@/components/ConfirmDeleteButton'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { DatePicker } from '@/components/DatePickers'
import { FormDialog, selectClass, type FieldDef, type FormValues } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { emptyToNull } from '@/lib/form'
import { formatDate, formatRupiah, formatTime, localDateIso } from '@/lib/format'
import {
  DAYS,
  RULE_LEVEL_LABELS,
  type DayOfWeek,
  type EffectiveRule,
  type RuleField,
  type RuleLevel,
  type RuleValues,
  type WorkRule,
} from '@/lib/types'
import { cn } from '@/lib/utils'
import { useCategoryOptions, useEmployeeOptions } from '@/pages/options'

const ENDPOINT = '/api/work-rules'
const LEVELS: RuleLevel[] = ['GLOBAL', 'CATEGORY', 'EMPLOYEE']

/** Nilai yang tidak diisi di level ini: diwarisi (Kategori/Karyawan) atau belum diatur (Global). */
function Inherited({ level }: { level: RuleLevel }) {
  return <span className="text-muted-foreground italic">{level === 'GLOBAL' ? 'belum diatur' : 'mewarisi'}</span>
}

function show(value: string | null, level: RuleLevel): ReactNode {
  return value ?? <Inherited level={level} />
}

function timeRange(start: string | null, end: string | null): string | null {
  return start || end ? `${formatTime(start)}–${formatTime(end)}` : null
}

function days(values: DayOfWeek[] | null): string | null {
  if (!values) {
    return null
  }
  return DAYS.filter((d) => values.includes(d.value))
    .map((d) => d.label.slice(0, 3))
    .join(', ')
}

/** Baris parameter yang ditampilkan di daftar versi dan panel aturan berlaku. */
const PARAMS: { field: RuleField; label: string; render: (v: RuleValues) => string | null }[] = [
  { field: 'WORK_START', label: 'Jam masuk', render: (v) => (v.workStart ? formatTime(v.workStart) : null) },
  { field: 'WORK_END', label: 'Jam pulang', render: (v) => (v.workEnd ? formatTime(v.workEnd) : null) },
  { field: 'BREAK', label: 'Istirahat', render: (v) => timeRange(v.breakStart, v.breakEnd) },
  {
    field: 'LATE_TOLERANCE',
    label: 'Toleransi terlambat',
    render: (v) => (v.lateToleranceMinutes === null ? null : `${v.lateToleranceMinutes} menit`),
  },
  { field: 'OVERTIME_START', label: 'Lembur mulai', render: (v) => (v.overtimeStart ? formatTime(v.overtimeStart) : null) },
  { field: 'WORK_DAYS', label: 'Hari kerja', render: (v) => days(v.workDays) },
  { field: 'DAILY_WAGE', label: 'Upah harian', render: (v) => (v.dailyWage === null ? null : formatRupiah(v.dailyWage)) },
  {
    field: 'OVERTIME_RATE',
    label: 'Tarif lembur/jam',
    render: (v) => (v.overtimeRatePerHour === null ? null : formatRupiah(v.overtimeRatePerHour)),
  },
  { field: 'BASE_SALARY', label: 'Gaji pokok', render: (v) => (v.baseSalary === null ? null : formatRupiah(v.baseSalary)) },
  {
    field: 'MIN_SCAN_GAP',
    label: 'Jeda minimum antar scan',
    render: (v) => (v.minScanGapMinutes === null ? null : `${v.minScanGapMinutes} menit`),
  },
]

const FIELDS: FieldDef[] = [
  {
    name: 'effectiveFrom',
    label: 'Berlaku mulai',
    type: 'date',
    required: true,
    hint: 'Tidak boleh sebelum hari ini. Setelah tanggal ini tiba, versi terkunci dan tidak bisa diubah.',
  },
  { name: 'workStart', label: 'Jam masuk', type: 'time' },
  { name: 'workEnd', label: 'Jam pulang', type: 'time' },
  { name: 'breakStart', label: 'Mulai istirahat', type: 'time' },
  { name: 'breakEnd', label: 'Selesai istirahat', type: 'time', hint: 'Isi keduanya atau kosongkan keduanya.' },
  { name: 'lateToleranceMinutes', label: 'Toleransi terlambat (menit)', type: 'number', placeholder: 'Bawaan 0 menit (tanpa toleransi)' },
  { name: 'overtimeStart', label: 'Lembur mulai dihitung setelah', type: 'time' },
  {
    name: 'workDays',
    label: 'Hari kerja',
    type: 'multi',
    options: DAYS.map((d) => ({ value: d.value, label: d.label })),
    hint: 'Tidak memilih satu hari pun berarti mewarisi.',
  },
  { name: 'dailyWage', label: 'Upah harian (Rp)', type: 'number' },
  { name: 'overtimeRatePerHour', label: 'Tarif lembur per jam (Rp)', type: 'number' },
  { name: 'baseSalary', label: 'Gaji pokok bulanan (Rp)', type: 'number' },
  {
    name: 'minScanGapMinutes',
    label: 'Jeda minimum antar scan (menit)',
    type: 'number',
    hint: 'Scan yang berjarak kurang dari jeda ini dihitung satu kali. Kosongkan jika tidak dipakai.',
  },
  { name: 'note', label: 'Catatan', placeholder: 'mis. Penyesuaian jadwal Ramadan' },
]

const EMPTY: FormValues = Object.fromEntries(FIELDS.map((f) => [f.name, '']))

const time = (value: string | null) => (value ? value.slice(0, 5) : '')
const num = (value: number | null) => (value === null ? '' : String(value))

function toValues(rule: WorkRule): FormValues {
  return {
    effectiveFrom: rule.effectiveFrom,
    workStart: time(rule.workStart),
    workEnd: time(rule.workEnd),
    breakStart: time(rule.breakStart),
    breakEnd: time(rule.breakEnd),
    lateToleranceMinutes: num(rule.lateToleranceMinutes),
    overtimeStart: time(rule.overtimeStart),
    workDays: (rule.workDays ?? []).join(','),
    dailyWage: num(rule.dailyWage),
    overtimeRatePerHour: num(rule.overtimeRatePerHour),
    baseSalary: num(rule.baseSalary),
    minScanGapMinutes: num(rule.minScanGapMinutes),
    note: rule.note ?? '',
  }
}

function toBody(v: FormValues, scope: RuleLevel, targetId: string) {
  const workDays = String(v.workDays).split(',').filter(Boolean)
  const tolerance = emptyToNull(v.lateToleranceMinutes)
  const scanGap = emptyToNull(v.minScanGapMinutes)
  return {
    scope,
    categoryId: scope === 'CATEGORY' ? Number(targetId) : null,
    employeeId: scope === 'EMPLOYEE' ? Number(targetId) : null,
    effectiveFrom: v.effectiveFrom || null,
    workStart: emptyToNull(v.workStart),
    workEnd: emptyToNull(v.workEnd),
    breakStart: emptyToNull(v.breakStart),
    breakEnd: emptyToNull(v.breakEnd),
    lateToleranceMinutes: tolerance === null ? (scope === 'GLOBAL' ? 0 : null) : Number(tolerance),
    overtimeStart: emptyToNull(v.overtimeStart),
    workDays: workDays.length ? workDays : null,
    // Nominal dikirim sebagai teks agar tidak kehilangan presisi; backend memakai BigDecimal.
    dailyWage: emptyToNull(v.dailyWage),
    overtimeRatePerHour: emptyToNull(v.overtimeRatePerHour),
    baseSalary: emptyToNull(v.baseSalary),
    minScanGapMinutes: scanGap === null ? null : Number(scanGap),
    note: emptyToNull(v.note),
  }
}

/**
 * Pengaturan aturan kerja bertanggal berlaku (K-046–K-050). Owner menambah dan mengubah versi yang belum
 * berlaku; HR/Finance hanya melihat. Nilai kosong mewarisi level di atasnya: Karyawan → Kategori → Global.
 */
export function WorkRulesPage() {
  const { me } = useAuth()
  const canEdit = me?.role === 'OWNER'
  const queryClient = useQueryClient()
  const categories = useCategoryOptions()
  const employees = useEmployeeOptions()
  const [level, setLevel] = useState<RuleLevel>('GLOBAL')
  const [targetId, setTargetId] = useState('')
  const [dialog, setDialog] = useState<{ editing: WorkRule | null } | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  const ready = level === 'GLOBAL' || targetId !== ''
  const params = new URLSearchParams({ scope: level })
  if (level === 'CATEGORY' && targetId) params.set('categoryId', targetId)
  if (level === 'EMPLOYEE' && targetId) params.set('employeeId', targetId)
  const versions = useQuery({
    queryKey: [ENDPOINT, params.toString()],
    queryFn: () => api<WorkRule[]>(`${ENDPOINT}?${params}`),
    enabled: ready,
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: [ENDPOINT] })

  const save = async (values: FormValues) => {
    const body = toBody(values, level, targetId)
    if (dialog?.editing) {
      await api(`${ENDPOINT}/${dialog.editing.id}`, { method: 'PUT', body })
    } else {
      await api(ENDPOINT, { method: 'POST', body })
    }
    await refresh()
  }

  const remove = async (rule: WorkRule) => {
    setActionError(null)
    await api(`${ENDPOINT}/${rule.id}`, { method: 'DELETE' })
    await refresh()
  }

  const chooseLevel = (next: RuleLevel) => {
    setLevel(next)
    setTargetId('')
  }

  const targetOptions = level === 'CATEGORY' ? categories : employees

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Aturan Kerja"
        description="Setiap perubahan dibuat sebagai versi baru dengan tanggal berlaku, sehingga hasil tanggal sebelumnya tidak berubah. Parameter yang kosong mewarisi level di atasnya: Karyawan → Kategori → Global."
        actions={
          canEdit && ready ? (
            <Button onClick={() => setDialog({ editing: null })}>
              <Plus />
              Tambah versi
            </Button>
          ) : undefined
        }
      />

      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-semibold" id="level-label">
            Level aturan
          </span>
          <div className="inline-flex rounded-md border glass p-1" role="group" aria-labelledby="level-label">
            {LEVELS.map((item) => (
              <Button
                key={item}
                size="sm"
                variant={item === level ? 'default' : 'ghost'}
                aria-pressed={item === level}
                onClick={() => chooseLevel(item)}
              >
                {RULE_LEVEL_LABELS[item]}
              </Button>
            ))}
          </div>
        </div>
        {level !== 'GLOBAL' && (
          <div className="flex min-w-64 flex-col gap-1.5">
            <Label htmlFor="rule-target" className="font-semibold">
              {level === 'CATEGORY' ? 'Kategori' : 'Karyawan'}
            </Label>
            <select id="rule-target" className={selectClass} value={targetId} onChange={(e) => setTargetId(e.target.value)}>
              <option value="">— Pilih —</option>
              {targetOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {!canEdit && (
        <p className="flex gap-2 rounded-md border border-info/25 bg-info-bg px-3 py-2 text-sm text-info">
          <Lock className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          Hanya Owner yang dapat mengubah aturan kerja dan tarif.
        </p>
      )}
      {actionError && <ErrorAlert message={actionError} />}
      {versions.error && <ErrorAlert message={versions.error.message} />}

      <section className="overflow-hidden rounded-lg border glass-strong" aria-labelledby="versi-aturan">
        <h2 id="versi-aturan" className="flex items-center gap-2 border-b px-5 py-3.5 text-base font-semibold">
          <CalendarClock className="size-4 text-muted-foreground" aria-hidden="true" />
          Versi aturan {RULE_LEVEL_LABELS[level]}
        </h2>
        {!ready && <EmptyState message={`Pilih ${level === 'CATEGORY' ? 'kategori' : 'karyawan'} untuk melihat versinya.`} />}
        {ready && versions.isPending && <SkeletonLines />}
        {versions.data?.length === 0 && (
          <EmptyState
            message={
              level === 'GLOBAL'
                ? 'Belum ada aturan Global. Perhitungan absensi ditolak sampai aturan wajib diatur.'
                : 'Belum ada versi di level ini; semua parameter mewarisi level di atasnya.'
            }
          />
        )}
        {versions.data && versions.data.length > 0 && (
          <ul className="divide-y">
            {versions.data.map((rule) => (
              <li key={rule.id} className="flex flex-col gap-3 px-5 py-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-semibold">Berlaku mulai {formatDate(rule.effectiveFrom)}</p>
                    {rule.locked ? (
                      <Badge variant="neutral">
                        <Lock />
                        Terkunci
                      </Badge>
                    ) : (
                      <Badge variant="info">
                        <CalendarClock />
                        Terjadwal
                      </Badge>
                    )}
                  </div>
                  {canEdit && !rule.locked && (
                    <div className="flex gap-1">
                      <Button variant="ghost" size="sm" onClick={() => setDialog({ editing: rule })}>
                        <Pencil />
                        Ubah
                      </Button>
                      <ConfirmDeleteButton
                        label={`versi ${formatDate(rule.effectiveFrom)}`}
                        onConfirm={() => remove(rule)}
                        onError={setActionError}
                      />
                    </div>
                  )}
                </div>
                <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 text-sm sm:grid-cols-[max-content_1fr_max-content_1fr]">
                  {PARAMS.map((param) => (
                    <div key={param.field} className="contents">
                      <dt className="text-muted-foreground">{param.label}</dt>
                      <dd className="tabular-nums">{show(param.render(rule), level)}</dd>
                    </div>
                  ))}
                </dl>
                {rule.note && <p className="text-sm text-muted-foreground">Catatan: {rule.note}</p>}
                <p className="text-xs text-muted-foreground">Dibuat oleh {rule.createdBy}</p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <EffectiveRulePanel />

      {dialog && (
        <FormDialog
          open
          title={dialog.editing ? 'Ubah versi aturan' : `Tambah versi aturan ${RULE_LEVEL_LABELS[level]}`}
          description={
            level === 'GLOBAL'
              ? 'Parameter yang kosong berarti belum diatur. Jam masuk, jam pulang, toleransi, batas lembur, dan hari kerja wajib ada sebelum absensi bisa dihitung.'
              : 'Isi hanya parameter yang berbeda; yang kosong mewarisi level di atasnya.'
          }
          fields={FIELDS}
          initialValues={dialog.editing ? toValues(dialog.editing) : { ...EMPTY, effectiveFrom: localDateIso() }}
          onClose={() => setDialog(null)}
          onSubmit={save}
        />
      )}
    </div>
  )
}

/** Aturan yang benar-benar dipakai untuk seorang karyawan pada sebuah tanggal, beserta asal tiap nilai. */
function EffectiveRulePanel() {
  const employees = useEmployeeOptions()
  const [employeeId, setEmployeeId] = useState('')
  const [date, setDate] = useState(localDateIso())
  const effective = useQuery({
    queryKey: [`${ENDPOINT}/effective`, employeeId, date],
    queryFn: () => api<EffectiveRule>(`${ENDPOINT}/effective?employeeId=${employeeId}&date=${date}`),
    enabled: employeeId !== '' && date !== '',
  })
  const data = effective.data

  return (
    <section className="rounded-lg border glass" aria-labelledby="aturan-berlaku">
      <h2 id="aturan-berlaku" className="flex items-center gap-2 border-b px-5 py-3.5 text-base font-semibold">
        <Scale className="size-4 text-muted-foreground" aria-hidden="true" />
        Aturan yang berlaku
      </h2>
      <div className="flex flex-wrap items-end gap-4 px-5 py-4">
        <div className="flex min-w-64 flex-col gap-1.5">
          <Label htmlFor="effective-employee" className="font-semibold">
            Karyawan
          </Label>
          <select
            id="effective-employee"
            className={selectClass}
            value={employeeId}
            onChange={(e) => setEmployeeId(e.target.value)}
          >
            <option value="">— Pilih —</option>
            {employees.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="effective-date" className="font-semibold">
            Tanggal
          </Label>
          <DatePicker id="effective-date" context="Aturan yang berlaku pada tanggal" value={date} onChange={setDate} />
        </div>
      </div>
      {effective.error && <ErrorAlert className="mx-5 mb-4" message={effective.error.message} />}
      {effective.isFetching && !data && <SkeletonLines count={4} />}
      {data && (
        <>
          {data.missing.length > 0 && (
            <p className="mx-5 mb-4 flex gap-2 rounded-md border border-warning/25 bg-warning-bg px-3 py-2 text-sm text-warning">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              Aturan belum lengkap: {data.missing.join(', ')}. Perhitungan absensi untuk tanggal ini akan ditolak.
            </p>
          )}
          <dl className="divide-y border-t">
            {PARAMS.map((param) => {
              const source = data.sources[param.field]
              const value = param.render(data.values)
              return (
                <div key={param.field} className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 px-5 py-2.5">
                  <dt className="text-sm text-muted-foreground">{param.label}</dt>
                  <dd className="flex items-center gap-2 text-sm">
                    <span className={cn('font-semibold tabular-nums', !value && 'font-normal text-muted-foreground italic')}>
                      {value ?? 'belum diatur'}
                    </span>
                    {source && (
                      <Badge variant={source.level === 'GLOBAL' ? 'neutral' : 'info'}>
                        {RULE_LEVEL_LABELS[source.level]} · sejak {formatDate(source.effectiveFrom)}
                      </Badge>
                    )}
                  </dd>
                </div>
              )
            })}
          </dl>
        </>
      )}
    </section>
  )
}
