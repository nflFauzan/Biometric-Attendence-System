import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
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

  it('Owner melihat menu Audit Log', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }),
      'GET /api/employees': () => ({ status: 200, body: [] }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/devices': () => ({ status: 200, body: [] }),
    })
    renderApp('/')
    expect(await screen.findByRole('link', { name: 'Audit Log' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Karyawan' })).toBeInTheDocument()
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
    expect(screen.getByRole('link', { name: 'Profil Saya' })).toBeInTheDocument()
  })
})
