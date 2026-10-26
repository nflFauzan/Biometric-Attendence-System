import { useQuery } from '@tanstack/react-query'
import {
  AlertCircle,
  Building2,
  Check,
  ChevronDown,
  Fingerprint,
  IdCard,
  KeyRound,
  Pencil,
  UserRound,
  UserRoundCog,
} from 'lucide-react'
import { useEffect, useState, type ComponentProps, type ReactNode } from 'react'

import type { CrudForm } from '@/components/CrudPage'
import { DatePicker, outlineButton, primaryButton } from '@/components/DatePickers'
import { ErrorAlert } from '@/components/feedback'
import { fieldClass, selectClass, useFormState, type FieldValue } from '@/components/FormDialog'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import { followSuggestions, suggestPassword, suggestUsername, textOf, type SuggestionRule } from '@/lib/credentials'
import { GENDER_LABELS, ROLE_LABELS, type Branch, type Category, type Employee, type Gender, type Role } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { cn } from '@/lib/utils'

/** Usulan yang mengikuti isian lain selama belum diubah manual (K-133; PIN mesin = No. karyawan). */
const SUGGESTIONS: SuggestionRule[] = [
  ['devicePin', (v) => textOf(v.employeeNumber)],
  ['username', (v) => suggestUsername(textOf(v.fullName))],
  ['password', (v) => suggestPassword(textOf(v.username), textOf(v.employeeNumber))],
]

const GENDER_OPTIONS = (Object.keys(GENDER_LABELS) as Gender[]).map((g) => ({
  value: g,
  label: g === 'TIDAK_DISEBUTKAN' ? 'Tidak disebutkan' : GENDER_LABELS[g],
}))

const ROLE_OPTIONS = (['EMPLOYEE', 'HR_FINANCE', 'OWNER'] as Role[]).map((r) => ({ value: r, label: ROLE_LABELS[r] }))

/** Bagian yang diringkas atau dilipat; terbuka sendiri bila server menolak salah satu field-nya. */
const HIDDEN_FIELDS = {
  pin: ['devicePin'],
  account: ['username', 'password'],
  personal: ['email', 'gender', 'birthDate', 'address', 'notes'],
} as const

type Part = keyof typeof HIDDEN_FIELDS

const textareaClass =
  'min-h-20 w-full rounded-md border border-input bg-card px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 aria-invalid:border-destructive md:text-sm'

const editButton =
  'inline-flex h-11 shrink-0 items-center gap-1.5 rounded-lg border border-input bg-card px-3 text-sm font-semibold hover:bg-accent md:h-9 [&_svg]:size-3.5'

const toggleCard =
  'flex min-h-11 cursor-pointer items-center gap-3 rounded-xl border bg-secondary/50 px-4 text-sm font-semibold dark:bg-secondary'

/**
 * Form tambah/ubah karyawan bergaya sheet pemilih tanggal (docs/design-system.md §6.8, K-136): yang selalu
 * tampil hanya identitas dan penempatan; PIN mesin dan akun berupa ringkasan, data pribadi dilipat.
 */
