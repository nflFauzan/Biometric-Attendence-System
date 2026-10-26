import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Employee, Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

function renderAt(path: string) {
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

const LOAD = { timeout: 5000 }

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }
const owner: Me = { username: 'owner', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: false }

const budi: Employee = {
  id: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', phone: null, branchId: 1, branchName: 'Cabang Pusat',
  categoryId: 2, categoryName: 'Staf', position: null, joinDate: '2026-01-05', endDate: null, active: true, devicePin: '12',
}

const routes = (me: Me) => ({
  'GET /api/auth/me': () => ({ status: 200, body: me }),
  'GET /api/employees': () => ({ status: 200, body: [budi] }),
  'GET /api/branches': () => ({ status: 200, body: [] }),
  'GET /api/employee-categories': () => ({ status: 200, body: [] }),
})

/** K-128: data karyawan hanya diubah Owner; HR/Finance tetap melihat daftar. */
describe('Daftar karyawan', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('HR/Finance melihat daftar tanpa tombol Tambah dan Ubah', async () => {
    mockFetch(routes(hr))
    renderAt('/karyawan')
    expect((await screen.findAllByRole('link', { name: 'Budi Santoso' }, LOAD)).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Tambah' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ubah' })).not.toBeInTheDocument()
  })

  it('Owner tetap bisa menambah dan mengubah', async () => {
    mockFetch(routes(owner))
    renderAt('/karyawan')
    expect((await screen.findAllByRole('link', { name: 'Budi Santoso' }, LOAD)).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Tambah' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Ubah' }).length).toBeGreaterThan(0)
  })
})
