import { useState } from 'react'
import { Link } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { CredentialMessageDialog, type CredentialMessage } from '@/components/CredentialMessageDialog'
import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import type { FieldDef, FormValues } from '@/components/FormDialog'
import { followSuggestions, suggestPassword, suggestUsername, textOf, type SuggestionRule } from '@/lib/credentials'
import { emptyToNull, toId } from '@/lib/form'
import { formatDate } from '@/lib/format'
import { GENDER_LABELS, ROLE_LABELS, type Employee, type Gender, type Role } from '@/lib/types'
import { useBranchOptions, useCategoryOptions } from '@/pages/options'

const GENDER_OPTIONS = (Object.keys(GENDER_LABELS) as Gender[]).map((g) => ({ value: g, label: GENDER_LABELS[g] }))

const ROLE_OPTIONS = (['EMPLOYEE', 'HR_FINANCE', 'OWNER'] as Role[]).map((r) => ({ value: r, label: ROLE_LABELS[r] }))

const text = textOf

/** Usulan yang mengikuti isian lain selama belum diubah manual (K-133; PIN mesin = No. karyawan). */
const SUGGESTIONS: SuggestionRule[] = [
  ['devicePin', (v) => text(v.employeeNumber)],
  ['username', (v) => suggestUsername(text(v.fullName))],
  ['password', (v) => suggestPassword(text(v.username), text(v.employeeNumber))],
]

const hasAccount = (v: FormValues) => v.createAccount === true

export function EmployeesPage() {
  const { me } = useAuth()
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()
  const [message, setMessage] = useState<CredentialMessage | null>(null)
  const isOwner = me?.role === 'OWNER'

  const profileFields: FieldDef[] = [
    { name: 'employeeNumber', label: 'No. karyawan', required: true },
    { name: 'fullName', label: 'Nama lengkap', required: true },
    { name: 'phone', label: 'No. HP' },
    { name: 'branchId', label: 'Cabang', type: 'select', options: branchOptions, required: true },
    { name: 'categoryId', label: 'Kategori', type: 'select', options: categoryOptions, required: true },
    { name: 'position', label: 'Jabatan' },
    {
      name: 'devicePin',
      label: 'PIN mesin',
      hint: 'User ID karyawan di mesin absensi (sama di semua mesin). Scan dengan PIN ini otomatis terhubung.',
    },
    { name: 'email', label: 'Email' },
    { name: 'gender', label: 'Jenis kelamin', type: 'select', options: GENDER_OPTIONS },
    { name: 'birthDate', label: 'Tanggal lahir', type: 'date' },
    { name: 'address', label: 'Alamat' },
    { name: 'notes', label: 'Catatan' },
  ]

  // K-133: akun dibuat bersama karyawan baru; role selain Karyawan hanya bisa dipilih Owner.
  const accountFields: FieldDef[] = [
    { name: 'createAccount', label: 'Buatkan akun login', type: 'checkbox' },
    { name: 'username', label: 'Username', required: true, visible: hasAccount },
    {
      name: 'password',
      label: 'Password sementara',
      required: true,
      visible: hasAccount,
      hint: 'Karyawan wajib menggantinya saat login pertama.',
    },
    ...(isOwner
      ? [{ name: 'accountRole', label: 'Role', type: 'select', options: ROLE_OPTIONS, required: true, visible: hasAccount } as FieldDef]
      : []),
  ]

  return (
    <>
      <CrudPage<Employee>
        title="Karyawan"
        description="Data karyawan tidak dihapus. Untuk karyawan keluar, isi tanggal keluar lalu nonaktifkan."
        endpoint="/api/employees"
        // Owner dan Finance & Accounting mengubah data karyawan (K-135).
        readOnly={!isOwner && me?.role !== 'HR_FINANCE'}
        columns={[
          {
            header: 'Nama',
            cell: (e) => (
              <Link to={`/karyawan/${e.id}`} className="text-steel-700 underline-offset-4 hover:underline dark:text-foreground">
                {e.fullName}
              </Link>
            ),
            role: 'primary',
          },
          { header: 'No. karyawan', cell: (e) => e.employeeNumber, mono: true },
          { header: 'PIN mesin', cell: (e) => e.devicePin ?? '—', mono: true },
          { header: 'Cabang', cell: (e) => e.branchName },
          { header: 'Kategori', cell: (e) => e.categoryName },
          { header: 'Tgl masuk', cell: (e) => formatDate(e.joinDate) },
          { header: 'Status', cell: (e) => <ActiveBadge active={e.active} />, role: 'status' },
        ]}
        // K-134: tanggal masuk diisi otomatis saat tambah dan tidak diubah lewat form.
        fields={(editing) =>
          editing
            ? [
                ...profileFields,
                { name: 'endDate', label: 'Tanggal keluar', type: 'date' },
                { name: 'active', label: 'Aktif', type: 'checkbox' },
              ]
            : [...profileFields, ...accountFields]
        }
        derive={(next, prev, editing) => (editing ? next : followSuggestions(next, prev, SUGGESTIONS))}
        emptyValues={{
          employeeNumber: '',
          fullName: '',
          phone: '',
          branchId: '',
          categoryId: '',
          position: '',
          devicePin: '',
          email: '',
          gender: '',
          birthDate: '',
          address: '',
          notes: '',
          createAccount: true,
          username: '',
          password: '',
          accountRole: 'EMPLOYEE',
        }}
        toValues={(e) => ({
          employeeNumber: e.employeeNumber,
          fullName: e.fullName,
          phone: e.phone ?? '',
          branchId: String(e.branchId),
          categoryId: String(e.categoryId),
          position: e.position ?? '',
          endDate: e.endDate ?? '',
          devicePin: e.devicePin ?? '',
          email: e.email ?? '',
          gender: e.gender ?? '',
          birthDate: e.birthDate ?? '',
          address: e.address ?? '',
          notes: e.notes ?? '',
          active: e.active,
        })}
        toBody={(v, editing) => ({
          employeeNumber: v.employeeNumber,
          fullName: v.fullName,
          phone: emptyToNull(v.phone),
          branchId: toId(v.branchId),
          categoryId: toId(v.categoryId),
          position: emptyToNull(v.position),
          joinDate: editing ? editing.joinDate : null,
          endDate: editing ? emptyToNull(v.endDate) : null,
          devicePin: emptyToNull(v.devicePin),
          email: emptyToNull(v.email),
          gender: emptyToNull(v.gender),
          birthDate: emptyToNull(v.birthDate),
          address: emptyToNull(v.address),
          notes: emptyToNull(v.notes),
          active: editing ? v.active : true,
          account:
            !editing && hasAccount(v)
              ? { username: text(v.username), password: v.password, role: isOwner ? v.accountRole : 'EMPLOYEE' }
              : null,
        })}
        onSaved={(saved, editing, v) => {
          if (!editing && hasAccount(v)) {
            setMessage({
              kind: 'created',
              name: saved.fullName,
              username: text(v.username),
              password: String(v.password),
              phone: saved.phone,
            })
          }
        }}
      />
      {message && <CredentialMessageDialog message={message} onClose={() => setMessage(null)} />}
    </>
  )
}