export function EmployeeFormDialog({ form, isOwner }: { form: CrudForm<Employee>; isOwner: boolean }) {
  const { editing } = form
  const { values, set, error, fieldErrors, saving, handleSubmit } = useFormState({
    initialValues: form.initialValues,
    onSubmit: form.onSubmit,
    onClose: form.onClose,
    derive: editing ? undefined : (next, prev) => followSuggestions(next, prev, SUGGESTIONS),
  })
  const [opened, setOpened] = useState<Record<Part, boolean>>({ pin: false, account: false, personal: editing !== null })
  const isOpen = (part: Part) => opened[part] || HIDDEN_FIELDS[part].some((name) => fieldErrors[name])
  const toggle = (part: Part) => setOpened((current) => ({ ...current, [part]: !isOpen(part) }))

  const branches = useQuery({ queryKey: ['/api/branches'], queryFn: () => api<Branch[]>('/api/branches') }).data ?? []
  const categories =
    useQuery({ queryKey: ['/api/employee-categories'], queryFn: () => api<Category[]>('/api/employee-categories') }).data ?? []
  // Kunci sama dengan daftar di CrudPage, jadi tidak ada permintaan tambahan.
  const employees = useQuery({ queryKey: ['/api/employees', null], queryFn: () => api<Employee[]>('/api/employees') }).data
  const positions = [...new Set((employees ?? []).map((e) => e.position?.trim()).filter((p): p is string => !!p))].sort()

  // Satu-satunya cabang/kategori aktif langsung terpilih saat tambah (rencana: 1 cabang dulu). Hanya dijalankan
  // saat datanya datang, jadi pilihan yang dikosongkan pengguna tidak diisi ulang.
  const onlyBranch = single(branches.filter((b) => b.active))?.id
  const onlyCategory = single(categories.filter((c) => c.active))?.id
  useEffect(() => {
    if (editing) return
    if (onlyBranch !== undefined && values.branchId === '') set('branchId', String(onlyBranch))
    if (onlyCategory !== undefined && values.categoryId === '') set('categoryId', String(onlyCategory))
  }, [onlyBranch, onlyCategory])

  const value = (name: string) => String(values[name] ?? '')
  const aria = (name: string): Aria => ({
    'aria-invalid': Boolean(fieldErrors[name]),
    'aria-describedby': fieldErrors[name] ? `${name}-error` : undefined,
  })
  const textInput = (name: string, props: ComponentProps<typeof Input> = {}) => (
    <Input id={name} value={value(name)} onChange={(e) => set(name, e.target.value)} {...aria(name)} {...props} className={cn(fieldClass, props.className)} />
  )
  const field = (name: string, label: string, control: ReactNode, opts: { required?: boolean; hint?: string; wide?: boolean } = {}) => (
    <Field name={name} label={label} error={fieldErrors[name]} {...opts}>
      {control}
    </Field>
  )

  const pin = value('devicePin').trim()
  const number = value('employeeNumber').trim()
  const hasAccount = values.createAccount === true
  const personalFilled = HIDDEN_FIELDS.personal.filter((name) => value(name).trim() !== '').length

  return (
    <Dialog open onOpenChange={(open) => !open && form.onClose()}>
      <DialogContent className={cn(dialogSheetClass, 'gap-0 px-5 pt-3 md:max-w-2xl md:pt-5')} showCloseButton={false}>
        <div className="mx-auto mb-3 h-1 w-10 rounded-full bg-steel-200 md:hidden dark:bg-steel-700" aria-hidden />
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <DialogTitle className="font-heading text-lg font-semibold">{editing ? 'Ubah karyawan' : 'Tambah karyawan'}</DialogTitle>
            <DialogDescription className="mt-1 truncate text-xs">
              {editing ? editing.fullName : 'Tanggal masuk tercatat otomatis hari ini.'}
            </DialogDescription>
          </div>
          {number && (
            <span className="flex max-w-[40%] shrink-0 items-center gap-1.5 rounded-lg bg-steel-800 px-2.5 py-1.5 font-mono text-xs font-semibold text-white dark:bg-primary dark:text-primary-foreground [&_svg]:size-3.5 [&_svg]:shrink-0">
              <IdCard />
              <span className="truncate">{number}</span>
            </span>
          )}
        </div>

        <form onSubmit={handleSubmit} className="mt-5 flex flex-col gap-6">
          {error && <ErrorAlert message={error} />}

          <Section icon={<UserRound />} title="Identitas">
            {field('employeeNumber', 'No. karyawan', textInput('employeeNumber', { className: 'font-mono', autoComplete: 'off' }), { required: true })}
            {field('fullName', 'Nama lengkap', textInput('fullName', { autoComplete: 'off' }), { required: true })}
            {field('phone', 'No. HP', textInput('phone', { type: 'tel', inputMode: 'tel', autoComplete: 'off' }), {
              hint: 'Dipakai untuk mengirim akun lewat WhatsApp.',
            })}
            {field(
              'position',
              'Jabatan',
              <>
                {textInput('position', { list: 'position-options', autoComplete: 'off' })}
                <datalist id="position-options">
                  {positions.map((p) => (
                    <option key={p} value={p} />
                  ))}
                </datalist>
              </>,
            )}
          </Section>

          <Section icon={<Building2 />} title="Penempatan">
            {field(
              'branchId',
              'Cabang',
              <Select
                name="branchId"
                value={value('branchId')}
                onChange={set}
                aria={aria('branchId')}
                options={branches.map((b) => ({ value: String(b.id), label: b.active ? b.name : `${b.name} (nonaktif)` }))}
              />,
              { required: true },
            )}
            {field(
              'categoryId',
              'Kategori',
              <Select
                name="categoryId"
                value={value('categoryId')}
                onChange={set}
                aria={aria('categoryId')}
                options={categories.map((c) => ({ value: String(c.id), label: c.name }))}
              />,
              { required: true },
            )}
            {isOpen('pin') ? (
              field('devicePin', 'PIN mesin', textInput('devicePin', { className: 'font-mono', autoComplete: 'off' }), {
                hint: 'User ID karyawan di mesin absensi, sama di semua cabang.',
                wide: true,
              })
            ) : (
              <SummaryCard
                icon={<Fingerprint />}
                testId="pin-summary"
                editLabel="Ubah PIN mesin"
                onEdit={() => toggle('pin')}
                className="md:col-span-2"
              >
                <p className="text-sm font-semibold">
                  PIN mesin <span className="ml-1 font-mono">{pin || '-'}</span>
                </p>
                <p className="text-xs text-muted-foreground">
                  {!pin
                    ? 'Terisi dari No. karyawan.'
                    : pin === number
                      ? 'Sama dengan No. karyawan.'
                      : 'User ID karyawan di mesin absensi.'}
                </p>
              </SummaryCard>
            )}
          </Section>

          {!editing && (
            <Section icon={<KeyRound />} title="Akun login" columns={1}>
              <label className={toggleCard}>
                <input
                  type="checkbox"
                  className="size-4 accent-steel-600"
                  checked={hasAccount}
                  onChange={(e) => set('createAccount', e.target.checked)}
                />
                Buatkan akun login
              </label>
              {hasAccount &&
                (isOpen('account') ? (
                  <div className="grid gap-4 md:grid-cols-2">
                    {field('username', 'Username', textInput('username', { className: 'font-mono', autoComplete: 'off' }), { required: true })}
                    {field('password', 'Password sementara', textInput('password', { className: 'font-mono', autoComplete: 'off' }), {
                      required: true,
                      hint: 'Wajib diganti saat login pertama.',
                    })}
                  </div>
                ) : (
                  <SummaryCard icon={<KeyRound />} testId="account-summary" editLabel="Ubah akun" onEdit={() => toggle('account')}>
                    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-0.5 text-sm">
                      <dt className="text-muted-foreground">Username</dt>
                      <dd className="min-w-0 truncate font-mono">{value('username') || '-'}</dd>
                      <dt className="text-muted-foreground">Password</dt>
                      <dd className="min-w-0 truncate font-mono">{value('password') || '-'}</dd>
                    </dl>
                  </SummaryCard>
                ))}
              {hasAccount && isOwner && (
                <Choice name="accountRole" label="Role" value={value('accountRole')} options={ROLE_OPTIONS} onChange={set} />
              )}
              {hasAccount && (
                <p className="text-xs text-muted-foreground">
                  Setelah disimpan, pesan berisi username dan password siap dikirim lewat WhatsApp.
                </p>
              )}
            </Section>
          )}

          <section className="flex flex-col gap-4">
            <button
              type="button"
              aria-expanded={isOpen('personal')}
              onClick={() => toggle('personal')}
              className="flex h-12 w-full items-center gap-3 rounded-xl border bg-card px-4 text-left hover:bg-accent [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-muted-foreground"
            >
              <UserRoundCog />
              <span className="flex-1 font-heading text-sm font-semibold">
                Data pribadi
                <span className="ml-2 font-sans text-xs font-normal text-muted-foreground">
                  {personalFilled > 0 ? `${personalFilled} terisi` : 'opsional'}
                </span>
              </span>
              <ChevronDown className={cn('transition-transform duration-200', isOpen('personal') && 'rotate-180')} />
            </button>
            {isOpen('personal') && (
              <div className="grid gap-4 md:grid-cols-2">
                <div className="md:col-span-2">
                  <Choice name="gender" label="Jenis kelamin" value={value('gender')} options={GENDER_OPTIONS} onChange={set} />
                </div>
                {field(
                  'birthDate',
                  'Tanggal lahir',
                  <DatePicker
                    id="birthDate"
                    value={value('birthDate')}
                    onChange={(v) => set('birthDate', v)}
                    context="Tanggal lahir"
                    clearable
                    {...aria('birthDate')}
                  />,
                )}
                {field('email', 'Email', textInput('email', { type: 'email', inputMode: 'email', autoComplete: 'off' }))}
                {field('address', 'Alamat', <TextArea name="address" value={value('address')} onChange={set} aria={aria('address')} />, {
                  wide: true,
                })}
                {field('notes', 'Catatan', <TextArea name="notes" value={value('notes')} onChange={set} aria={aria('notes')} />, {
                  wide: true,
                })}
              </div>
            )}
          </section>

          {editing && (
            <Section icon={<IdCard />} title="Status kepegawaian">
              {field(
                'endDate',
                'Tanggal keluar',
                <DatePicker
                  id="endDate"
                  value={value('endDate')}
                  onChange={(v) => set('endDate', v)}
                  context="Tanggal keluar"
                  clearable
                  {...aria('endDate')}
                />,
              )}
              <div className="flex flex-col justify-end">
                <label className={toggleCard}>
                  <input
                    type="checkbox"
                    className="size-4 accent-steel-600"
                    checked={values.active === true}
                    onChange={(e) => set('active', e.target.checked)}
                  />
                  Aktif
                </label>
              </div>
            </Section>
          )}

          <div className="flex gap-3">
            <button type="button" className={outlineButton} onClick={form.onClose}>
              Batal
            </button>
            <button type="submit" className={primaryButton} disabled={saving}>
              <Check />
              {saving ? 'Menyimpan…' : 'Simpan'}
            </button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function single<T>(items: T[]): T | undefined {
  return items.length === 1 ? items[0] : undefined
}

function Section({ icon, title, columns = 2, children }: { icon: ReactNode; title: string; columns?: 1 | 2; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4">
      <h3 className="flex items-center gap-2 font-heading text-sm font-semibold [&_svg]:size-4 [&_svg]:text-muted-foreground">
        {icon}
        {title}
      </h3>
      <div className={cn('grid gap-4', columns === 2 && 'md:grid-cols-2')}>{children}</div>
    </section>
  )
}

function Field({
  name,
  label,
  required,
  hint,
  error,
  wide,
  children,
}: {
  name: string
  label: string
  required?: boolean
  hint?: string
  error?: string
  wide?: boolean
  children: ReactNode
}) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', wide && 'md:col-span-2')}>
      <Label htmlFor={name} className="font-semibold">
        {label}
        {required && <span className="font-normal text-muted-foreground">(wajib)</span>}
      </Label>
      {children}
      {hint && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && (
        <p id={`${name}-error`} className="flex items-center gap-1.5 text-xs text-danger">
          <AlertCircle className="size-3.5 shrink-0" />
          {error}
        </p>
      )}
    </div>
  )
}

