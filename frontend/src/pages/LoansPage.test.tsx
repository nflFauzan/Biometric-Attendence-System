import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { EmployeeLoan, Me } from '@/lib/types'
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

const owner: Me = {
  username: 'owner',
  role: 'OWNER',
  employeeId: null,
  employeeName: null,
  mustChangePassword: false,
}

const sampleLoans: EmployeeLoan[] = [
  {
    id: 1,
    employeeId: 101,
    employeeName: 'Asep Supir',
    branchName: 'Takka 1',
    loanNumber: 'KB-202610-001',
    loanDate: '2026-10-01',
    totalAmount: 1000000,
    installmentAmount: 100000,
    remainingAmount: 900000,
    status: 'AKTIF',
    notes: 'Keperluan mendesak',
    createdAt: '2026-10-01T08:00:00Z',
    repayments: [
      {
        id: 11,
        loanId: 1,
        repaymentDate: '2026-10-08',
        amount: 100000,
        periodLabel: '03-10-2026 s.d. 09-10-2026',
        reportType: 'UPAH_MINGGUAN',
        notes: 'Potongan otomatis upah mingguan',
        createdAt: '2026-10-08T17:00:00Z',
      },
    ],
  },
]

describe('LoansPage', () => {
  it('menampilkan daftar kasbon karyawan dan rincian pinjaman', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/loans': () => ({ status: 200, body: sampleLoans }),
      'GET /api/employees': () => ({
        status: 200,
        body: [{ id: 101, fullName: 'Asep Supir', branchName: 'Takka 1', active: true }],
      }),
    })

    renderAt('/kasbon')

    expect(await screen.findByText('KB-202610-001')).toBeInTheDocument()
    expect(screen.getByText('Asep Supir')).toBeInTheDocument()
    expect(screen.getByText('Takka 1')).toBeInTheDocument()
    expect(screen.getByText('AKTIF')).toBeInTheDocument()
  })

  it('membuka dialog tambah kasbon baru', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/loans': () => ({ status: 200, body: sampleLoans }),
      'GET /api/employees': () => ({
        status: 200,
        body: [{ id: 101, fullName: 'Asep Supir', branchName: 'Takka 1', active: true }],
      }),
    })

    renderAt('/kasbon')

    const btn = await screen.findByRole('button', { name: /tambah kasbon/i })
    fireEvent.click(btn)

    expect(await screen.findByText('Tambah Kasbon Baru')).toBeInTheDocument()
    expect(screen.getByLabelText(/karyawan/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/total pinjaman/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/cicilan per periode/i)).toBeInTheDocument()
  })
})
