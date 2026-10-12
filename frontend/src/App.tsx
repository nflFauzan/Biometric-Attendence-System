import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { AppLayout } from '@/components/AppLayout'
import type { Role } from '@/lib/types'
import { homePathFor } from '@/navigation'
import { AttendanceEmployeePage } from '@/pages/AttendanceEmployeePage'
import { AttendancePage } from '@/pages/AttendancePage'
import { AuditLogPage } from '@/pages/AuditLogPage'
import { BranchesPage } from '@/pages/BranchesPage'
import { CalculationPreviewPage } from '@/pages/CalculationPreviewPage'
import { CategoriesPage } from '@/pages/CategoriesPage'
import { ChangePasswordPage } from '@/pages/ChangePasswordPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { DevicesPage } from '@/pages/DevicesPage'
import { EmployeeDetailPage } from '@/pages/EmployeeDetailPage'
import { EmployeesPage } from '@/pages/EmployeesPage'
import { HolidaysPage } from '@/pages/HolidaysPage'
import { LoginPage } from '@/pages/LoginPage'
import { ProfilePage } from '@/pages/ProfilePage'
import { UnmatchedScansPage } from '@/pages/UnmatchedScansPage'
import { UsersPage } from '@/pages/UsersPage'
import { WorkRulesPage } from '@/pages/WorkRulesPage'

const MANAGERS: Role[] = ['OWNER', 'HR_FINANCE']

/** Harus login; kalau wajib ganti password, arahkan ke halaman ganti password dulu. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading } = useAuth()
  if (loading) {
    return <p className="p-6 text-sm">Memuat…</p>
  }
  if (!me) {
    return <Navigate to="/login" replace />
  }
  if (me.mustChangePassword) {
    return <Navigate to="/ganti-password" replace />
  }
  return children
}

function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { me } = useAuth()
  if (!me || !roles.includes(me.role)) {
    return <Navigate to={me ? homePathFor(me.role) : '/login'} replace />
  }
  return children
}

function ChangePasswordRoute() {
  const { me, loading } = useAuth()
  if (loading) {
    return null
  }
  return me ? <ChangePasswordPage /> : <Navigate to="/login" replace />
}

function App() {
  const managers = (page: ReactNode) => <RequireRole roles={MANAGERS}>{page}</RequireRole>
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/ganti-password" element={<ChangePasswordRoute />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={managers(<DashboardPage />)} />
        <Route path="absensi" element={managers(<AttendancePage />)} />
        <Route path="absensi/karyawan/:id" element={managers(<AttendanceEmployeePage />)} />
        <Route path="absensi/scan-tak-dikenal" element={managers(<UnmatchedScansPage />)} />
        <Route path="karyawan" element={managers(<EmployeesPage />)} />
        <Route path="karyawan/:id" element={managers(<EmployeeDetailPage />)} />
        <Route path="cabang" element={managers(<BranchesPage />)} />
        <Route path="kategori" element={managers(<CategoriesPage />)} />
        <Route path="mesin" element={managers(<DevicesPage />)} />
        <Route path="akun" element={managers(<UsersPage />)} />
        <Route path="aturan-kerja" element={managers(<WorkRulesPage />)} />
        <Route path="kalender-libur" element={managers(<HolidaysPage />)} />
        <Route path="simulasi" element={managers(<CalculationPreviewPage />)} />
        <Route path="audit-log" element={<RequireRole roles={['OWNER']}><AuditLogPage /></RequireRole>} />
        <Route path="profil" element={<RequireRole roles={['EMPLOYEE']}><ProfilePage /></RequireRole>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
