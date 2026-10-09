import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import type { Category } from '@/lib/types'

export function CategoriesPage() {
  return (
    <CrudPage<Category>
      title="Kategori Karyawan"
      description="Contoh: Kantor, Supir/Kenek. Aturan gaji per kategori diatur di Pengaturan Aturan (Fase 2)."
      endpoint="/api/employee-categories"
      columns={[
        { header: 'Kode', cell: (c) => c.code },
        { header: 'Nama', cell: (c) => c.name },
        { header: 'Status', cell: (c) => <ActiveBadge active={c.active} /> },
      ]}
      fields={() => [
        { name: 'code', label: 'Kode', required: true },
        { name: 'name', label: 'Nama', required: true },
        { name: 'active', label: 'Aktif', type: 'checkbox' },
      ]}
      emptyValues={{ code: '', name: '', active: true }}
      toValues={(c) => ({ code: c.code, name: c.name, active: c.active })}
      toBody={(v) => ({ code: v.code, name: v.name, active: v.active })}
    />
  )
}
