import type { ReactNode } from 'react'
import { Navigate, Outlet, Route, Routes, useLocation, useParams } from 'react-router'

import { useAuth } from '@/auth/AuthContext'
import { AppLayout } from '@/components/AppLayout'
import { RedirectKeepSearch } from '@/components/RedirectKeepSearch'
import { SettingsSubpage } from '@/components/SettingsSubpage'
import { withNext } from '@/lib/safeNext'
import type { Role } from '@/lib/types'
import { homePathFor } from '@/navigation'
import { AttendanceHubPage } from '@/pages/AttendanceHubPage'
import { AuditLogPage } from '@/pages/AuditLogPage'
import { BranchesPage } from '@/pages/BranchesPage'
import { CalculationPreviewPage } from '@/pages/CalculationPreviewPage'
import { CategoriesPage } from '@/pages/CategoriesPage'
import { ChangePasswordPage } from '@/pages/ChangePasswordPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { DesignPreviewPage } from '@/pages/DesignPreviewPage'
import { DevicesPage } from '@/pages/DevicesPage'
import { EmployeeDetailPage } from '@/pages/EmployeeDetailPage'
import { EmployeesPage } from '@/pages/EmployeesPage'
import { HolidaysPage } from '@/pages/HolidaysPage'
import { LeaveDetailPage } from '@/pages/LeaveDetailPage'
import { LeaveRequestsPage } from '@/pages/LeaveRequestsPage'
import { LeaveWizardPage } from '@/pages/LeaveWizardPage'
import { LeaveTypesPage } from '@/pages/LeaveTypesPage'
import { LoginPage } from '@/pages/LoginPage'
import { MyAttendancePage } from '@/pages/MyAttendancePage'
import { MyLeavePage } from '@/pages/MyLeavePage'
import { PayrollPage } from '@/pages/PayrollPage'
import { HomePage } from '@/pages/HomePage'
import { ProfilePage } from '@/pages/ProfilePage'
import { SettingsPage } from '@/pages/SettingsPage'
import { VerifyLetterPage } from '@/pages/VerifyLetterPage'
import { UsersPage } from '@/pages/UsersPage'
import { WorkRulesPage } from '@/pages/WorkRulesPage'

const MANAGERS: Role[] = ['OWNER', 'HR_FINANCE']

/** Alamat lama menu pengaturan yang kini berada di bawah /pengaturan (K-145). */
const LEGACY_SETTINGS = ['cabang', 'kategori', 'mesin', 'akun', 'aturan-kerja', 'kalender-libur', 'jenis-izin', 'simulasi', 'audit-log']

