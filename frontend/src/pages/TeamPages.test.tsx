import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Me, TeamDaily, TeamMember } from '@/lib/types'
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

const employee: Me = { username: 'budi', role: 'EMPLOYEE', employeeId: 12, employeeName: 'Budi Santoso', mustChangePassword: false }

const daily: TeamDaily = {
  date: '2026-10-05',
  branchId: null,
  branches: [
    { id: 1, name: 'Pusat' },
    { id: 2, name: 'Cabang Timur' },
  ],
  rows: [
    {
      employeeId: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', branchId: 1, branchName: 'Pusat', position: 'Staf gudang',
      day: { date: '2026-10-05', status: 'HADIR', checkIn: '08:10:00', checkOut: '17:30:00', lateSeconds: 600, overtimeSeconds: 1800 },
    },
    {
      employeeId: 13, employeeNumber: 'K-013', fullName: 'Siti Aminah', branchId: 2, branchName: 'Cabang Timur', position: null,
      day: { date: '2026-10-05', status: 'IZIN', checkIn: null, checkOut: null, lateSeconds: null, overtimeSeconds: null },
    },
  ],
}

const dailyRoutes = {
  'GET /api/auth/me': () => ({ status: 200, body: employee }),
  'GET /api/team/attendance?date=2026-10-05': () => ({ status: 200, body: daily }),
}

describe('Halaman Rekan Kerja', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('karyawan melihat absensi rekan kerja; izin tampil tanpa jenis', async () => {
    mockFetch(dailyRoutes)
    renderAt('/rekan-kerja?tanggal=2026-10-05')

    const table = await screen.findByRole('table')
    const siti = within(table).getByText('Siti Aminah').closest('tr')!
    expect(within(siti).getByText('Izin')).toBeInTheDocument()
    // Jabatan kosong dan jam kosong tampil sebagai tanda strip, tidak crash.
    expect(within(siti).getAllByText('—').length).toBeGreaterThan(1)
    const budi = within(table).getByText('Budi Santoso').closest('tr')!
    expect(within(budi).getByText('Hadir')).toBeInTheDocument()
    expect(within(budi).getByText('Terlambat')).toBeInTheDocument()
    expect(within(budi).getByText('08.10')).toBeInTheDocument()
    expect(within(table).getByText('Budi Santoso').closest('a')).toHaveAttribute('href', '/rekan-kerja/12')
  })

  it('pencarian tidak peka huruf besar-kecil dan mencocokkan No. karyawan', async () => {
    mockFetch(dailyRoutes)
    renderAt('/rekan-kerja?tanggal=2026-10-05')
    const table = await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('Cari nama'), { target: { value: 'SITI' } })
    expect(within(table).queryByText('Budi Santoso')).not.toBeInTheDocument()
    expect(within(table).getByText('Siti Aminah')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Cari nama'), { target: { value: 'k-012' } })
    expect(within(table).getByText('Budi Santoso')).toBeInTheDocument()
    expect(within(table).queryByText('Siti Aminah')).not.toBeInTheDocument()
  })

  it('daftar kartu di HP ikut tersaring oleh Cari nama', async () => {
    mockFetch(dailyRoutes)
    renderAt('/rekan-kerja?tanggal=2026-10-05')
    await screen.findByRole('table')
    const list = screen.getByRole('list', { name: 'Daftar rekan kerja' })
    expect(within(list).getByText('Budi Santoso')).toBeInTheDocument()
    expect(within(list).getByText('Siti Aminah')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Cari nama'), { target: { value: 'budi' } })
    expect(within(list).getByText('Budi Santoso')).toBeInTheDocument()
    expect(within(list).queryByText('Siti Aminah')).not.toBeInTheDocument()
  })

  it('filter cabang memanggil API dengan branchId', async () => {
    const fetch = mockFetch({
      ...dailyRoutes,
      'GET /api/team/attendance?date=2026-10-05&branchId=2': () => ({ status: 200, body: { ...daily, branchId: 2, rows: [daily.rows[1]] } }),
    })
    renderAt('/rekan-kerja?tanggal=2026-10-05')
    await screen.findByRole('table')

    fireEvent.change(screen.getByLabelText('Cabang'), { target: { value: '2' } })
    await waitFor(() =>
      expect(fetch.mock.calls.some(([url]) => url === '/api/team/attendance?date=2026-10-05&branchId=2')).toBe(true),
    )
  })

  it('tanggal yang belum terjadi tidak bisa dipilih', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-05T03:00:00Z'))
    mockFetch(dailyRoutes)
    renderAt('/rekan-kerja')
    await screen.findByRole('table')

    expect(screen.getByRole('button', { name: 'Hari berikutnya' })).toBeDisabled()
    expect(screen.getByLabelText('Tanggal')).toHaveAttribute('max', '2026-10-05')
  })

  it('riwayat satu bulan beserta ringkasan', async () => {
    // Hanya Date yang dipalsukan, agar timer React Query tetap berjalan normal.
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-10-14T03:00:00Z'))
    const member: TeamMember = {
      employeeId: 13, employeeNumber: 'K-013', fullName: 'Siti Aminah', branchName: 'Cabang Timur', position: null,
      from: '2026-09-01', to: '2026-09-30',
      days: [
        { date: '2026-09-01', status: 'HADIR', checkIn: '08:20:00', checkOut: '17:00:00', lateSeconds: 1200, overtimeSeconds: 0 },
        { date: '2026-09-02', status: 'HADIR', checkIn: '07:55:00', checkOut: '17:00:00', lateSeconds: 0, overtimeSeconds: 0 },
        { date: '2026-09-03', status: 'IZIN', checkIn: null, checkOut: null, lateSeconds: null, overtimeSeconds: null },
        { date: '2026-09-04', status: 'TIDAK_HADIR', checkIn: null, checkOut: null, lateSeconds: null, overtimeSeconds: null },
      ],
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/team/employees/13/attendance?from=2026-09-01&to=2026-09-30': () => ({ status: 200, body: member }),
    })
    renderAt('/rekan-kerja/13?bulan=2026-09')

    expect(await screen.findByRole('heading', { name: 'Siti Aminah' })).toBeInTheDocument()
    expect(screen.getByText('September 2026')).toBeInTheDocument()
    const summary = screen.getByRole('list', { name: 'Ringkasan bulan ini' })
    expect(within(summary).getByText('Hadir').nextSibling).toHaveTextContent('2')
    expect(within(summary).getByText('Terlambat').nextSibling).toHaveTextContent('1')
    expect(within(summary).getByText('Izin').nextSibling).toHaveTextContent('1')
    expect(within(summary).getByText('Tidak hadir').nextSibling).toHaveTextContent('1')
  })
})
