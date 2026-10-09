import { useQuery } from '@tanstack/react-query'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { api } from '@/lib/api'
import type { Employee } from '@/lib/types'

export function ProfilePage() {
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/me/employee'],
    queryFn: () => api<Employee>('/api/me/employee'),
  })

  const rows: [string, string | null | undefined][] = [
    ['No. karyawan', data?.employeeNumber],
    ['Nama', data?.fullName],
    ['Cabang', data?.branchName],
    ['Kategori', data?.categoryName],
    ['Jabatan', data?.position],
    ['No. HP', data?.phone],
    ['Tanggal masuk', data?.joinDate],
  ]

  return (
    <Card className="max-w-lg">
      <CardHeader>
        <CardTitle>Profil Saya</CardTitle>
      </CardHeader>
      <CardContent>
        {isPending && <p className="text-sm">Memuat…</p>}
        {error && <p className="text-sm text-destructive">{error.message}</p>}
        {data && (
          <dl className="grid grid-cols-[max-content_1fr] gap-x-6 gap-y-2 text-sm">
            {rows.map(([label, value]) => (
              <div key={label} className="contents">
                <dt className="text-muted-foreground">{label}</dt>
                <dd>{value ?? '—'}</dd>
              </div>
            ))}
          </dl>
        )}
        <p className="mt-6 text-sm text-muted-foreground">Pengajuan izin/cuti dan riwayat absensi menyusul di fase berikutnya.</p>
      </CardContent>
    </Card>
  )
}
