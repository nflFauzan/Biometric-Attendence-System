import { useQuery } from '@tanstack/react-query'
import { AlertTriangle, ArrowLeft, KeyRound, UserPlus, UserRound } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { ActiveBadge } from '@/components/CrudPage'
import { BranchAccessCard } from '@/components/BranchAccessCard'
import { CreateAccountDialog } from '@/components/CreateAccountDialog'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { FingerprintSection } from '@/components/FingerprintSection'
import { PageHeader } from '@/components/PageHeader'
import { ResetPasswordDialog } from '@/components/ResetPasswordDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ApiError, api } from '@/lib/api'
import { formatDate, formatDateTime } from '@/lib/format'
import { GENDER_LABELS, type Employee, type UserAccount } from '@/lib/types'

/**
 * Detail satu karyawan untuk Owner dan HR/Finance: data kepegawaian dan status akun web.
 * Mengubah data tetap lewat dialog di halaman daftar karyawan.
 */
export function EmployeeDetailPage() {
  const { id } = useParams()
  const { me } = useAuth()
  const employee = useQuery({
    queryKey: ['/api/employees', id],
    queryFn: () => api<Employee>(`/api/employees/${id}`),
    retry: false,
  })
  // HR/Finance hanya menerima akun karyawan dari backend; itu cukup untuk mencari akun milik karyawan ini.
  const accounts = useQuery({ queryKey: ['/api/users'], queryFn: () => api<UserAccount[]>('/api/users') })
  const account = accounts.data?.find((user) => String(user.employeeId) === id)
  const [dialog, setDialog] = useState<'create' | 'reset' | null>(null)
  const canManage = me?.role === 'OWNER' || me?.role === 'HR_FINANCE'

  const back = (
    <Button variant="outline" asChild>
      <Link to="/karyawan">
        <ArrowLeft />
        Kembali ke daftar
      </Link>
    </Button>
  )

  if (employee.error instanceof ApiError && employee.error.status === 404) {
    return (
      <section className="rounded-lg border glass">
        <EmptyState message="Karyawan tidak ditemukan." action={back} />
      </section>
    )
  }

  const data = employee.data
  const rows: [string, string | null | undefined][] = [
    ['No. karyawan', data?.employeeNumber],
    ['Cabang', data?.branchName],
    ['Kategori', data?.categoryName],
    ['Jabatan', data?.position],
    ['No. HP', data?.phone],
    ['Tanggal masuk', data ? formatDate(data.joinDate) : null],
    ['Tanggal keluar', data?.endDate ? formatDate(data.endDate) : null],
    ['Email', data?.email],
    ['Jenis kelamin', data?.gender ? GENDER_LABELS[data.gender] : null],
    ['Tanggal lahir', data?.birthDate ? formatDate(data.birthDate) : null],
    ['Alamat', data?.address],
    ['Catatan', data?.notes],
  ]

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={data?.fullName ?? 'Detail karyawan'}
        description={data ? <ActiveBadge active={data.active} /> : undefined}
        actions={back}
      />

      {employee.error && <ErrorAlert message={employee.error.message} />}

      <div className="grid gap-4 lg:grid-cols-3">
        <section className="rounded-lg border glass lg:col-span-2" aria-labelledby="data-karyawan">
          <h2 id="data-karyawan" className="flex items-center gap-2 border-b px-5 py-3.5 text-base font-semibold">
            <UserRound className="size-4 text-muted-foreground" aria-hidden="true" />
            Data kepegawaian
          </h2>
          {employee.isPending && <SkeletonLines count={4} />}
          {data && (
            <dl className="divide-y">
              {rows.map(([label, value]) => (
                <div key={label} className="flex items-center justify-between gap-4 px-5 py-3">
                  <dt className="text-sm text-muted-foreground">{label}</dt>
                  <dd className="text-right text-sm font-semibold break-words">{value || '—'}</dd>
                </div>
              ))}
            </dl>
          )}
        </section>

        <section className="rounded-lg border glass" aria-labelledby="akun-web">
          <h2 id="akun-web" className="flex items-center gap-2 border-b px-5 py-3.5 text-base font-semibold">
            <KeyRound className="size-4 text-muted-foreground" aria-hidden="true" />
            Akun web
          </h2>
          {accounts.isPending && <SkeletonLines count={2} />}
          {accounts.error && <ErrorAlert className="m-4" message={accounts.error.message} />}
          {accounts.data && !account && (
            <EmptyState
              message="Karyawan ini belum punya akun web."
              action={
                data && (
                  <Button variant="secondary" size="sm" onClick={() => setDialog('create')}>
                    <UserPlus />
                    Buat akun
                  </Button>
                )
              }
            />
          )}
          {account && (
            <dl className="divide-y">
              <div className="flex items-center justify-between gap-4 px-5 py-3">
                <dt className="text-sm text-muted-foreground">Username</dt>
                <dd className="font-mono text-[13px]">{account.username}</dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-5 py-3">
                <dt className="text-sm text-muted-foreground">Status</dt>
                <dd>
                  <ActiveBadge active={account.active} />
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-5 py-3">
                <dt className="text-sm text-muted-foreground">Login terakhir</dt>
                <dd className="text-sm tabular-nums">{formatDateTime(account.lastLoginAt)}</dd>
              </div>
              {account.mustChangePassword && (
                <div className="px-5 py-3">
                  <Badge variant="warning">
                    <KeyRound />
                    Wajib ganti password saat login
                  </Badge>
                </div>
              )}
              {/* F&A hanya menerima akun karyawan dari backend, jadi tombol ini tidak muncul untuk akun Owner/F&A. */}
              <div className="px-5 py-3">
                <Button variant="secondary" size="sm" onClick={() => setDialog('reset')}>
                  <KeyRound />
                  Reset password
                </Button>
              </div>
            </dl>
          )}
          {data && !data.active && account?.active && (
            <p className="m-4 flex gap-2 rounded-md border border-warning/25 bg-warning-bg px-3 py-2 text-sm text-warning">
              <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
              Karyawan sudah nonaktif, tetapi akun webnya masih aktif. Nonaktifkan di menu Akun jika perlu.
            </p>
          )}
        </section>

        {data && <BranchAccessCard employee={data} readOnly={!canManage} />}

        {data && <FingerprintSection employee={data} />}
      </div>

      {dialog === 'create' && data && <CreateAccountDialog employee={data} onClose={() => setDialog(null)} />}
      {dialog === 'reset' && account && (
        <ResetPasswordDialog account={account} employee={data} onClose={() => setDialog(null)} />
      )}
    </div>
  )
}