/** Harus login; kalau wajib ganti password, arahkan ke halaman ganti password dulu. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { me, loading, loggedOut } = useAuth()
  const location = useLocation()
  if (loading) {
    return <p className="p-6 text-sm">Memuat…</p>
  }
  // Lokasi asal dibawa lewat ?next= (bertahan saat dimuat ulang) agar login/ganti password kembali ke sini.
  if (!me) {
    // Keluar eksplisit selalu ke /login polos; kalau tidak, pengguna berikutnya mewarisi halaman ini.
    return <Navigate to={loggedOut ? '/login' : withNext('/login', location)} replace />
  }
  if (me.mustChangePassword) {
    return <Navigate to={withNext('/ganti-password', location)} replace />
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

function LetterRedirect({ base }: { base: string }) {
  const { id } = useParams()
  return <Navigate to={`${base}/${id}`} replace />
}

function App() {
  const managers = (page: ReactNode) => <RequireRole roles={MANAGERS}>{page}</RequireRole>
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/design-preview" element={<DesignPreviewPage />} />
      {/* Publik: tanpa login dan tanpa kerangka aplikasi (dibuka dari QR di PDF). */}
      <Route path="/verifikasi/:token" element={<VerifyLetterPage />} />
      <Route path="/ganti-password" element={<ChangePasswordRoute />} />
      {/* Halaman cetak lama diganti PDF dari server; tautan lama dialihkan ke detail pengajuan. */}
      <Route path="/izin/:id/surat" element={<LetterRedirect base="/izin" />} />
      <Route path="/izin-saya/:id/surat" element={<LetterRedirect base="/izin-saya" />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={managers(<DashboardPage />)} />
        <Route path="absensi" element={managers(<AttendanceHubPage />)} />
        {/* Rincian absensi per karyawan kini tab di halaman karyawan (K-145). */}
        <Route path="absensi/karyawan/:id" element={<RedirectKeepSearch to={(p) => `/karyawan/${p.id}?tab=absensi`} />} />
        {/* Alamat lama sub menu absensi (K-145): sekarang tab di /absensi, query lama ikut dibawa. */}
        <Route path="absensi/perlu-ditinjau" element={<RedirectKeepSearch to="/absensi?tab=bereskan" />} />
        <Route path="absensi/scan-tertahan" element={<RedirectKeepSearch to="/absensi?tab=bereskan" />} />
        <Route path="absensi/scan-tak-dikenal" element={<RedirectKeepSearch to="/absensi?tab=bereskan" />} />
        <Route path="absensi/lembur" element={<RedirectKeepSearch to="/absensi?tab=lembur" />} />
        <Route path="gaji" element={managers(<PayrollPage />)} />
        <Route path="laporan" element={<RedirectKeepSearch to="/gaji" />} />
        <Route path="kasbon" element={<RedirectKeepSearch to="/gaji?tab=kasbon" />} />
        <Route path="izin" element={managers(<LeaveRequestsPage />)} />
        <Route path="izin/:id" element={managers(<LeaveDetailPage mode="manager" />)} />
        {/* Pengajuan sendiri: backend memastikan akun terhubung ke data karyawan. */}
        <Route path="beranda" element={<RequireRole roles={['EMPLOYEE']}><HomePage /></RequireRole>} />
        <Route path="izin-saya" element={<MyLeavePage />} />
        <Route path="izin-saya/baru" element={<LeaveWizardPage />} />
        <Route path="izin-saya/:id/ubah" element={<LeaveWizardPage />} />
        <Route path="izin-saya/:id" element={<LeaveDetailPage mode="mine" />} />
        {/* Absensi Saya: karyawan hanya melihat absensinya sendiri (K-127); alamat rekan kerja lama dialihkan. */}
        <Route path="absensi-saya" element={<MyAttendancePage />} />
        <Route path="rekan-kerja" element={<Navigate to="/absensi-saya" replace />} />
        <Route path="rekan-kerja/:id" element={<Navigate to="/absensi-saya" replace />} />
        <Route path="karyawan" element={managers(<EmployeesPage />)} />
        {/* Impor karyawan dari template client membuat data dan akun sekaligus: hanya Owner (K-130). */}
        <Route path="karyawan/:id" element={managers(<EmployeeDetailPage />)} />
        {/* Pengaturan (K-145): halaman yang jarang dipakai, hak akses sama dengan saat masih menjadi menu sendiri. */}
        <Route path="pengaturan" element={managers(<Outlet />)}>
          <Route index element={<SettingsPage />} />
          <Route element={<SettingsSubpage />}>
            <Route path="cabang" element={<BranchesPage />} />
            <Route path="kategori" element={<CategoriesPage />} />
            <Route path="mesin" element={<DevicesPage />} />
            <Route path="akun" element={<UsersPage />} />
            <Route path="aturan-kerja" element={<WorkRulesPage />} />
            <Route path="kalender-libur" element={<HolidaysPage />} />
            <Route path="simulasi" element={<CalculationPreviewPage />} />
            <Route path="jenis-izin" element={<RequireRole roles={['OWNER']}><LeaveTypesPage /></RequireRole>} />
            <Route path="audit-log" element={<RequireRole roles={['OWNER']}><AuditLogPage /></RequireRole>} />
          </Route>
        </Route>
        {LEGACY_SETTINGS.map((slug) => (
          <Route key={slug} path={slug} element={<RedirectKeepSearch to={`/pengaturan/${slug}`} />} />
        ))}
        {/* Profil: semua role, agar Owner/HR juga bisa mengatur spesimen tanda tangan (K-115). */}
        <Route path="profil" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
