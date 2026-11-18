import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

function LocationProbe() {
  const l = useLocation()
  return <span data-testid="loc">{l.pathname + l.search}</span>
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
          <LocationProbe />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }
const range = 'from=2026-10-01&to=2026-10-05'
const reviewItem = (employeeId: number, fullName: string) => ({
  employeeId,
  employeeNumber: `K-${employeeId}`,
  fullName,
  branchName: 'Pusat',
  categoryName: 'Supir',
  date: '2026-10-03',
  status: 'TIDAK_LENGKAP',
  flags: [],
  ruleMissing: false,
  reasons: ['Tidak lengkap'],
})
const heldScan = {
  scan: { id: 9, devicePin: '7', deviceName: 'Mesin Cabang 2', deviceLocalTime: '2026-10-03T07:58:00', receivedAt: '2026-10-03T00:58:05Z' },
  employeeName: 'Rina',
  placementBranchName: 'Pusat',
  deviceBranchName: 'Cabang 2',
  status: 'BELUM_DITINJAU',
  reason: null,
  decidedBy: null,
  decidedAt: null,
}

function routes() {
  return {
    'GET /api/auth/me': () => ({ status: 200, body: hr }),
    'GET /api/branches': () => ({ status: 200, body: [] }),
    'GET /api/employee-categories': () => ({ status: 200, body: [] }),
    [`GET /api/attendance/review-queue?${range}`]: () => ({
      status: 200,
      body: [reviewItem(12, 'Budi Santoso'), reviewItem(13, 'Siti Aminah')],
    }),
    [`GET /api/attendance/held-scans?${range}&status=BELUM_DITINJAU`]: () => ({ status: 200, body: [heldScan] }),
    [`GET /api/attendance/unmatched-scans?${range}`]: () => ({ status: 200, body: [] }),
    [`GET /api/overtime?${range}`]: () => ({ status: 200, body: [] }),
  }
}

describe('Tab Perlu Dibereskan (K-145)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('alamat lama Perlu Ditinjau dialihkan ke tab dengan periode yang sama', async () => {
    mockFetch(routes())
    renderAt(`/absensi/perlu-ditinjau?${range}`)
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(`/absensi?tab=bereskan&${range}`))
    expect(await screen.findByText('Budi Santoso')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Perlu Dibereskan/ })).toHaveAttribute('aria-selected', 'true')
  })

  it('menampilkan tiga bagian dengan jumlah, badge tab menjumlahkan semuanya', async () => {
    mockFetch(routes())
    renderAt(`/absensi?tab=bereskan&${range}`)
    const tab = await screen.findByRole('tab', { name: /Perlu Dibereskan/ })
    await waitFor(() => expect(tab).toHaveTextContent('3'))

    const days = screen.getByRole('region', { name: 'Hari bermasalah' })
    expect(within(days).getByText('2 belum beres')).toBeInTheDocument()
    const held = screen.getByRole('region', { name: 'Scan dari cabang lain' })
    expect(within(held).getByText('1 belum beres')).toBeInTheDocument()
    const unmatched = screen.getByRole('region', { name: 'Scan sidik jari tak dikenal' })
    expect(within(unmatched).getByText('Beres')).toBeInTheDocument()
  })

  it('tanpa periode di alamat, scan tertahan yang belum ditinjau dari semua tanggal ikut tampil (tautan Beranda)', async () => {
    const today = new Date()
    const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    const to = iso(today)
    const from = `${to.slice(0, 8)}01`
    const fetchFn = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/employee-categories': () => ({ status: 200, body: [] }),
      [`GET /api/attendance/review-queue?from=${from}&to=${to}`]: () => ({ status: 200, body: [] }),
      [`GET /api/overtime?from=${from}&to=${to}`]: () => ({ status: 200, body: [] }),
      [`GET /api/attendance/unmatched-scans?from=${from}&to=${to}`]: () => ({ status: 200, body: [] }),
      [`GET /api/attendance/held-scans?from=2000-01-01&to=${to}&status=BELUM_DITINJAU`]: () => ({ status: 200, body: [heldScan] }),
    })
    renderAt('/absensi?tab=bereskan')

    const held = await screen.findByRole('region', { name: 'Scan dari cabang lain' })
    await waitFor(() => expect(within(held).getByText('1 belum beres')).toBeInTheDocument())
    expect(within(held).getByText(/semua tanggal/)).toBeInTheDocument()
    expect(fetchFn.mock.calls.some(([url]) => String(url).startsWith(`/api/attendance/held-scans?from=${from}`))).toBe(false)
  })

  it('alamat lama Validasi Lembur membuka tab Lembur', async () => {
    mockFetch(routes())
    renderAt(`/absensi/lembur?${range}`)
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(`/absensi?tab=lembur&${range}`))
    expect(await screen.findByRole('tab', { name: 'Lembur' })).toHaveAttribute('aria-selected', 'true')
  })
})
