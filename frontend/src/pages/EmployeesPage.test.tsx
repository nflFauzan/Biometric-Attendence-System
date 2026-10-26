import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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
  email: null, gender: 'PEREMPUAN', birthDate: '1990-05-01', address: 'Jl. Lama 1', notes: null,
}

const routes = (me: Me) => ({
  'PUT /api/employees/12': () => ({ status: 200, body: budi }),
  'GET /api/auth/me': () => ({ status: 200, body: me }),
  'GET /api/employees': () => ({ status: 200, body: [budi] }),
  'GET /api/branches': () => ({ status: 200, body: [] }),
  'GET /api/employee-categories': () => ({ status: 200, body: [] }),
})

/** K-128: data karyawan hanya diubah Owner; HR/Finance tetap melihat daftar. */
describe('Daftar karyawan', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('Finance & Accounting melihat daftar tanpa tombol Tambah dan Ubah', async () => {
    mockFetch(routes(hr))
    renderAt('/karyawan')
    expect((await screen.findAllByRole('link', { name: 'Budi Santoso' }, LOAD)).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Tambah' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ubah' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Impor dari Excel' })).not.toBeInTheDocument()
  })

  it('Owner tetap bisa menambah dan mengubah', async () => {
    mockFetch(routes(owner))
    renderAt('/karyawan')
    expect((await screen.findAllByRole('link', { name: 'Budi Santoso' }, LOAD)).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Tambah' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Ubah' }).length).toBeGreaterThan(0)
    expect(screen.getByRole('link', { name: 'Impor dari Excel' })).toHaveAttribute('href', '/karyawan/impor')
  })

  it('form Owner memuat dan mengirim profil lengkap (K-129)', async () => {
    const fetch = mockFetch(routes(owner))
    renderAt('/karyawan')
    fireEvent.click((await screen.findAllByRole('button', { name: 'Ubah' }, LOAD))[0])
    const address = await screen.findByLabelText('Alamat')
    expect(address).toHaveValue('Jl. Lama 1')
    fireEvent.change(address, { target: { value: 'Jl. Baru 2' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'budi@contoh.id' } })
    fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => url === '/api/employees/12' && init?.method === 'PUT')).toBe(true))
    const put = fetch.mock.calls.find(([url, init]) => url === '/api/employees/12' && init?.method === 'PUT')!
    expect(JSON.parse(String(put[1]!.body))).toMatchObject({
      address: 'Jl. Baru 2',
      email: 'budi@contoh.id',
      gender: 'PEREMPUAN',
      birthDate: '1990-05-01',
      notes: null,
    })
  })
})
