import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/auth/AuthContext'
import { LoginPage } from '@/pages/LoginPage'
import { mockFetch } from '@/test/fetchMock'

function Probe() {
  const l = useLocation()
  return <span data-testid="loc">{l.pathname + l.search}</span>
}

function renderLogin(initial = '/login') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initial]}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/ganti-password" element={<><p>Halaman ganti password</p><Probe /></>} />
            <Route path="/izin/:id" element={<><p>Detail izin</p><Probe /></>} />
            <Route path="/beranda" element={<p>Beranda karyawan</p>} />
            <Route path="/" element={<p>Dasbor</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function submit(username: string, password: string) {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } })
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } })
  fireEvent.click(screen.getByRole('button', { name: 'Masuk' }))
}

describe('LoginPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menampilkan pesan dari server saat password salah', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 401 }),
      'GET /api/auth/csrf': () => ({ status: 200, body: {} }),
      'POST /api/auth/login': () => ({ status: 401, body: { detail: 'Username atau password salah' } }),
    })
    renderLogin()
    submit('budi', 'salah')
    expect(await screen.findByText('Username atau password salah')).toBeInTheDocument()
  })

  it('mengarahkan ke ganti password setelah login pertama', async () => {
    const fetchFn = mockFetch({
      'GET /api/auth/me': () => ({ status: 401 }),
      'GET /api/auth/csrf': () => ({ status: 200, body: {} }),
      'POST /api/auth/login': () => ({
        status: 200,
        body: { username: 'owner', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: true },
      }),
    })
    renderLogin()
    submit('owner', 'password-awal')
    expect(await screen.findByText('Halaman ganti password')).toBeInTheDocument()
    const loginCall = fetchFn.mock.calls.find(([url, init]) => url === '/api/auth/login' && init?.method === 'POST')
    expect(JSON.parse(loginCall?.[1]?.body as string)).toEqual({ username: 'owner', password: 'password-awal' })
  })

  const loginOk = (role: string, mustChangePassword = false) =>
    mockFetch({
      'GET /api/auth/me': () => ({ status: 401 }),
      'GET /api/auth/csrf': () => ({ status: 200, body: {} }),
      'POST /api/auth/login': () => ({
        status: 200,
        body: { username: 'u', role, employeeId: null, employeeName: null, mustChangePassword },
      }),
    })

  it('kembali ke tujuan next (termasuk query) setelah login', async () => {
    loginOk('OWNER')
    renderLogin('/login?next=' + encodeURIComponent('/izin/123?tab=a'))
    submit('owner', 'rahasia-123')
    expect(await screen.findByText('Detail izin')).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent('/izin/123?tab=a')
  })

  it.each([['//evil.com'], ['https://x'], ['/login']])('menolak next %s dan memakai beranda peran', async (bad) => {
    loginOk('EMPLOYEE')
    renderLogin('/login?next=' + encodeURIComponent(bad))
    submit('siti', 'rahasia-123')
    expect(await screen.findByText('Beranda karyawan')).toBeInTheDocument()
  })

  it('tanpa next diarahkan ke beranda peran', async () => {
    loginOk('OWNER')
    renderLogin()
    submit('owner', 'rahasia-123')
    expect(await screen.findByText('Dasbor')).toBeInTheDocument()
  })

  it('wajib ganti password didahulukan dan membawa tujuan asli', async () => {
    loginOk('OWNER', true)
    renderLogin('/login?next=' + encodeURIComponent('/izin/123'))
    submit('owner', 'rahasia-123')
    expect(await screen.findByText('Halaman ganti password')).toBeInTheDocument()
    expect(screen.getByTestId('loc')).toHaveTextContent('/ganti-password?next=%2Fizin%2F123')
  })
})
