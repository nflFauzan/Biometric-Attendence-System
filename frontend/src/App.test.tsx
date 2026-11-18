import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, useLocation } from 'react-router'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'


function LocationProbe() {
  const l = useLocation()
  return <span data-testid="loc">{l.pathname + l.search}</span>
}

function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
          <LocationProbe />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const me = (role: Me['role'], mustChangePassword = false): Me => ({
  username: 'pengguna',
  role,
  employeeId: role === 'EMPLOYEE' ? 1 : null,
  employeeName: role === 'EMPLOYEE' ? 'Siti' : null,
  mustChangePassword,
})

describe('App', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('mengarahkan pengguna yang belum login ke halaman login', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 401 }) })
    renderApp('/karyawan')
    expect(await screen.findByRole('button', { name: 'Masuk' })).toBeInTheDocument()
  })

  it('guard membawa lokasi asal lewat next ke halaman login', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 401 }) })
    renderApp('/izin/123?tab=a')
    expect(await screen.findByRole('button', { name: 'Masuk' })).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent('/login?next=%2Fizin%2F123%3Ftab%3Da')
  })

  it('akun wajib ganti password membawa lokasi asal lewat next', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 200, body: me('OWNER', true) }) })
    renderApp('/izin/123')
    expect(await screen.findByText('Anda wajib mengganti password sebelum melanjutkan.')).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent('/ganti-password?next=%2Fizin%2F123')
  })

  it('memaksa ganti password bila akun baru', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 200, body: me('OWNER', true) }) })
    renderApp('/')
    expect(await screen.findByText('Anda wajib mengganti password sebelum melanjutkan.')).toBeInTheDocument()
  })

  it('kembali ke halaman login dengan pemberitahuan saat sesi berakhir di tengah pemakaian', async () => {
    let sessionAlive = true
    mockFetch({
      'GET /api/auth/me': () => (sessionAlive ? { status: 200, body: me('HR_FINANCE') } : { status: 401 }),
      'GET /api/branches': () => {
        // Sesi dicabut di backend (mis. password di-reset) sebelum halaman sempat memuat data.
        sessionAlive = false
        return { status: 401, body: { status: 401, detail: 'Silakan login terlebih dahulu' } }
      },
    })
    renderApp('/cabang')
    expect(await screen.findByRole('button', { name: 'Masuk' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Sesi Anda sudah berakhir. Silakan masuk kembali.')
    expect(screen.getByTestId('loc')).toHaveTextContent('/login?next=%2Fpengaturan%2Fcabang')
  })

  it('login pertama tanpa sesi tidak menampilkan pemberitahuan sesi berakhir', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 401 }) })
    renderApp('/')
    expect(await screen.findByRole('button', { name: 'Masuk' })).toBeInTheDocument()
    expect(screen.queryByText(/Sesi Anda sudah berakhir/)).not.toBeInTheDocument()
  })

  it('setelah login berhasil langsung pindah ke halaman berikutnya tanpa muat ulang', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 401 }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/auth/login': () => ({ status: 200, body: me('OWNER', true) }),
    })
    renderApp('/login')
    fireEvent.change(await screen.findByLabelText('Username'), { target: { value: 'owner' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'rahasia-123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Masuk' }))
    expect(await screen.findByText('Anda wajib mengganti password sebelum melanjutkan.')).toBeInTheDocument()
  })

  it('Owner melihat 6 menu utama yang disusun menurut pekerjaan (K-145)', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }),
      'GET /api/employees': () => ({ status: 200, body: [] }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/devices': () => ({ status: 200, body: [] }),
    })
    renderApp('/')
    const sidebar = await screen.findByRole('navigation', { name: 'Menu utama' })
    const labels = within(sidebar).getAllByRole('link').map((link) => link.textContent)
    expect(labels).toEqual(['Beranda', 'Absensi', 'Izin & Cuti', 'Gaji & Laporan', 'Karyawan', 'Pengaturan'])
  })

  it('header desktop Owner/Finance punya tombol Profil', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 200, body: me('HR_FINANCE') }) })
    renderApp('/izin/5')
    fireEvent.click(await screen.findByRole('button', { name: 'Profil' }))
    expect(screen.getByTestId('loc')).toHaveTextContent(/^\/profil$/)
  })

  it('alamat lama halaman pengaturan dialihkan ke bawah /pengaturan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
    })
    renderApp('/cabang')
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(/^\/pengaturan\/cabang$/))
    expect(await screen.findByLabelText('Kembali ke Pengaturan')).toHaveAttribute('href', '/pengaturan')
  })

  it('belum login membuka alamat lama: login dulu, lalu mendarat di alamat baru', async () => {
    let loggedIn = false
    mockFetch({
      'GET /api/auth/me': () => (loggedIn ? { status: 200, body: me('OWNER') } : { status: 401 }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/auth/login': () => {
        loggedIn = true
        return { status: 200, body: me('OWNER') }
      },
    })
    renderApp('/aturan-kerja')
    expect(await screen.findByRole('button', { name: 'Masuk' })).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent('/login?next=%2Faturan-kerja')
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'owner' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'rahasia-123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Masuk' }))
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(/^\/pengaturan\/aturan-kerja$/))
    expect(await screen.findByLabelText('Kembali ke Pengaturan')).toBeInTheDocument()
  })

  it('/laporan dialihkan ke /gaji', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }) })
    renderApp('/laporan')
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(/^\/gaji/))
  })

  it('logout eksplisit berakhir di /login polos tanpa next', async () => {
    let alive = true
    mockFetch({
      'GET /api/auth/me': () => (alive ? { status: 200, body: me('HR_FINANCE') } : { status: 401 }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/auth/logout': () => {
        alive = false
        return { status: 204 }
      },
    })
    renderApp('/izin/5')
    const buttons = await screen.findAllByRole('button', { name: 'Keluar' })
    fireEvent.click(buttons[0])
    expect(await screen.findByRole('button', { name: 'Masuk' })).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent(/^\/login$/)
  })

  it('Karyawan diarahkan ke beranda dan tidak melihat menu master data', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('EMPLOYEE') }),
      'GET /api/me/employee': () => ({
        status: 200,
        body: { id: 1, employeeNumber: 'K-01', fullName: 'Siti', branchName: 'Pusat', categoryName: 'Kantor' },
      }),
    })
    renderApp('/karyawan')
    expect(await screen.findByText('Halo, Siti')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Karyawan' })).not.toBeInTheDocument()
    const sidebar = screen.getByRole('navigation', { name: 'Menu utama' })
    expect(within(sidebar).getByRole('link', { name: 'Profil' })).toBeInTheDocument()
    // Di HP, menu yang sama tersedia di bottom navigation.
    const bottomNav = screen.getByRole('navigation', { name: 'Menu bawah' })
    expect(within(bottomNav).getByRole('link', { name: 'Profil' })).toBeInTheDocument()
  })

  it('menu Lainnya di mobile berisi Karyawan, Pengaturan, Profil, dan akun & sesi', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }),
      'GET /api/employees': () => ({ status: 200, body: [] }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/devices': () => ({ status: 200, body: [] }),
    })
    renderApp('/')
    const bottomNav = await screen.findByRole('navigation', { name: 'Menu bawah' })
    const bottomLabels = within(bottomNav).getAllByRole('link').map((link) => link.textContent)
    expect(bottomLabels).toEqual(['Beranda', 'Absensi', 'Izin & Cuti', 'Gaji & Laporan'])
    fireEvent.click(within(bottomNav).getByRole('button', { name: 'Lainnya' }))
    const dialog = await screen.findByRole('dialog', { name: 'Menu lainnya' })
    for (const name of ['Karyawan', 'Pengaturan', 'Profil']) {
      expect(within(dialog).getByRole('link', { name })).toBeInTheDocument()
    }
    expect(within(dialog).getByText('Akun & Sesi')).toBeInTheDocument()
  })

  it('menyediakan tombol Atur Kaca dan modal pengatur efek kaca 21st dev', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }),
      'GET /api/employees': () => ({ status: 200, body: [] }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/devices': () => ({ status: 200, body: [] }),
    })
    renderApp('/')
    const adjustBtn = await screen.findByRole('button', { name: /Atur Kaca/i })
    expect(adjustBtn).toBeInTheDocument()
    fireEvent.click(adjustBtn)
    expect(await screen.findByRole('dialog', { name: /Sesuaikan Efek Kaca/i })).toBeInTheDocument()
  })
})
