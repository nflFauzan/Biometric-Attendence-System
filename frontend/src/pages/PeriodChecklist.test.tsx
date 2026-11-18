import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { localDateIso } from '@/lib/format'
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

const LOAD = { timeout: 5000 }
const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }
const today = localDateIso()
const range = `from=${today.slice(0, 8)}01&to=${today}`
const emptyReport = { type: 'REKAP_ABSENSI', title: 'Rekap Absensi', periodLabel: '', filterLabels: [], dates: [], employees: [] }
const rows = (n: number, extra: object = {}) => Array.from({ length: n }, (_, i) => ({ id: i + 1, ...extra }))

type Routes = Parameters<typeof mockFetch>[0]

function routes(counts: { review: number; overtime: number; held: number; unmatched: number }, extra: Routes = {}): Routes {
  return {
    'GET /api/auth/me': () => ({ status: 200, body: hr }),
    'GET /api/branches': () => ({ status: 200, body: [] }),
    'GET /api/employees': () => ({ status: 200, body: [] }),
    'GET /api/employee-categories': () => ({ status: 200, body: [] }),
    [`GET /api/reports/rekap-absensi?${range}`]: () => ({ status: 200, body: emptyReport }),
    [`GET /api/attendance/review-queue?${range}`]: () => ({ status: 200, body: rows(counts.review) }),
    // Lembur yang sudah divalidasi tidak dihitung.
    [`GET /api/overtime?${range}`]: () => ({
      status: 200,
      body: [...rows(counts.overtime, { status: 'BELUM_DIVALIDASI' }), { id: 99, status: 'TERVALIDASI' }],
    }),
    [`GET /api/attendance/held-scans?${range}&status=BELUM_DITINJAU`]: () => ({ status: 200, body: rows(counts.held) }),
    [`GET /api/attendance/unmatched-scans?${range}`]: () => ({ status: 200, body: rows(counts.unmatched) }),
    ...extra,
  }
}

describe('Daftar cek sebelum unduh di Gaji & Laporan (K-145)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menampilkan yang belum beres dengan tautan ke tab Absensi, tanpa mengunci tombol unduh', async () => {
    mockFetch(routes({ review: 3, overtime: 5, held: 2, unmatched: 0 }))
    renderAt('/gaji')

    const checklist = await screen.findByRole('region', { name: 'Sebelum unduh, bereskan dulu' }, LOAD)
    expect(await within(checklist).findByText('3 hari bermasalah')).toBeInTheDocument()
    expect(await within(checklist).findByText('5 lembur belum divalidasi')).toBeInTheDocument()
    expect(await within(checklist).findByText('2 scan dari cabang lain belum ditinjau')).toBeInTheDocument()
    expect(await within(checklist).findByText('Tidak ada scan sidik jari tak dikenal')).toBeInTheDocument()
    expect(within(checklist).getByRole('link', { name: /Validasi/ })).toHaveAttribute('href', `/absensi?tab=lembur&${range}`)
    expect(within(checklist).getByRole('link', { name: /Bereskan/ })).toHaveAttribute('href', `/absensi?tab=bereskan&${range}`)
    expect(screen.getByText('Masih ada 10 hal belum beres')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Unduh Excel' })).toBeEnabled()
    expect(screen.getByRole('tab', { name: 'Laporan' })).toHaveAttribute('aria-selected', 'true')
  })

  it('semua beres memberi satu baris hijau', async () => {
    mockFetch(routes({ review: 0, overtime: 0, held: 0, unmatched: 0 }))
    renderAt('/gaji')

    expect(await screen.findByText('Semua beres. Laporan siap diunduh.', {}, LOAD)).toBeInTheDocument()
    expect(screen.queryByText(/Masih ada/)).not.toBeInTheDocument()
  })

  it('bagian yang gagal dimuat ditandai dan tidak dianggap beres', async () => {
    mockFetch(
      routes(
        { review: 0, overtime: 0, held: 0, unmatched: 0 },
        { [`GET /api/attendance/held-scans?${range}&status=BELUM_DITINJAU`]: () => ({ status: 500, body: { detail: 'x' } }) },
      ),
    )
    renderAt('/gaji')

    expect(await screen.findByText('Gagal memuat scan dari cabang lain', {}, LOAD)).toBeInTheDocument()
    expect(screen.queryByText('Semua beres. Laporan siap diunduh.')).not.toBeInTheDocument()
    expect(screen.getByText('Daftar cek gagal dimuat sebagian')).toBeInTheDocument()
  })

  it('gaji bulanan memeriksa seluruh bulan yang dipilih', async () => {
    const fetchFn = mockFetch(routes({ review: 0, overtime: 0, held: 0, unmatched: 0 }))
    renderAt('/gaji')

    fireEvent.change(await screen.findByLabelText('Jenis laporan', {}, LOAD), { target: { value: 'gaji-bulanan' } })
    fireEvent.change(await screen.findByLabelText('Bulan'), { target: { value: '2026-10' } })
    await waitFor(() =>
      expect(
        fetchFn.mock.calls.some(([url]) => url === '/api/attendance/review-queue?from=2026-10-01&to=2026-10-31'),
      ).toBe(true),
    )
  })

  it('jenis laporan dan bulan disimpan di alamat, jadi kembali dari tab Absensi tidak mengulang pilihan', async () => {
    const fetchFn = mockFetch(routes({ review: 0, overtime: 0, held: 0, unmatched: 0 }))
    renderAt('/gaji?jenis=gaji-bulanan&bulan=2026-10')

    expect(await screen.findByLabelText('Bulan', {}, LOAD)).toHaveValue('2026-10')
    expect(screen.getByLabelText('Jenis laporan')).toHaveValue('gaji-bulanan')
    await waitFor(() =>
      expect(fetchFn.mock.calls.some(([url]) => url === '/api/reports/gaji-bulanan?from=2026-10-01&to=2026-10-31')).toBe(true),
    )

    fireEvent.change(screen.getByLabelText('Bulan'), { target: { value: '2026-09' } })
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('bulan=2026-09'))
    fireEvent.click(screen.getByRole('tab', { name: 'Kasbon' }))
    fireEvent.click(await screen.findByRole('tab', { name: 'Laporan' }))
    expect(await screen.findByLabelText('Bulan')).toHaveValue('2026-09')
  })

  it('alamat lama Kasbon membuka tab Kasbon', async () => {
    mockFetch(routes({ review: 0, overtime: 0, held: 0, unmatched: 0 }, { 'GET /api/loans': () => ({ status: 200, body: [] }) }))
    renderAt('/kasbon')

    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/gaji?tab=kasbon'))
    expect(await screen.findByRole('tab', { name: 'Kasbon' }, LOAD)).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('button', { name: /Tambah Kasbon/ })).toBeInTheDocument()
  })
})
