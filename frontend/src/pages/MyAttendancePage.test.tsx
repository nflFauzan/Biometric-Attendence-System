import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
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

const days: TeamDay[] = [
  { date: '2026-10-13', status: 'HADIR', checkIn: '08:00:00', checkOut: '17:00:00', lateSeconds: null, overtimeSeconds: null },
  { date: '2026-10-14', status: 'IZIN', checkIn: null, checkOut: null, lateSeconds: null, overtimeSeconds: null },
]
const mine: TeamMember = {
  employeeId: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', branchName: 'Pusat', position: 'Staf gudang',
  from: '2026-10-01', to: '2026-10-14', days,
}

/** Absensi Saya (K-127): karyawan hanya melihat absensinya sendiri. */
describe('Absensi Saya', () => {
  beforeEach(() => vi.useFakeTimers({ toFake: ['Date'], now: new Date(2026, 9, 14, 9, 0) }))
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('mengambil absensi milik sendiri bulan berjalan dan menampilkan baris per hari', async () => {
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/attendance?from=2026-10-01&to=2026-10-14': () => ({ status: 200, body: mine }),
    })
    renderAt('/absensi-saya')

    expect(await screen.findByRole('heading', { name: 'Absensi Saya' })).toBeInTheDocument()
    expect((await screen.findAllByText('Izin')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('08.00').length).toBeGreaterThan(0)
    expect(fetch.mock.calls.some(([url]) => String(url).startsWith('/api/team'))).toBe(false)
  })

  it('alamat lama rekan kerja diarahkan ke Absensi Saya', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/attendance?from=2026-10-01&to=2026-10-14': () => ({ status: 200, body: mine }),
    })
    renderAt('/rekan-kerja/5')
    expect(await screen.findByRole('heading', { name: 'Absensi Saya' })).toBeInTheDocument()
  })
})
