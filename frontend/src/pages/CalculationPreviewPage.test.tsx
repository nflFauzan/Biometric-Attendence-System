import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { DailyPreview, Employee, Me } from '@/lib/types'
import { parseScanTimes } from '@/lib/form'
import { mockFetch } from '@/test/fetchMock'
import { pickDate } from '@/test/pickers'

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

const LOAD = { timeout: 5000 }

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }

const employee: Employee = {
  id: 12,
  employeeNumber: 'K-012',
  fullName: 'Budi Santoso',
  phone: null,
  branchId: 1,
  branchName: 'Pusat',
  categoryId: 2,
  categoryName: 'Kantor',
  position: null,
  joinDate: '2026-01-05',
  endDate: null,
  active: true,
  devicePin: null,
  email: null,
  gender: null,
  birthDate: null,
  address: null,
  notes: null,
}

const preview: DailyPreview = {
  timezone: 'Asia/Jakarta',
  holiday: false,
  rule: {
    employeeId: 12,
    employeeName: 'Budi Santoso',
    categoryId: 2,
    categoryName: 'Kantor',
    date: '2026-10-12',
    values: {
      workStart: '08:00',
      workEnd: '17:00',
      breakStart: null,
      breakEnd: null,
      lateToleranceMinutes: 0,
      overtimeStart: '17:00',
      workDays: ['MONDAY'],
      dailyWage: null,
      overtimeRatePerHour: null,
      baseSalary: null,
      minScanGapMinutes: null,
    },
    sources: {},
    missing: [],
  },
  result: {
    date: '2026-10-12',
    dayType: 'HARI_KERJA',
    status: 'LENGKAP',
    scanTimes: ['15:00', '18:00'],
    checkIn: { time: '15:00', source: 'SCAN' },
    checkOut: { time: '18:00', source: 'SCAN' },
    lateSeconds: 25200,
    earlyLeaveSeconds: 0,
    regularWorkSeconds: 7200,
    overtimeCandidateSeconds: 3600,
    totalPresenceSeconds: 10800,
    flags: ['ISTIRAHAT_BELUM_DIATUR'],
  },
}

describe('parseScanTimes', () => {
  it('menerima koma, spasi, dan titik sebagai pemisah jam', () => {
    expect(parseScanTimes('08:00, 17.30 12:00')).toEqual(['08:00', '17:30', '12:00'])
    expect(parseScanTimes('')).toEqual([])
    expect(parseScanTimes('8:00')).toBeNull()
    expect(parseScanTimes('25:00')).toBeNull()
  })
})

describe('CalculationPreviewPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menampilkan jam normal, kandidat lembur, dan penanda dari backend (AC-05)', async () => {
    const fetchMock = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/employees': () => ({ status: 200, body: [employee] }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/calculation/daily-preview': () => ({ status: 200, body: preview }),
    })
    renderAt('/simulasi')

    await screen.findByRole('option', { name: /Budi Santoso/ }, LOAD)
    fireEvent.change(screen.getByLabelText('Karyawan'), { target: { value: '12' } })
    pickDate(screen.getByLabelText('Tanggal'), '2026-10-12')
    fireEvent.change(screen.getByLabelText('Jam scan'), { target: { value: '15:00, 18:00' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hitung' }))

    expect(await screen.findByText('Lengkap')).toBeInTheDocument()
    expect(screen.getByText('Jam kerja normal').nextSibling).toHaveTextContent('2 jam')
    expect(screen.getByText('Jam lembur').nextSibling).toHaveTextContent('1 jam')
    expect(screen.getByText(/Jam istirahat belum diatur/)).toBeInTheDocument()

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      employeeId: 12,
      date: '2026-10-12',
      scanTimes: ['15:00', '18:00'],
      correctionCheckIn: null,
      correctionCheckOut: null,
    })
  })

  it('menampilkan pesan bila aturan belum diatur', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/employees': () => ({ status: 200, body: [employee] }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/calculation/daily-preview': () => ({
        status: 409,
        body: { status: 409, detail: 'Aturan belum diatur: jam masuk, jam pulang' },
      }),
    })
    renderAt('/simulasi')

    await screen.findByRole('option', { name: /Budi Santoso/ }, LOAD)
    fireEvent.change(screen.getByLabelText('Karyawan'), { target: { value: '12' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hitung' }))

    expect(await screen.findByText('Aturan belum diatur: jam masuk, jam pulang')).toBeInTheDocument()
  })
})
