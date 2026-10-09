import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/auth/AuthContext'
import { LoginPage } from '@/pages/LoginPage'
import { mockFetch } from '@/test/fetchMock'

function renderLogin() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/ganti-password" element={<p>Halaman ganti password</p>} />
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
})
