import { useState } from 'react'
import { Link } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { CredentialMessageDialog, type CredentialMessage } from '@/components/CredentialMessageDialog'
import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { EmployeeFormDialog } from '@/components/EmployeeFormDialog'
import type { FormValues } from '@/components/FormDialog'
import { textOf as text } from '@/lib/credentials'
import { emptyToNull, toId } from '@/lib/form'
import { formatDate } from '@/lib/format'
import type { Employee } from '@/lib/types'

const hasAccount = (v: FormValues) => v.createAccount === true

export function EmployeesPage() {
  const { me } = useAuth()
  const [message, setMessage] = useState<CredentialMessage | null>(null)
  const isOwner = me?.role === 'OWNER'

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
        // K-136: form berbagian; K-134: tanggal masuk diisi otomatis saat tambah dan tidak diubah lewat form.
        renderForm={(form) => <EmployeeFormDialog form={form} isOwner={isOwner} />}
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
