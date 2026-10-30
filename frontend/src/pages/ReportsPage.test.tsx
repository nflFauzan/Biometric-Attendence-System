import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { localDateIso } from '@/lib/format'
import type { Me, ReportModel } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

const saveBlob = vi.hoisted(() => vi.fn())
vi.mock('@/lib/download', () => ({ saveBlob }))

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

const model = (overrides: Partial<ReportModel> = {}): ReportModel => ({
  type: 'REKAP_ABSENSI',
  title: 'Rekap Absensi',
  periodLabel: '01-10-2026 s/d 31-10-2026',
  filterLabels: [],
  dates: ['2026-10-05'],
  employees: [
    {
      employeeNumber: 'K-001',
      fullName: 'Budi Santoso',
      branchName: 'Pusat',
      categoryName: 'Supir',
      days: [
        {
          date: '2026-10-05',
          dayName: 'Senin',
          status: null,
          checkIn: null,
          checkOut: null,
          regularHours: 0,
          validatedOvertimeHours: 0,
          scanTimes: [],
          mark: '',
        },
      ],
      totals: { countingDays: 20, leaveDays: 1, absentDays: 2, countingHours: 150.5, validatedOvertimeHours: 3 },
    },
  ],
  ...overrides,
})

describe('Halaman Laporan', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    saveBlob.mockReset()
  })

  const to = localDateIso()
  const from = `${to.slice(0, 8)}01`
  const query = `from=${from}&to=${to}`
  const common = {
    'GET /api/auth/me': () => ({ status: 200, body: hr }),
    'GET /api/branches': () => ({ status: 200, body: [] }),
    'GET /api/employees': () => ({ status: 200, body: [] }),
    'GET /api/employee-categories': () => ({ status: 200, body: [{ id: 3, name: 'Supir' }] }),
  }

  it('memuat pratinjau rekap absensi dengan filter kategori', async () => {
    const fetchFn = mockFetch({
      ...common,
      [`GET /api/reports/rekap-absensi?${query}&categoryId=3`]: () => ({ status: 200, body: model() }),
    })
    renderAt('/laporan')
    await screen.findByRole('option', { name: 'Supir' })
    fireEvent.change(screen.getByLabelText('Kategori'), { target: { value: '3' } })

    expect(await screen.findByText('Budi Santoso')).toBeInTheDocument()
    expect(screen.getByText('150,50')).toBeInTheDocument()
    expect(fetchFn).toHaveBeenCalledWith(`/api/reports/rekap-absensi?${query}&categoryId=3`, expect.anything())
  })

  it('tombol unduh memanggil .xlsx dan .pdf dengan query sama lalu menyimpan berkas', async () => {
    const fetchFn = mockFetch({
      ...common,
      [`GET /api/reports/rekap-absensi?${query}`]: () => ({ status: 200, body: model() }),
      [`GET /api/reports/rekap-absensi.xlsx?${query}`]: () => ({ status: 200, body: new Blob(['x'], { type: 'application/vnd.ms-excel' }) }),
      [`GET /api/reports/rekap-absensi.pdf?${query}`]: () => ({ status: 200, body: new Blob(['p'], { type: 'application/pdf' }) }),
    })
    renderAt('/laporan')
    await screen.findByText('Budi Santoso')

    fireEvent.click(screen.getByRole('button', { name: 'Unduh Excel' }))
    await waitFor(() => expect(saveBlob).toHaveBeenCalledTimes(1))
    expect(saveBlob.mock.calls[0][1]).toBe(`rekap-absensi_${from}_${to}.xlsx`)
    fireEvent.click(screen.getByRole('button', { name: 'Unduh PDF' }))
    await waitFor(() => expect(saveBlob).toHaveBeenCalledTimes(2))
    expect(saveBlob.mock.calls[1][1]).toBe(`rekap-absensi_${from}_${to}.pdf`)
    const urls = fetchFn.mock.calls.map((call) => String(call[0]))
    expect(urls).toContain(`/api/reports/rekap-absensi.xlsx?${query}`)
    expect(urls).toContain(`/api/reports/rekap-absensi.pdf?${query}`)
  })

  it('mengganti jenis laporan memuat slug lain', async () => {
    mockFetch({
      ...common,
      [`GET /api/reports/rekap-absensi?${query}`]: () => ({ status: 200, body: model() }),
      [`GET /api/reports/rekap-lembur?${query}`]: () => ({
        status: 200,
        body: model({ type: 'REKAP_LEMBUR', title: 'Rekap Lembur Tervalidasi' }),
      }),
    })
    renderAt('/laporan')
    await screen.findByText('Budi Santoso')
    fireEvent.change(screen.getByLabelText('Jenis laporan'), { target: { value: 'rekap-lembur' } })
    await waitFor(() => expect(screen.getByText('Lembur tervalidasi (jam)')).toBeInTheDocument())
  })

  it('data kosong menampilkan pesan kosong', async () => {
    mockFetch({
      ...common,
      [`GET /api/reports/rekap-absensi?${query}`]: () => ({ status: 200, body: model({ employees: [] }) }),
    })
    renderAt('/laporan')
    expect(await screen.findByText('Tidak ada data pada periode ini.')).toBeInTheDocument()
  })

  it('menampilkan pesan error 400 dari server', async () => {
    mockFetch({
      ...common,
      [`GET /api/reports/rekap-absensi?${query}`]: () => ({
        status: 400,
        body: { detail: 'Rentang maksimal 62 hari.' },
      }),
    })
    renderAt('/laporan')
    expect(await screen.findByText('Rentang maksimal 62 hari.')).toBeInTheDocument()
  })
})
