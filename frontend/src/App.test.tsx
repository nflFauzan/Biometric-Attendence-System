import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'


function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
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

  it('Owner melihat menu Audit Log', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }),
      'GET /api/employees': () => ({ status: 200, body: [] }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/devices': () => ({ status: 200, body: [] }),
    })
    renderApp('/')
    const sidebar = await screen.findByRole('navigation', { name: 'Menu utama' })
    expect(within(sidebar).getByRole('link', { name: 'Audit Log' })).toBeInTheDocument()
    expect(within(sidebar).getByRole('link', { name: 'Karyawan' })).toBeInTheDocument()
  })

  it('Karyawan diarahkan ke profil dan tidak melihat menu master data', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('EMPLOYEE') }),
      'GET /api/me/employee': () => ({
        status: 200,
        body: { id: 1, employeeNumber: 'K-01', fullName: 'Siti', branchName: 'Pusat', categoryName: 'Kantor' },
      }),
    })
    renderApp('/karyawan')
    expect(await screen.findByText('K-01')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Karyawan' })).not.toBeInTheDocument()
    const sidebar = screen.getByRole('navigation', { name: 'Menu utama' })
    expect(within(sidebar).getByRole('link', { name: 'Profil Saya' })).toBeInTheDocument()
    // Di HP, menu yang sama tersedia di bottom navigation.
    const bottomNav = screen.getByRole('navigation', { name: 'Menu bawah' })
    expect(within(bottomNav).getByRole('link', { name: 'Profil Saya' })).toBeInTheDocument()
  })
})
