import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { toId } from '@/lib/form'
import type { Device } from '@/lib/types'
import { useBranchOptions } from '@/pages/options'

export function DevicesPage() {
  const branchOptions = useBranchOptions()
  return (
    <CrudPage<Device>
      title="Mesin Absensi"
      description="Pendaftaran mesin. Koneksi mesin ke server ditambahkan di Fase 3."
      endpoint="/api/devices"
      columns={[
        { header: 'Nama', cell: (d) => d.name, role: 'primary' },
        { header: 'Serial number', cell: (d) => d.serialNumber, mono: true },
        { header: 'Cabang', cell: (d) => d.branchName },
        { header: 'Status', cell: (d) => <ActiveBadge active={d.active} />, role: 'status' },
      ]}
      fields={() => [
        { name: 'name', label: 'Nama', required: true },
        { name: 'serialNumber', label: 'Serial number', required: true },
        { name: 'branchId', label: 'Cabang', type: 'select', options: branchOptions, required: true },
        { name: 'active', label: 'Aktif', type: 'checkbox' },
      ]}
      emptyValues={{ name: '', serialNumber: '', branchId: '', active: true }}
      toValues={(d) => ({ name: d.name, serialNumber: d.serialNumber, branchId: String(d.branchId), active: d.active })}
      toBody={(v) => ({ name: v.name, serialNumber: v.serialNumber, branchId: toId(v.branchId), active: v.active })}
    />
  )
}