/** Ringkasan isian otomatis; "Ubah" membuka field aslinya. */
function SummaryCard({
  icon,
  testId,
  editLabel,
  onEdit,
  className,
  children,
}: {
  icon: ReactNode
  testId: string
  editLabel: string
  onEdit: () => void
  className?: string
  children: ReactNode
}) {
  return (
    <div data-testid={testId} className={cn('flex items-center gap-3 rounded-xl border bg-secondary/50 px-4 py-3 dark:bg-secondary', className)}>
      <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-card text-steel-700 dark:text-foreground [&_svg]:size-4">
        {icon}
      </span>
      <div className="min-w-0 flex-1">{children}</div>
      <button type="button" className={editButton} aria-label={editLabel} onClick={onEdit}>
        <Pencil />
        Ubah
      </button>
    </div>
  )
}

type OnChange = (name: string, value: FieldValue) => void
type Aria = { 'aria-invalid': boolean; 'aria-describedby'?: string }

function Select({
  name,
  value,
  options,
  onChange,
  aria,
}: {
  name: string
  value: string
  options: { value: string; label: string }[]
  onChange: OnChange
  aria: Aria
}) {
  return (
    <select id={name} className={selectClass} value={value} onChange={(e) => onChange(name, e.target.value)} {...aria}>
      <option value="">Pilih</option>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  )
}

