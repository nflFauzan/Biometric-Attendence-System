import { ArrowLeft, ArrowRight, CalendarDays, Check, ChevronLeft, ChevronRight, Clock } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'

import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { formatDayLabel, localDateIso } from '@/lib/format'
import { dialogSheetClass } from '@/lib/ui'
import { cn } from '@/lib/utils'

/*
 * Pemilih tanggal dan jam bergaya booking sheet (docs/design-system.md §6.8):
 * field berupa tombol, pilihan dibuat di sheet (bottom sheet di HP, dialog di desktop).
 * Nilai sama dengan input bawaan browser: "YYYY-MM-DD", "HH:mm", "YYYY-MM-DDTHH:mm[:ss]".
 */

const MONTHS_LONG = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember']
const MONTHS_SHORT = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des']
const WEEKDAYS = ['Sen', 'Sel', 'Rab', 'Kam', 'Jum', 'Sab', 'Min']
const pad = (n: number) => String(n).padStart(2, '0')

/** "14:05" atau "14:05:30" → "14.05" / "14.05.30" (detik hanya bila diminta). */
function formatClock(time: string, withSeconds = false): string {
  const [h, m, s = '00'] = time.split(':')
  return withSeconds ? `${h}.${m}.${s.slice(0, 2)}` : `${h}.${m}`
}

function clampDate(iso: string, min?: string, max?: string): string {
  if (min && iso < min) {
    return min
  }
  if (max && iso > max) {
    return max
  }
  return iso
}

/** Sel kalender sebulan dengan Senin sebagai hari pertama; null untuk sel kosong sebelum tanggal 1. */
function monthCells(year: number, month: number): (string | null)[] {
  const offset = (new Date(year, month, 1).getDay() + 6) % 7
  const days = new Date(year, month + 1, 0).getDate()
  const cells: (string | null)[] = Array.from({ length: offset }, () => null)
  for (let day = 1; day <= days; day++) {
    cells.push(`${year}-${pad(month + 1)}-${pad(day)}`)
  }
  return cells
}

function nowParts() {
  const now = new Date()
  return { date: localDateIso(now), time: `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}` }
}

type CommonProps = {
  id: string
  value: string
  onChange: (value: string) => void
  /** Teks di bawah judul sheet, biasanya label field (mis. "Tanggal lahir") atau nomor surat. */
  context?: string
  placeholder?: string
  /** Field opsional: sheet menampilkan tombol "Kosongkan" yang mengirim nilai kosong. */
  clearable?: boolean
  className?: string
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}

type DateBounds = {
  /** Batas tanggal "YYYY-MM-DD"; tanggal di luar batas tidak bisa dipilih. */
  min?: string
  max?: string
}

/** Field tombol pembuka sheet; tingginya sama dengan input lain (fieldClass). */
function PickerTrigger({
  id,
  text,
  placeholder,
  icon,
  className,
  onOpen,
  ...aria
}: {
  id: string
  text: string | null
  placeholder: string
  icon: ReactNode
  className?: string
  onOpen: () => void
  'aria-invalid'?: boolean
  'aria-describedby'?: string
}) {
  return (
    <button
      id={id}
      type="button"
      className={cn(
        'flex h-11 w-full min-w-0 items-center gap-2 rounded-md border border-input bg-card px-3 text-left text-base outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 aria-invalid:border-destructive md:h-10 md:text-sm',
        className,
      )}
      onClick={onOpen}
      {...aria}
    >
      <span className="shrink-0 text-muted-foreground [&_svg]:size-4">{icon}</span>
      <span className={cn('min-w-0 flex-1 truncate', !text && 'text-muted-foreground')}>{text ?? placeholder}</span>
      <ChevronRight className="size-4 shrink-0 text-muted-foreground" />
    </button>
  )
}

