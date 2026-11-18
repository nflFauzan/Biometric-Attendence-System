import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/auth/AuthContext'
import type { DashboardSummary, Me } from '@/lib/types'
import { DashboardPage } from '@/pages/DashboardPage'
import { HomePage } from '@/pages/HomePage'
import { mockFetch } from '@/test/fetchMock'

const owner: Me = { username: 'owner', role: 'OWNER', employeeId: null, employeeName: 'Pak Budi', mustChangePassword: false }
const employee: Me = { username: 'siti', role: 'EMPLOYEE', employeeId: 2, employeeName: 'Siti Aminah', mustChangePassword: false }

const sampleSummary: DashboardSummary = {
  date: '2026-11-18',
  totals: {
    totalEmployees: 150,
    present: 142,
    absent: 3,
    onLeave: 5,
    late: 4,
    incomplete: 2,
    needsReview: 1,
    overtimeCandidates: 8,
    ruleMissing: 0,
  },
  pendingLeaveRequests: 2,
  unmatchedScans: 0,
  heldScans: 0,
  recentScans: [],
  devices: [],
  branches: [],
}

function renderDashboard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <DashboardPage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function renderHome() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AuthProvider>
          <HomePage />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('Bento Grid Dashboard & Home (21st.dev)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('DashboardPage mengelompokkan KPI dan aktivitas ke dalam kartu bento gw-panel gw-glass', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/dashboard/summary': () => ({ status: 200, body: sampleSummary }),
    })
    renderDashboard()

    expect(await screen.findByText('Hadir hari ini')).toBeInTheDocument()
    const presentCard = screen.getByText('Hadir hari ini').closest('.gw-panel')
    expect(presentCard).not.toBeNull()
    expect(presentCard).toHaveClass('gw-glass')
  })

  it('HomePage mengelompokkan kartu ringkasan ke dalam gw-panel gw-glass', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/attendance?from=2026-11-18&to=2026-11-18': () => ({ status: 200, body: { employeeId: 2, employeeNumber: 'K-02', fullName: 'Siti Aminah', branchName: 'Pusat', position: null, from: '2026-11-18', to: '2026-11-18', days: [] } }),
      'GET /api/me/attendance?from=2026-11-01&to=2026-11-18': () => ({ status: 200, body: { employeeId: 2, employeeNumber: 'K-02', fullName: 'Siti Aminah', branchName: 'Pusat', position: null, from: '2026-11-01', to: '2026-11-18', days: [] } }),
      'GET /api/me/leave-requests': () => ({ status: 200, body: [] }),
    })
    renderHome()

    expect(await screen.findByRole('region', { name: 'Hari ini' })).toHaveClass('gw-panel')
    expect(screen.getByRole('region', { name: 'Hari ini' })).toHaveClass('gw-glass')
  })
})
