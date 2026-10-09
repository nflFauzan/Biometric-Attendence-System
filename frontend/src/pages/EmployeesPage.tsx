import { ActiveBadge, CrudPage, emptyToNull, toId } from '@/components/CrudPage'
import type { Employee } from '@/lib/types'
import { useBranchOptions, useCategoryOptions } from '@/pages/options'

export function EmployeesPage() {
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()
  return (
    <CrudPage<Employee>
      title="Karyawan"
      description="Data karyawan tidak dihapus. Untuk karyawan keluar, isi tanggal keluar lalu nonaktifkan."
      endpoint="/api/employees"
      columns={[
        { header: 'No. karyawan', cell: (e) => e.employeeNumber },
        { header: 'Nama', cell: (e) => e.fullName },
        { header: 'Cabang', cell: (e) => e.branchName },
        { header: 'Kategori', cell: (e) => e.categoryName },
        { header: 'Tgl masuk', cell: (e) => e.joinDate },
        { header: 'Status', cell: (e) => <ActiveBadge active={e.active} /> },
      ]}
      fields={() => [
        { name: 'employeeNumber', label: 'No. karyawan', required: true },
        { name: 'fullName', label: 'Nama lengkap', required: true },
        { name: 'phone', label: 'No. HP' },
        { name: 'branchId', label: 'Cabang', type: 'select', options: branchOptions, required: true },
        { name: 'categoryId', label: 'Kategori', type: 'select', options: categoryOptions, required: true },
        { name: 'position', label: 'Jabatan' },
        { name: 'joinDate', label: 'Tanggal masuk', type: 'date', required: true },
        { name: 'endDate', label: 'Tanggal keluar', type: 'date' },
        { name: 'active', label: 'Aktif', type: 'checkbox' },
      ]}
      emptyValues={{
        employeeNumber: '',
        fullName: '',
        phone: '',
        branchId: '',
        categoryId: '',
        position: '',
        joinDate: '',
        endDate: '',
        active: true,
      }}
      toValues={(e) => ({
        employeeNumber: e.employeeNumber,
        fullName: e.fullName,
        phone: e.phone ?? '',
        branchId: String(e.branchId),
        categoryId: String(e.categoryId),
        position: e.position ?? '',
        joinDate: e.joinDate,
        endDate: e.endDate ?? '',
        active: e.active,
      })}
      toBody={(v) => ({
        employeeNumber: v.employeeNumber,
        fullName: v.fullName,
        phone: emptyToNull(v.phone),
        branchId: toId(v.branchId),
        categoryId: toId(v.categoryId),
        position: emptyToNull(v.position),
        joinDate: emptyToNull(v.joinDate),
        endDate: emptyToNull(v.endDate),
        active: v.active,
      })}
    />
  )
}