/** Kerangka sheet: pegangan (HP), judul + konteks, dan chip ringkasan di kanan atas. */
function PickerSheet({
  title,
  context,
  chip,
  onClose,
  children,
}: {
  title: string
  context?: string
  chip?: ReactNode
  onClose: () => void
  children: ReactNode
}) {
  return (
    <Dialog open onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className={cn(dialogSheetClass, 'gap-0 px-5 pt-3 md:max-w-sm md:pt-5')} showCloseButton={false}>
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-steel-200 md:hidden dark:bg-steel-700" aria-hidden />
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <DialogTitle className="font-heading text-lg font-semibold">{title}</DialogTitle>
            <DialogDescription className={cn('mt-1 truncate text-xs', !context && 'sr-only')}>
              {context ?? 'Pilih lalu konfirmasi'}
            </DialogDescription>
          </div>
          {chip && (
            <span className="flex shrink-0 items-center gap-1.5 rounded-lg bg-steel-800 px-2.5 py-1.5 text-xs font-semibold text-white dark:bg-primary dark:text-primary-foreground [&_svg]:size-3.5">
              {chip}
            </span>
          )}
        </div>
        {children}
      </DialogContent>
    </Dialog>
  )
}

/** Tombol aksi sheet; juga dipakai form bergaya sheet lain (EmployeeFormDialog) agar seragam. */
export const primaryButton =
  'flex h-12 flex-1 items-center justify-center gap-2 rounded-xl bg-steel-800 font-heading text-sm font-semibold text-white hover:bg-steel-900 disabled:opacity-50 dark:bg-primary dark:text-primary-foreground [&_svg]:size-4'
export const outlineButton =
  'flex h-12 shrink-0 items-center justify-center gap-2 rounded-xl border border-input bg-card px-4 font-heading text-sm font-semibold hover:bg-accent [&_svg]:size-4'

function SheetActions({ children }: { children: ReactNode }) {
  return <div className="mt-5 flex gap-3">{children}</div>
}

function ClearButton({ onClear }: { onClear: () => void }) {
  return (
    <button type="button" className={outlineButton} onClick={onClear}>
      Kosongkan
    </button>
  )
}

// ---------------------------------------------------------------------------------------------
// DatePicker

/** Pemilih tanggal satu langkah: kalender, lalu "Pilih". */
export function DatePicker({
  id,
  value,
  onChange,
  context,
  placeholder = 'Pilih tanggal',
  clearable,
  className,
  min,
  max,
  ...aria
}: CommonProps & DateBounds) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <PickerTrigger
        id={id}
        text={value ? formatDayLabel(value) : null}
        placeholder={placeholder}
        icon={<CalendarDays />}
        className={className}
        onOpen={() => setOpen(true)}
        {...aria}
      />
      {open && (
        <DateSheet
          initial={value}
          context={context}
          min={min}
          max={max}
          clearable={clearable}
          onClose={() => setOpen(false)}
          onConfirm={(next) => {
            onChange(next)
            setOpen(false)
          }}
        />
      )}
    </>
  )
}

function DateSheet({
  initial,
  context,
  min,
  max,
  clearable,
  onClose,
  onConfirm,
}: DateBounds & { initial: string; context?: string; clearable?: boolean; onClose: () => void; onConfirm: (value: string) => void }) {
  const [date, setDate] = useState(() => initial || clampDate(localDateIso(), min, max))
  return (
    <PickerSheet
      title="Pilih tanggal"
      context={context}
      chip={
        <>
          <CalendarDays />
          {formatDayLabel(date)}
        </>
      }
      onClose={onClose}
    >
      <MonthCalendar selected={date} min={min} max={max} onSelect={setDate} />
      <SheetActions>
        {clearable && <ClearButton onClear={() => onConfirm('')} />}
        <button type="button" className={primaryButton} onClick={() => onConfirm(date)}>
          <Check />
          Pilih
        </button>
      </SheetActions>
    </PickerSheet>
  )
}

// ---------------------------------------------------------------------------------------------
// TimePicker

/** Pemilih jam satu langkah: roda jam dan menit (24 jam), lalu "Pilih". Nilai keluar "HH:mm". */
export function TimePicker({ id, value, onChange, context, placeholder = 'Pilih jam', clearable, className, ...aria }: CommonProps) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <PickerTrigger
        id={id}
        text={value ? formatClock(value) : null}
        placeholder={placeholder}
        icon={<Clock />}
        className={className}
        onOpen={() => setOpen(true)}
        {...aria}
      />
      {open && (
        <TimeSheet
          initial={value}
          context={context}
          clearable={clearable}
          onClose={() => setOpen(false)}
          onConfirm={(next) => {
            onChange(next)
            setOpen(false)
          }}
        />
      )}
    </>
  )
}

