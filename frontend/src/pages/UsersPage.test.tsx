import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Employee, Me, UserAccount } from '@/lib/types'
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

const owner: Me = { username: 'owner', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: false }

const eni: Employee = {
  id: 6, employeeNumber: '006', fullName: 'Eni Contoh', phone: '0812-0000-0006', branchId: 1, branchName: 'Cabang Pusat',
  categoryId: 2, categoryName: 'Kantor', position: null, joinDate: '2026-11-01', endDate: null, active: true,
  devicePin: '006', email: null, gender: null, birthDate: null, address: null, notes: null,
}

const eniAccount: UserAccount = {
  id: 40, username: 'eni@takka.id', role: 'EMPLOYEE', employeeId: 6, employeeName: 'Eni Contoh', active: true,
  mustChangePassword: false, lastLoginAt: null,
}

const routes = () => ({
  'GET /api/auth/me': () => ({ status: 200, body: owner }),
  'GET /api/users': () => ({ status: 200, body: [eniAccount] }),
  'GET /api/employees': () => ({ status: 200, body: [eni] }),
  'POST /api/users/40/reset-password': () => ({ status: 204 }),
  'POST /api/users': () => ({ status: 201, body: { ...eniAccount, id: 41 } }),
})

/** K-133: reset password dan buat akun dari menu Akun memakai usulan password dan pesan WA. */
describe('Menu Akun', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('reset password terisi usulan nama-ID-takka lalu menampilkan pesan reset', async () => {
    const fetch = mockFetch(routes())
    renderAt('/akun')
    fireEvent.click((await screen.findAllByRole('button', { name: 'Reset password' }, LOAD))[0])
    const field = await screen.findByLabelText(/Password sementara/)
    await waitFor(() => expect(field).toHaveValue('eni-006-takka'))
    fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))

    const isReset = ([url]: Parameters<typeof fetch>) => url === '/api/users/40/reset-password'
    await waitFor(() => expect(fetch.mock.calls.some(isReset)).toBe(true))
    expect(JSON.parse(String(fetch.mock.calls.find(isReset)![1]!.body))).toEqual({ newPassword: 'eni-006-takka' })
    expect(await screen.findByDisplayValue(/\*Password Baru\*/, {}, LOAD)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kirim WA' }).getAttribute('href')).toMatch(/^https:\/\/wa\.me\/6281200000006\?/)
  })

  it('membuat akun dari menu Akun dengan usulan dari karyawan yang dipilih', async () => {
    const fetch = mockFetch(routes())
    renderAt('/akun')
    fireEvent.click(await screen.findByRole('button', { name: 'Tambah' }, LOAD))
    const employeeSelect = await screen.findByLabelText(/^Karyawan/)
    await screen.findByRole('option', { name: 'Eni Contoh (006)' })
    fireEvent.change(employeeSelect, { target: { value: '6' } })
    expect(screen.getByLabelText(/Username/)).toHaveValue('eni@takka.id')
    expect(screen.getByLabelText(/Password sementara/)).toHaveValue('eni-006-takka')
    fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))

    const isPost = ([url, init]: Parameters<typeof fetch>) => url === '/api/users' && init?.method === 'POST'
    await waitFor(() => expect(fetch.mock.calls.some(isPost)).toBe(true))
    expect(JSON.parse(String(fetch.mock.calls.find(isPost)![1]!.body))).toEqual({
      username: 'eni@takka.id',
      password: 'eni-006-takka',
      role: 'EMPLOYEE',
      employeeId: 6,
    })
    expect(await screen.findByDisplayValue(/Halo Kak Eni Contoh!/, {}, LOAD)).toBeInTheDocument()
  })
})
