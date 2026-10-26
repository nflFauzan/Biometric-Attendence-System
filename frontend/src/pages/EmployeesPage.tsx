import { FileSpreadsheet } from 'lucide-react'
import { Link } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { Button } from '@/components/ui/button'
import { emptyToNull, toId } from '@/lib/form'
import { formatDate } from '@/lib/format'
import { GENDER_LABELS, type Employee, type Gender } from '@/lib/types'
import { useBranchOptions, useCategoryOptions } from '@/pages/options'

const GENDER_OPTIONS = (Object.keys(GENDER_LABELS) as Gender[]).map((g) => ({ value: g, label: GENDER_LABELS[g] }))

export function EmployeesPage() {
  const { me } = useAuth()
  const branchOptions = useBranchOptions()
  const categoryOptions = useCategoryOptions()
  return (
    <CrudPage<Employee>
      title="Karyawan"
      description="Data karyawan tidak dihapus. Untuk karyawan keluar, isi tanggal keluar lalu nonaktifkan."
      endpoint="/api/employees"
      // Data karyawan hanya diubah Owner (K-128); HR/Finance tetap melihat daftar dan detail.
      readOnly={me?.role !== 'OWNER'}
      toolbar={
        me?.role === 'OWNER' && (
          <div>
            <Button asChild variant="outline">
              <Link to="/karyawan/impor">
                <FileSpreadsheet />
                Impor dari Excel
              </Link>
            </Button>
          </div>
        )
      }
      columns={[
        {
          header: 'Nama',
          cell: (e) => (
            <Link to={`/karyawan/${e.id}`} className="text-steel-700 underline-offset-4 hover:underline dark:text-foreground">
              {e.fullName}
            </Link>
          ),
          role: 'primary',
        },
        { header: 'No. karyawan', cell: (e) => e.employeeNumber, mono: true },
        { header: 'PIN mesin', cell: (e) => e.devicePin ?? '—', mono: true },
        { header: 'Cabang', cell: (e) => e.branchName },
        { header: 'Kategori', cell: (e) => e.categoryName },
        { header: 'Tgl masuk', cell: (e) => formatDate(e.joinDate) },
        { header: 'Status', cell: (e) => <ActiveBadge active={e.active} />, role: 'status' },
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
        {
          name: 'devicePin',
          label: 'PIN mesin',
          hint: 'User ID karyawan di mesin absensi (sama di semua mesin). Scan dengan PIN ini otomatis terhubung.',
        },
        { name: 'email', label: 'Email' },
        { name: 'gender', label: 'Jenis kelamin', type: 'select', options: GENDER_OPTIONS },
        { name: 'birthDate', label: 'Tanggal lahir', type: 'date' },
        { name: 'address', label: 'Alamat' },
        { name: 'notes', label: 'Catatan' },
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
        devicePin: '',
        email: '',
        gender: '',
        birthDate: '',
        address: '',
        notes: '',
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
        devicePin: e.devicePin ?? '',
        email: e.email ?? '',
        gender: e.gender ?? '',
        birthDate: e.birthDate ?? '',
        address: e.address ?? '',
        notes: e.notes ?? '',
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
        devicePin: emptyToNull(v.devicePin),
        email: emptyToNull(v.email),
        gender: emptyToNull(v.gender),
        birthDate: emptyToNull(v.birthDate),
        address: emptyToNull(v.address),
        notes: emptyToNull(v.notes),
        active: v.active,
      })}
    />
  )
}