function TimeSheet({
  initial,
  context,
  clearable,
  onClose,
  onConfirm,
}: {
  initial: string
  context?: string
  clearable?: boolean
  onClose: () => void
  onConfirm: (value: string) => void
}) {
  const [start] = useState(() => initial || nowParts().time)
  const [hour, setHour] = useState(Number(start.slice(0, 2)))
  const [minute, setMinute] = useState(Number(start.slice(3, 5)))
  return (
    <PickerSheet
      title="Pilih jam"
      context={context}
      chip={
        <>
          <Clock />
          {pad(hour)}.{pad(minute)}
        </>
      }
      onClose={onClose}
    >
      <TimeWheels hour={hour} minute={minute} onHour={setHour} onMinute={setMinute} />
      <SheetActions>
        {clearable && <ClearButton onClear={() => onConfirm('')} />}
        <button type="button" className={primaryButton} onClick={() => onConfirm(`${pad(hour)}:${pad(minute)}`)}>
          <Check />
          Pilih
        </button>
      </SheetActions>
    </PickerSheet>
  )
}

// ---------------------------------------------------------------------------------------------
// DateTimePicker

/**
 * Pemilih tanggal lalu jam dalam satu sheet dua langkah (kalender → roda jam), pengganti input datetime-local.
 * `withSeconds` menambah roda detik dan nilai keluar "YYYY-MM-DDTHH:mm:ss".
 */
export function DateTimePicker({
  id,
  value,
  onChange,
  context,
  placeholder = 'Pilih tanggal & jam',
  clearable,
  className,
  min,
  max,
  withSeconds,
  ...aria
}: CommonProps & DateBounds & { withSeconds?: boolean }) {
  const [open, setOpen] = useState(false)
  const [date, time = ''] = value ? value.split('T') : ['']
  return (
    <>
      <PickerTrigger
        id={id}
        text={value ? `${formatDayLabel(date)} · ${formatClock(time, withSeconds)}` : null}
        placeholder={placeholder}
        icon={<CalendarDays />}
        className={className}
        onOpen={() => setOpen(true)}
        {...aria}
      />
      {open && (
        <DateTimeSheet
          initial={value}
          context={context}
          min={min}
          max={max}
          clearable={clearable}
          withSeconds={withSeconds}
          onClose={() => setOpen(false)}
          onConfirm={(next) => {
            onChange(next)
            setOpen(false)
          }}
        />
      )}
    </>
  )
}

function DateTimeSheet({
  initial,
  context,
  min,
  max,
  clearable,
  withSeconds,
  onClose,
  onConfirm,
}: DateBounds & {
  initial: string
  context?: string
  clearable?: boolean
  withSeconds?: boolean
  onClose: () => void
  onConfirm: (value: string) => void
}) {
  const [start] = useState(() => {
    if (initial) {
      return initial
    }
    const now = nowParts()
    return `${clampDate(now.date, min, max)}T${now.time}`
  })
  const [step, setStep] = useState<'date' | 'time'>('date')
  const [date, setDate] = useState(start.slice(0, 10))
  const [hour, setHour] = useState(Number(start.slice(11, 13)))
  const [minute, setMinute] = useState(Number(start.slice(14, 16)))
  const [second, setSecond] = useState(withSeconds ? Number(start.slice(17, 19) || 0) : 0)

  const confirm = () => onConfirm(`${date}T${pad(hour)}:${pad(minute)}${withSeconds ? `:${pad(second)}` : ''}`)

  return (
    <PickerSheet
      title={step === 'date' ? 'Pilih tanggal' : 'Pilih jam'}
      context={context}
      chip={
        step === 'date' ? (
          <>
            <CalendarDays />
            {formatDayLabel(date)}
          </>
        ) : undefined
      }
      onClose={onClose}
    >
      <StepTrack step={step} />
      {step === 'date' ? (
        <>
          <MonthCalendar selected={date} min={min} max={max} onSelect={setDate} />
          <SheetActions>
            {clearable && <ClearButton onClear={() => onConfirm('')} />}
            <button type="button" className={primaryButton} onClick={() => setStep('time')}>
              <ArrowRight />
              Lanjut · Pilih jam
            </button>
          </SheetActions>
        </>
      ) : (
        <>
          <TimeWheels
            hour={hour}
            minute={minute}
            second={withSeconds ? second : undefined}
            onHour={setHour}
            onMinute={setMinute}
            onSecond={setSecond}
          />
          <SheetActions>
            <button type="button" aria-label="Kembali ke tanggal" className={cn(outlineButton, 'w-12 px-0')} onClick={() => setStep('date')}>
              <ArrowLeft />
            </button>
            <button type="button" className={primaryButton} onClick={confirm}>
              <Check />
              Konfirmasi
            </button>
          </SheetActions>
        </>
      )}
    </PickerSheet>
  )
}

