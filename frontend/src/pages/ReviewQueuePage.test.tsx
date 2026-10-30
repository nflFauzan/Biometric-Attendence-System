import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { localDateIso } from '@/lib/format'
import type { Me, ReviewItem } from '@/lib/types'
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

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }

const item = (overrides: Partial<ReviewItem> = {}): ReviewItem => ({
  employeeId: 12,
  employeeNumber: 'K-012',
  fullName: 'Budi Santoso',
  branchName: 'Pusat',
  categoryName: 'Supir',
  date: '2026-10-05',
  status: 'TIDAK_LENGKAP',
  flags: [],
  ruleMissing: false,
  reasons: ['Tidak lengkap', 'Ada scan tertahan'],
  ...overrides,
})

describe('Halaman Perlu Ditinjau (FR-REV-001)', () => {
  afterEach(() => vi.unstubAllGlobals())

  const to = localDateIso()
  const from = `${to.slice(0, 8)}01`
  const base = `/api/attendance/review-queue?from=${from}&to=${to}`

  it('menampilkan nama, tanggal, dan alasan, dengan tautan Tinjau ke halaman koreksi', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/employee-categories': () => ({ status: 200, body: [] }),
      [`GET ${base}`]: () => ({ status: 200, body: [item()] }),
    })
    renderAt('/absensi/perlu-ditinjau')

    expect(await screen.findByText('Budi Santoso')).toBeInTheDocument()
    expect(screen.getAllByText('5 Okt 2026').length).toBeGreaterThan(0)
    expect(screen.getByText('Tidak lengkap')).toBeInTheDocument()
    expect(screen.getByText('Ada scan tertahan')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Tinjau Budi Santoso' })).toHaveAttribute(
      'href',
      '/absensi/karyawan/12?from=2026-10-05&to=2026-10-05',
    )
  })

  it('filter cabang dan kategori ikut dikirim sebagai query', async () => {
    const fetchFn = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [{ id: 2, code: 'CKR', name: 'Cikarang', timezone: 'Asia/Jakarta', active: true }] }),
      'GET /api/employee-categories': () => ({ status: 200, body: [{ id: 3, code: 'SPR', name: 'Supir', active: true }] }),
      [`GET ${base}`]: () => ({ status: 200, body: [item()] }),
      [`GET ${base}&branchId=2`]: () => ({ status: 200, body: [] }),
      [`GET ${base}&branchId=2&categoryId=3`]: () => ({ status: 200, body: [] }),
    })
    renderAt('/absensi/perlu-ditinjau')

    await screen.findByText('Budi Santoso')
    await screen.findByRole('option', { name: 'Cikarang' })
    await screen.findByRole('option', { name: 'Supir' })
    fireEvent.change(screen.getByLabelText('Cabang'), { target: { value: '2' } })
    fireEvent.change(screen.getByLabelText('Kategori'), { target: { value: '3' } })

    await waitFor(() => expect(fetchFn.mock.calls.some(([url]) => url === `${base}&branchId=2&categoryId=3`)).toBe(true))
  })

  it('saat kosong menampilkan teks tidak ada data', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/employee-categories': () => ({ status: 200, body: [] }),
      [`GET ${base}`]: () => ({ status: 200, body: [] }),
    })
    renderAt('/absensi/perlu-ditinjau')

    expect(await screen.findByText('Tidak ada data yang perlu ditinjau pada periode ini.')).toBeInTheDocument()
  })
})
