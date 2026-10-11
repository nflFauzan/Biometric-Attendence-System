import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
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

// Render pertama di worker baru (cold start) bisa lebih dari batas default 1 detik.
const LOAD = { timeout: 5000 }

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }

const employee = (overrides: Partial<Employee> = {}): Employee => ({
  id: 12,
  employeeNumber: 'K-012',
  fullName: 'Budi Santoso',
  phone: null,
  branchId: 1,
  branchName: 'Cabang Pusat',
  categoryId: 2,
  categoryName: 'Supir/Kenek',
  position: 'Supir',
  joinDate: '2026-01-05',
  endDate: null,
  active: true,
  ...overrides,
})

const account = (overrides: Partial<UserAccount> = {}): UserAccount => ({
  id: 30,
  username: 'budi',
  role: 'EMPLOYEE',
  employeeId: 12,
  employeeName: 'Budi Santoso',
  active: true,
  mustChangePassword: true,
  lastLoginAt: null,
  ...overrides,
})

describe('EmployeeDetailPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menampilkan data kepegawaian dan akun web karyawan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/employees/12': () => ({ status: 200, body: employee() }),
      'GET /api/users': () => ({ status: 200, body: [account()] }),
    })
    renderAt('/karyawan/12')

    expect(await screen.findByRole('heading', { name: 'Budi Santoso', level: 1 }, LOAD)).toBeInTheDocument()
    expect(screen.getByText('K-012')).toBeInTheDocument()
    expect(screen.getByText('Cabang Pusat')).toBeInTheDocument()
    expect(await screen.findByText('budi')).toBeInTheDocument()
    expect(screen.getByText('Wajib ganti password saat login')).toBeInTheDocument()
  })

  it('menampilkan ajakan membuat akun jika karyawan belum punya akun', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/employees/12': () => ({ status: 200, body: employee() }),
      'GET /api/users': () => ({ status: 200, body: [account({ employeeId: 99 })] }),
    })
    renderAt('/karyawan/12')

    expect(await screen.findByText('Karyawan ini belum punya akun web.', {}, LOAD)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Buat di menu Akun' })).toHaveAttribute('href', '/akun')
  })

  it('memperingatkan jika karyawan nonaktif tetapi akunnya masih aktif', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/employees/12': () => ({ status: 200, body: employee({ active: false, endDate: '2026-09-30' }) }),
      'GET /api/users': () => ({ status: 200, body: [account({ mustChangePassword: false })] }),
    })
    renderAt('/karyawan/12')

    expect(await screen.findByText(/Karyawan sudah nonaktif, tetapi akun webnya masih aktif/, {}, LOAD)).toBeInTheDocument()
    expect(screen.getByText('30 Sep 2026')).toBeInTheDocument()
  })

  it('menampilkan keadaan tidak ditemukan untuk ID yang tidak ada', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/employees/404': () => ({ status: 404, body: { status: 404, detail: 'Karyawan tidak ditemukan' } }),
      'GET /api/users': () => ({ status: 200, body: [] }),
    })
    renderAt('/karyawan/404')

    expect(await screen.findByText('Karyawan tidak ditemukan.', {}, LOAD)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kembali ke daftar' })).toHaveAttribute('href', '/karyawan')
  })

  it('Karyawan tidak bisa membuka detail karyawan lain', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({
        status: 200,
        body: { username: 'siti', role: 'EMPLOYEE', employeeId: 1, employeeName: 'Siti', mustChangePassword: false },
      }),
      'GET /api/me/employee': () => ({ status: 200, body: employee({ id: 1, employeeNumber: 'K-001', fullName: 'Siti' }) }),
    })
    renderAt('/karyawan/12')

    expect(await screen.findByText('K-001', {}, LOAD)).toBeInTheDocument()
    expect(screen.queryByText('Data kepegawaian')).not.toBeInTheDocument()
  })
})