/** Indikator dua langkah: titik "Tanggal" dan "Jam" dihubungkan garis yang terisi saat pindah ke langkah jam. */
function StepTrack({ step }: { step: 'date' | 'time' }) {
  const dot = (active: boolean) =>
    cn(
      'size-3.5 shrink-0 rounded-full border-2 transition-colors duration-200',
      active
        ? 'border-steel-800 bg-card ring-[3px] ring-steel-800 ring-inset dark:border-primary dark:ring-primary'
        : 'border-steel-200 bg-steel-100 dark:border-steel-700 dark:bg-steel-800',
    )
  return (
    <ol className="mt-4 flex items-center gap-2 text-xs font-semibold">
      <li className="flex items-center gap-1.5" aria-current={step === 'date' ? 'step' : undefined}>
        <span className={dot(true)} />
        Tanggal
      </li>
      <li className="relative h-0.5 flex-1 overflow-hidden rounded-full bg-steel-200 dark:bg-steel-700" aria-hidden>
        <span
          className={cn(
            'absolute inset-y-0 left-0 bg-steel-800 transition-[width] duration-300 motion-reduce:transition-none dark:bg-primary',
            step === 'time' ? 'w-full' : 'w-0',
          )}
        />
      </li>
      <li className={cn('flex items-center gap-1.5', step === 'date' && 'text-muted-foreground')} aria-current={step === 'time' ? 'step' : undefined}>
        <span className={dot(step === 'time')} />
        Jam
      </li>
    </ol>
  )
}

// ---------------------------------------------------------------------------------------------
// Kalender

const navClass = 'flex size-9 items-center justify-center rounded-full border border-input bg-card hover:bg-accent disabled:opacity-40'
const YEARS_PER_PAGE = 12

/**
 * Kalender bulanan. Judul bulan bisa diklik untuk lompat ke daftar tahun lalu bulan,
 * agar tanggal jauh (mis. tanggal lahir) tidak perlu digeser bulan demi bulan.
 */
