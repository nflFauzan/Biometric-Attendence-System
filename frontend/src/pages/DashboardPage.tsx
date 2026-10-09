import { useQuery } from '@tanstack/react-query'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { api } from '@/lib/api'
import type { Branch, Device, Employee } from '@/lib/types'

function Stat({ label, value }: { label: string; value: number | undefined }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium text-muted-foreground">{label}</CardTitle>
      </CardHeader>
      <CardContent className="text-3xl font-semibold">{value ?? '…'}</CardContent>
    </Card>
  )
}

export function DashboardPage() {
  const employees = useQuery({ queryKey: ['/api/employees'], queryFn: () => api<Employee[]>('/api/employees') })
  const branches = useQuery({ queryKey: ['/api/branches'], queryFn: () => api<Branch[]>('/api/branches') })
  const devices = useQuery({ queryKey: ['/api/devices'], queryFn: () => api<Device[]>('/api/devices') })

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-xl font-semibold">Dashboard</h1>
      <div className="grid gap-4 sm:grid-cols-3">
        <Stat label="Karyawan aktif" value={employees.data?.filter((e) => e.active).length} />
        <Stat label="Cabang aktif" value={branches.data?.filter((b) => b.active).length} />
        <Stat label="Mesin terdaftar" value={devices.data?.length} />
      </div>
      <p className="text-sm text-muted-foreground">
        Kehadiran real-time, keterlambatan, dan lembur akan tampil di sini setelah modul absensi (Fase 3).
      </p>
    </div>
  )
}
