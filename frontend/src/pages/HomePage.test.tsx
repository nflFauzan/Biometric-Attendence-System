import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { sampleRequest } from '@/components/leave/fixtures'
import type { Me, TeamDay, TeamMember } from '@/lib/types'
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

const yesterday: TeamDay = { date: '2026-10-13', status: 'HADIR', checkIn: '08:00:00', checkOut: '17:00:00', lateSeconds: null, overtimeSeconds: null }
const today: TeamDay = { date: '2026-10-14', status: 'HADIR', checkIn: '08:05:00', checkOut: null, lateSeconds: 300, overtimeSeconds: null }

const member = (days: TeamDay[]): TeamMember => ({
  employeeId: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', branchName: 'Pusat', position: null,
  from: days[0].date, to: days[days.length - 1].date, days,
})

const routes = (leaves: unknown[]) => ({
  'GET /api/auth/me': () => ({ status: 200, body: employee }),
  'GET /api/me/attendance?from=2026-10-14&to=2026-10-14': () => ({ status: 200, body: member([today]) }),
  'GET /api/me/attendance?from=2026-10-01&to=2026-10-14': () => ({ status: 200, body: member([yesterday, today]) }),
  'GET /api/me/leave-requests': () => ({ status: 200, body: leaves }),
})

describe('Beranda karyawan', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 9, 14, 9, 0) }))
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('menampilkan sapaan, kartu Hari ini, pengajuan berjalan, dan tombol Ajukan izin', async () => {
    mockFetch(
      routes([
        sampleRequest({ id: 5, status: 'PERLU_TINJAU_OWNER' }),
        sampleRequest({ id: 6, status: 'DRAFT' }),
        sampleRequest({ id: 7, status: 'DISETUJUI' }),
      ]),
    )
    renderAt('/beranda')

    expect(await screen.findByText('Halo, Budi')).toBeInTheDocument()
    const todayCard = screen.getByRole('region', { name: 'Hari ini' })
    expect(await within(todayCard).findByText('08.05')).toBeInTheDocument()
    expect(within(todayCard).getByText('Masuk')).toBeInTheDocument()

    expect(await screen.findByText('Perlu ditinjau Owner')).toBeInTheDocument()
    expect(screen.getByText('Draft')).toBeInTheDocument()
    expect(screen.queryByText('Disetujui')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ajukan izin' })).toHaveAttribute('href', '/izin-saya/baru')
    expect(screen.getByRole('link', { name: 'Lihat semua' })).toHaveAttribute('href', '/izin-saya')
    const month = await screen.findByRole('region', { name: 'Bulan ini' })
    expect(within(month).getByText('Hadir')).toBeInTheDocument()
  })

  it('daftar kosong menampilkan keadaan kosong', async () => {
    mockFetch(routes([]))
    renderAt('/beranda')
    expect(await screen.findByText('Belum ada pengajuan berjalan')).toBeInTheDocument()
  })
})