function TextArea({ name, value, onChange, aria }: { name: string; value: string; onChange: OnChange; aria: Aria }) {
  return (
    <textarea id={name} rows={2} className={textareaClass} value={value} onChange={(e) => onChange(name, e.target.value)} {...aria} />
  )
}

/** Sedikit pilihan sebagai tombol berjejer; tetap radio asli, jadi panah keyboard berjalan. */
function Choice({
  name,
  label,
  value,
  options,
  onChange,
}: {
  name: string
  label: string
  value: string
  options: { value: string; label: string }[]
  onChange: OnChange
}) {
  return (
    <div role="radiogroup" aria-label={label} className="flex min-w-0 flex-col gap-1.5">
      <span className="text-sm font-semibold" aria-hidden>
        {label}
      </span>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
        {options.map((option) => (
          <label
            key={option.value}
            className="flex min-h-11 cursor-pointer items-center justify-center rounded-xl border border-input bg-card px-3 text-center text-sm font-semibold transition-colors duration-150 hover:bg-accent has-[:checked]:border-steel-800 has-[:checked]:bg-steel-800 has-[:checked]:text-white has-[:focus-visible]:ring-[3px] has-[:focus-visible]:ring-ring/25 md:min-h-10 dark:has-[:checked]:border-primary dark:has-[:checked]:bg-primary dark:has-[:checked]:text-primary-foreground"
          >
            <input
              type="radio"
              name={name}
              className="sr-only"
              value={option.value}
              checked={value === option.value}
              onChange={() => onChange(name, option.value)}
            />
            {option.label}
          </label>
        ))}
      </div>
    </div>
  )
}
