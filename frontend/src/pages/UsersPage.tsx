import { KeyRound } from 'lucide-react'
import { useState } from 'react'

import { useAuth } from '@/auth/AuthContext'
import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { FormDialog } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { toId } from '@/lib/form'
import { formatDateTime } from '@/lib/format'
import { ROLE_LABELS, type Role, type UserAccount } from '@/lib/types'
import { useEmployeeOptions } from '@/pages/options'

export function UsersPage() {
  const { me } = useAuth()
  const employeeOptions = useEmployeeOptions()
  const [resetting, setResetting] = useState<UserAccount | null>(null)

  const allowedRoles: Role[] = me?.role === 'OWNER' ? ['OWNER', 'HR_FINANCE', 'EMPLOYEE'] : ['EMPLOYEE']
  const roleOptions = allowedRoles.map((role) => ({ value: role, label: ROLE_LABELS[role] }))

  return (
    <>
      <CrudPage<UserAccount>
        title="Akun"
        description={
          me?.role === 'OWNER'
            ? 'Akun baru wajib mengganti password saat login pertama.'
            : 'Finance & Accounting hanya dapat mengelola akun karyawan.'
        }
        endpoint="/api/users"
        columns={[
          { header: 'Username', cell: (u) => u.username, role: 'primary' },
          { header: 'Role', cell: (u) => ROLE_LABELS[u.role] },
          { header: 'Karyawan', cell: (u) => u.employeeName ?? '—' },
          { header: 'Login terakhir', cell: (u) => formatDateTime(u.lastLoginAt) },
          { header: 'Status', cell: (u) => <ActiveBadge active={u.active} />, role: 'status' },
        ]}
        fields={(editing) =>
          editing
            ? [
                { name: 'role', label: 'Role', type: 'select', options: roleOptions, required: true },
                { name: 'employeeId', label: 'Karyawan', type: 'select', options: employeeOptions },
                { name: 'active', label: 'Aktif', type: 'checkbox' },
              ]
            : [
                { name: 'username', label: 'Username', required: true },
                { name: 'password', label: 'Password awal', type: 'password', required: true },
                { name: 'role', label: 'Role', type: 'select', options: roleOptions, required: true },
                { name: 'employeeId', label: 'Karyawan (wajib untuk role Karyawan)', type: 'select', options: employeeOptions },
              ]
        }
        emptyValues={{ username: '', password: '', role: 'EMPLOYEE', employeeId: '' }}
        toValues={(u) => ({ role: u.role, employeeId: u.employeeId ? String(u.employeeId) : '', active: u.active })}
        toBody={(v, editing) =>
          editing
            ? { role: v.role, employeeId: toId(v.employeeId), active: v.active }
            : { username: v.username, password: v.password, role: v.role, employeeId: toId(v.employeeId) }
        }
        rowActions={(u) => (
          <Button variant="ghost" size="sm" onClick={() => setResetting(u)}>
            <KeyRound />
            Reset password
          </Button>
        )}
      />
      {resetting && (
        <FormDialog
          open
          title={`Reset password: ${resetting.username}`}
          fields={[{ name: 'newPassword', label: 'Password sementara', type: 'password', required: true }]}
          initialValues={{ newPassword: '' }}
          onClose={() => setResetting(null)}
          onSubmit={(v) =>
            api(`/api/users/${resetting.id}/reset-password`, { method: 'POST', body: { newPassword: v.newPassword } })
          }
        />
      )}
    </>
  )
}
