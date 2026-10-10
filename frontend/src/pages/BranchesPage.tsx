import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { emptyToNull } from '@/lib/form'
import type { Branch } from '@/lib/types'

export function BranchesPage() {
  return (
    <CrudPage<Branch>
      title="Cabang"
      description="Cabang tidak dihapus; nonaktifkan bila sudah tidak dipakai."
      endpoint="/api/branches"
      columns={[
        { header: 'Nama', cell: (b) => b.name, role: 'primary' },
        { header: 'Kode', cell: (b) => b.code, mono: true },
        { header: 'Zona waktu', cell: (b) => b.timezone },
        { header: 'Status', cell: (b) => <ActiveBadge active={b.active} />, role: 'status' },
      ]}
      fields={() => [
        { name: 'code', label: 'Kode', required: true },
        { name: 'name', label: 'Nama', required: true },
        { name: 'address', label: 'Alamat' },
        { name: 'timezone', label: 'Zona waktu', required: true, placeholder: 'Asia/Jakarta' },
        { name: 'active', label: 'Aktif', type: 'checkbox' },
      ]}
      emptyValues={{ code: '', name: '', address: '', timezone: 'Asia/Jakarta', active: true }}
      toValues={(b) => ({ code: b.code, name: b.name, address: b.address ?? '', timezone: b.timezone, active: b.active })}
      toBody={(v) => ({
        code: v.code,
        name: v.name,
        address: emptyToNull(v.address),
        timezone: v.timezone,
        active: v.active,
      })}
    />
  )
}
