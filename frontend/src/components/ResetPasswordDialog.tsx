import { useRef, useState } from 'react'

import { CredentialMessageDialog } from '@/components/CredentialMessageDialog'
import { FormDialog } from '@/components/FormDialog'
import { api } from '@/lib/api'
import { suggestPassword } from '@/lib/credentials'
import type { Employee, UserAccount } from '@/lib/types'

/** Reset password dengan usulan `nama-ID-takka`, lalu pesan WA versi reset (K-133). */
export function ResetPasswordDialog({
  account,
  employee,
  onClose,
}: {
  account: UserAccount
  employee?: Employee
  onClose: () => void
}) {
  const [password, setPassword] = useState<string | null>(null)
  // FormDialog menutup dirinya setelah simpan; saat itu jendela pesan yang harus tetap tampil.
  const saved = useRef(false)

  if (password !== null) {
    return (
      <CredentialMessageDialog
        message={{
          kind: 'reset',
          name: employee?.fullName ?? account.employeeName ?? account.username,
          username: account.username,
          password,
          phone: employee?.phone,
        }}
        onClose={onClose}
      />
    )
  }

  return (
    <FormDialog
      open
      title={`Reset password: ${account.username}`}
      fields={[
        {
          name: 'newPassword',
          label: 'Password sementara',
          required: true,
          hint: 'Wajib diganti saat login berikutnya.',
        },
      ]}
      initialValues={{ newPassword: suggestPassword(account.username, employee?.employeeNumber) }}
      onClose={() => !saved.current && onClose()}
      onSubmit={async (v) => {
        const newPassword = String(v.newPassword)
        await api(`/api/users/${account.id}/reset-password`, { method: 'POST', body: { newPassword } })
        saved.current = true
        setPassword(newPassword)
      }}
    />
  )
}
