import { useQuery } from '@tanstack/react-query'
import { KeyRound } from 'lucide-react'
import { useState } from 'react'

import { useAuth } from '@/auth/AuthContext'
import { CredentialMessageDialog, type CredentialMessage } from '@/components/CredentialMessageDialog'
import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { ResetPasswordDialog } from '@/components/ResetPasswordDialog'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { followSuggestions, suggestPassword, suggestUsername, textOf, type SuggestionRule } from '@/lib/credentials'
import { toId } from '@/lib/form'
import { formatDateTime } from '@/lib/format'
import { ROLE_LABELS, type Employee, type Role, type UserAccount } from '@/lib/types'

export function UsersPage() {
  const { me } = useAuth()
  const employees = useQuery({ queryKey: ['/api/employees'], queryFn: () => api<Employee[]>('/api/employees') })
  const employeeOptions = (employees.data ?? []).map((e) => ({ value: String(e.id), label: `${e.fullName} (${e.employeeNumber})` }))
  const employeeById = (id: unknown) => employees.data?.find((e) => String(e.id) === String(id ?? ''))
  const [resetting, setResetting] = useState<UserAccount | null>(null)
  const [message, setMessage] = useState<CredentialMessage | null>(null)

  const allowedRoles: Role[] = me?.role === 'OWNER' ? ['OWNER', 'HR_FINANCE', 'EMPLOYEE'] : ['EMPLOYEE']
  const roleOptions = allowedRoles.map((role) => ({ value: role, label: ROLE_LABELS[role] }))

  // K-133: username dan password mengikuti karyawan yang dipilih selama belum diubah manual.
  const suggestions: SuggestionRule[] = [
    ['username', (v) => suggestUsername(employeeById(v.employeeId)?.fullName ?? '')],
    ['password', (v) => suggestPassword(textOf(v.username), employeeById(v.employeeId)?.employeeNumber)],
  ]

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
                { name: 'employeeId', label: 'Karyawan (wajib untuk role Karyawan)', type: 'select', options: employeeOptions },
                { name: 'username', label: 'Username', required: true },
                {
                  name: 'password',
                  label: 'Password sementara',
                  required: true,
                  hint: 'Wajib diganti saat login pertama.',
                },
                { name: 'role', label: 'Role', type: 'select', options: roleOptions, required: true },
              ]
        }
        derive={(next, prev, editing) => (editing ? next : followSuggestions(next, prev, suggestions))}
        emptyValues={{ username: '', password: '', role: 'EMPLOYEE', employeeId: '' }}
        toValues={(u) => ({ role: u.role, employeeId: u.employeeId ? String(u.employeeId) : '', active: u.active })}
        toBody={(v, editing) =>
          editing
            ? { role: v.role, employeeId: toId(v.employeeId), active: v.active }
            : { username: textOf(v.username), password: v.password, role: v.role, employeeId: toId(v.employeeId) }
        }
        onSaved={(saved, editing, v) => {
          if (editing) return
          const employee = employeeById(v.employeeId)
          setMessage({
            kind: 'created',
            name: employee?.fullName ?? saved.username,
            username: saved.username,
            password: String(v.password),
            phone: employee?.phone,
          })
        }}
        rowActions={(u) => (
          <Button variant="ghost" size="sm" onClick={() => setResetting(u)}>
            <KeyRound />
            Reset password
          </Button>
        )}
      />
      {resetting && (
        <ResetPasswordDialog
          account={resetting}
          employee={employeeById(resetting.employeeId)}
          onClose={() => setResetting(null)}
        />
      )}
      {message && <CredentialMessageDialog message={message} onClose={() => setMessage(null)} />}
    </>
  )
}
