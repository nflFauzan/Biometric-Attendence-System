import { useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'

import { CredentialMessageDialog } from '@/components/CredentialMessageDialog'
import { FormDialog } from '@/components/FormDialog'
import { api } from '@/lib/api'
import { followSuggestions, suggestPassword, suggestUsername, textOf, type SuggestionRule } from '@/lib/credentials'
import type { Employee } from '@/lib/types'

/** Password ikut username selama belum diubah manual. */
const RULES = (employee: Employee): SuggestionRule[] => [
  ['password', (v) => suggestPassword(textOf(v.username), employee.employeeNumber)],
]

/** Buat akun Karyawan dari detail karyawan dengan usulan kredensial, lalu pesan WA (K-133). */
export function CreateAccountDialog({ employee, onClose }: { employee: Employee; onClose: () => void }) {
  const queryClient = useQueryClient()
  const [created, setCreated] = useState<{ username: string; password: string } | null>(null)
  // FormDialog menutup dirinya setelah simpan; saat itu jendela pesan yang harus tetap tampil.
  const saved = useRef(false)

  if (created) {
    return (
      <CredentialMessageDialog
        message={{ kind: 'created', name: employee.fullName, ...created, phone: employee.phone }}
        onClose={onClose}
      />
    )
  }

  const username = suggestUsername(employee.fullName)
  return (
    <FormDialog
      open
      title={`Buat akun: ${employee.fullName}`}
      description="Akun dibuat dengan role Karyawan. Role lain diatur Owner di menu Akun."
      fields={[
        { name: 'username', label: 'Username', required: true },
        { name: 'password', label: 'Password sementara', required: true, hint: 'Wajib diganti saat login pertama.' },
      ]}
      initialValues={{ username, password: suggestPassword(username, employee.employeeNumber) }}
      derive={(next, prev) => followSuggestions(next, prev, RULES(employee))}
      onClose={() => !saved.current && onClose()}
      onSubmit={async (v) => {
        const credentials = { username: textOf(v.username), password: String(v.password) }
        await api('/api/users', { method: 'POST', body: { ...credentials, role: 'EMPLOYEE', employeeId: employee.id } })
        await queryClient.invalidateQueries({ queryKey: ['/api/users'] })
        saved.current = true
        setCreated(credentials)
      }}
    />
  )
}