function MonthCalendar({ selected, min, max, onSelect }: DateBounds & { selected: string; onSelect: (iso: string) => void }) {
  const [view, setView] = useState(() => {
    const [year, month] = selected.split('-').map(Number)
    return { year, month: month - 1 }
  })
  const [mode, setMode] = useState<'days' | 'years' | 'months'>('days')
  const [yearPage, setYearPage] = useState(() => Math.floor(view.year / YEARS_PER_PAGE) * YEARS_PER_PAGE)
  const today = localDateIso()
  const minYear = min ? Number(min.slice(0, 4)) : -Infinity
  const maxYear = max ? Number(max.slice(0, 4)) : Infinity
  const monthKey = (year: number, month: number) => `${year}-${pad(month + 1)}`

  const shift = (delta: number) => {
    const d = new Date(view.year, view.month + delta, 1)
    setView({ year: d.getFullYear(), month: d.getMonth() })
  }
  const firstOfView = `${monthKey(view.year, view.month)}-01`
  const lastOfView = `${monthKey(view.year, view.month)}-${pad(new Date(view.year, view.month + 1, 0).getDate())}`

  let heading: string
  let onPrev: () => void
  let onNext: () => void
  let prevDisabled: boolean
  let nextDisabled: boolean
  if (mode === 'years') {
    heading = `${yearPage}–${yearPage + YEARS_PER_PAGE - 1}`
    onPrev = () => setYearPage(yearPage - YEARS_PER_PAGE)
    onNext = () => setYearPage(yearPage + YEARS_PER_PAGE)
    prevDisabled = yearPage <= minYear
    nextDisabled = yearPage + YEARS_PER_PAGE - 1 >= maxYear
  } else if (mode === 'months') {
    heading = String(view.year)
    onPrev = () => setView({ ...view, year: view.year - 1 })
    onNext = () => setView({ ...view, year: view.year + 1 })
    prevDisabled = view.year <= minYear
    nextDisabled = view.year >= maxYear
  } else {
    heading = `${MONTHS_LONG[view.month]} ${view.year}`
    onPrev = () => shift(-1)
    onNext = () => shift(1)
    prevDisabled = !!min && firstOfView <= min
    nextDisabled = !!max && lastOfView >= max
  }

  const toggleMode = () => {
    if (mode === 'days') {
      setYearPage(Math.floor(view.year / YEARS_PER_PAGE) * YEARS_PER_PAGE)
      setMode('years')
    } else {
      setMode('days')
    }
  }

  const cellClass = (isSelected: boolean) =>
    cn(
      'flex items-center justify-center rounded-full text-sm font-medium transition-colors disabled:opacity-30',
      isSelected ? 'bg-steel-800 font-semibold text-white dark:bg-primary dark:text-primary-foreground' : 'hover:bg-accent',
    )

  return (
    <div className="mt-5">
      <div className="flex items-center justify-between">
        <button
          type="button"
          className="-ml-2 flex items-center gap-1 rounded-lg px-2 py-1 font-heading text-base font-semibold hover:bg-accent"
          aria-label={mode === 'days' ? `${heading}, ganti bulan dan tahun` : 'Kembali ke kalender'}
          onClick={toggleMode}
        >
          <span aria-live="polite">{heading}</span>
          <ChevronRight className={cn('size-4 text-muted-foreground transition-transform', mode !== 'days' && 'rotate-90')} />
        </button>
        <div className="flex gap-2">
          <button type="button" className={navClass} aria-label="Sebelumnya" disabled={prevDisabled} onClick={onPrev}>
            <ChevronLeft className="size-4" />
          </button>
          <button type="button" className={navClass} aria-label="Berikutnya" disabled={nextDisabled} onClick={onNext}>
            <ChevronRight className="size-4" />
          </button>
        </div>
      </div>

      {mode === 'years' && (
        <div className="mt-3 grid grid-cols-3 gap-2">
          {Array.from({ length: YEARS_PER_PAGE }, (_, i) => yearPage + i).map((year) => (
            <button
              key={year}
              type="button"
              disabled={year < minYear || year > maxYear}
              aria-pressed={year === view.year}
              className={cn(cellClass(year === view.year), 'h-11 rounded-xl')}
              onClick={() => {
                setView({ ...view, year })
                setMode('months')
              }}
            >
              {year}
            </button>
          ))}
        </div>
      )}

      {mode === 'months' && (
        <div className="mt-3 grid grid-cols-3 gap-2">
          {MONTHS_SHORT.map((label, month) => {
            const key = monthKey(view.year, month)
            const disabled = (!!min && key < min.slice(0, 7)) || (!!max && key > max.slice(0, 7))
            return (
              <button
                key={label}
                type="button"
                disabled={disabled}
                aria-label={`${MONTHS_LONG[month]} ${view.year}`}
                aria-pressed={key === selected.slice(0, 7)}
                className={cn(cellClass(key === selected.slice(0, 7)), 'h-11 rounded-xl')}
                onClick={() => {
                  setView({ year: view.year, month })
                  setMode('days')
                }}
              >
                {label}
              </button>
            )
          })}
        </div>
      )}

      {mode === 'days' && (
        <div className="mt-3 grid grid-cols-7 gap-y-1 text-center">
          {WEEKDAYS.map((day) => (
            <span key={day} className="pb-2 text-xs font-semibold text-muted-foreground">
              {day}
            </span>
          ))}
          {monthCells(view.year, view.month).map((iso, index) => {
            if (!iso) {
              return <span key={`blank-${index}`} />
            }
            const isSelected = iso === selected
            return (
              <button
                key={iso}
                type="button"
                disabled={(!!min && iso < min) || (!!max && iso > max)}
                aria-pressed={isSelected}
                aria-label={formatDayLabel(iso)}
                className={cn(cellClass(isSelected), 'mx-auto size-10', !isSelected && iso === today && 'font-bold text-primary')}
                onClick={() => onSelect(iso)}
              >
                {Number(iso.slice(8))}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------------------------
// Roda jam

function TimeWheels({
  hour,
  minute,
  second,
  onHour,
  onMinute,
  onSecond,
}: {
  hour: number
  minute: number
  second?: number
  onHour: (value: number) => void
  onMinute: (value: number) => void
  onSecond?: (value: number) => void
}) {
  const separator = (
    <span className="relative font-heading text-xl font-semibold" aria-hidden>
      :
    </span>
  )
  return (
    <div className="relative mt-4 flex items-center justify-center gap-1">
      <div className="pointer-events-none absolute inset-x-2 top-1/2 h-10 -translate-y-1/2 rounded-lg bg-steel-100 dark:bg-steel-800" aria-hidden />
      <WheelColumn label="Jam" count={24} value={hour} onChange={onHour} />
      {separator}
      <WheelColumn label="Menit" count={60} value={minute} onChange={onMinute} />
      {second !== undefined && onSecond && (
        <>
          {separator}
          <WheelColumn label="Detik" count={60} value={second} onChange={onSecond} />
        </>
      )}
    </div>
  )
}

const ITEM = 36
const VISIBLE = 5

/** Kolom roda bergaya iOS: digulir (scroll-snap), diklik, atau dikendalikan panah atas/bawah. */
function WheelColumn({ label, count, value, onChange }: { label: string; count: number; value: number; onChange: (value: number) => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const settle = useRef<ReturnType<typeof setTimeout>>(undefined)
  const [offset, setOffset] = useState(value * ITEM)
  const initialValue = useRef(value)

  useLayoutEffect(() => {
    if (ref.current) {
      ref.current.scrollTop = initialValue.current * ITEM
    }
  }, [])

  useEffect(() => () => clearTimeout(settle.current), [])

  const scrollToValue = (next: number) => {
    const clamped = Math.max(0, Math.min(count - 1, next))
    onChange(clamped)
    setOffset(clamped * ITEM)
    const el = ref.current
    if (!el) {
      return
    }
    if (typeof el.scrollTo === 'function') {
      el.scrollTo({ top: clamped * ITEM, behavior: 'smooth' })
    } else {
      el.scrollTop = clamped * ITEM
    }
  }

  const onScroll = () => {
    const top = ref.current?.scrollTop ?? 0
    setOffset(top)
    clearTimeout(settle.current)
    settle.current = setTimeout(() => {
      const index = Math.max(0, Math.min(count - 1, Math.round(top / ITEM)))
      if (index !== value) {
        onChange(index)
      }
    }, 90)
  }

  const onKeyDown = (event: KeyboardEvent) => {
    const moves: Record<string, number> = { ArrowUp: -1, ArrowDown: 1, PageUp: -5, PageDown: 5 }
    if (event.key in moves) {
      event.preventDefault()
      scrollToValue(value + moves[event.key])
    } else if (event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      scrollToValue(event.key === 'Home' ? 0 : count - 1)
    }
  }

  const fade = 'linear-gradient(to bottom, transparent, black 30%, black 70%, transparent)'

  return (
    <div
      ref={ref}
      role="spinbutton"
      tabIndex={0}
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={count - 1}
      aria-valuenow={value}
      aria-valuetext={pad(value)}
      className="relative w-20 snap-y snap-mandatory overflow-y-scroll rounded-lg outline-none [scrollbar-width:none] focus-visible:ring-2 focus-visible:ring-ring [&::-webkit-scrollbar]:hidden"
      style={{ height: ITEM * VISIBLE, maskImage: fade, WebkitMaskImage: fade }}
      onScroll={onScroll}
      onKeyDown={onKeyDown}
    >
      <div style={{ paddingBlock: ITEM * Math.floor(VISIBLE / 2) }}>
        {Array.from({ length: count }, (_, index) => {
          const distance = Math.min(Math.abs(index * ITEM - offset) / ITEM, 3)
          return (
            <div
              key={index}
              aria-hidden
              className={cn(
                'flex cursor-pointer snap-center items-center justify-center font-heading tabular-nums select-none',
                index === value ? 'font-semibold text-foreground' : 'text-muted-foreground',
              )}
              style={{ height: ITEM, fontSize: `${20 - distance * 2.5}px`, opacity: 1 - distance * 0.22 }}
              onClick={() => scrollToValue(index)}
            >
              {pad(index)}
            </div>
          )
        })}
      </div>
    </div>
  )
}
