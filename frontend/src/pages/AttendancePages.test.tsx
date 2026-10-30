import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { localDateIso } from '@/lib/format'
import type { DailyAttendance, DashboardSummary, DayRecord, Device, EmployeeAttendance, Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'
import { pickTime } from '@/test/pickers'

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

const day = (overrides: Partial<DayRecord> = {}): DayRecord => ({
  date: '2026-10-05',
  result: {
    date: '2026-10-05',
    dayType: 'HARI_KERJA',
    status: 'TIDAK_LENGKAP',
    scanTimes: ['08:10:00'],
    checkIn: null,
    checkOut: null,
    lateSeconds: null,
    earlyLeaveSeconds: null,
    regularWorkSeconds: null,
    overtimeCandidateSeconds: null,
    totalPresenceSeconds: null,
    flags: ['ISTIRAHAT_BELUM_DIATUR'],
  },
  ruleMissing: null,
  scans: [
    {
      id: 1,
      scannedAt: '2026-10-05T01:10:00Z',
      deviceLocalTime: '2026-10-05T08:10:00',
      deviceId: 3,
      deviceName: 'X401 Pusat',
      devicePin: '9001',
      employeeId: 12,
      flags: [],
      receivedAt: '2026-10-05T01:10:02Z',
      review: null,
      counted: true,
    },
  ],
  correction: null,
  scanFlags: [],
  ...overrides,
})

const device: Device = {
  id: 3,
  name: 'X401 Pusat',
  serialNumber: 'BRYV181860187',
  branchId: 1,
  branchName: 'Pusat',
  active: true,
  protocol: 'ZK_PUSH',
  acceptScansFrom: '2026-10-12T00:00:00Z',
  ignoredScanCount: 16951,
  lastSeenAt: '2026-10-12T07:00:00Z',
  lastIp: '172.35.3.102',
  firmware: 'Ver 6.5.4(build 162)',
  online: true,
  keyIssued: false,
  keyFirstUsedAt: null,
  lastInfo: null,
  sensorUsed: null,
  sensorCapacity: null,
  sensorAlmostFull: false,
  settingsVersion: 0,
  settingsAppliedVersion: 0,
  issuedKey: null,
}

describe('Halaman absensi Fase 3', () => {
  it('menampilkan sub-navigasi tab terpadu untuk absensi dan masalah scan', async () => {
    const date = localDateIso()
    const daily: DailyAttendance = { date, branchId: null, rows: [] }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      [`GET /api/attendance/daily?date=${date}`]: () => ({ status: 200, body: daily }),
    })
    renderAt('/absensi')

    const subNav = await screen.findByRole('navigation', { name: 'Sub menu absensi' })
    expect(subNav).toBeInTheDocument()
    expect(within(subNav).getByRole('link', { name: 'Absensi Harian' })).toHaveAttribute('href', '/absensi')
    expect(within(subNav).getByRole('link', { name: 'Perlu Ditinjau' })).toHaveAttribute('href', '/absensi/perlu-ditinjau')
    expect(within(subNav).getByRole('link', { name: 'Scan Tertahan' })).toHaveAttribute('href', '/absensi/scan-tertahan')
    expect(within(subNav).getByRole('link', { name: 'Scan Tak Dikenal' })).toHaveAttribute('href', '/absensi/scan-tak-dikenal')
    expect(within(subNav).getByRole('link', { name: 'Validasi Lembur' })).toHaveAttribute('href', '/absensi/lembur')
  })

  afterEach(() => vi.unstubAllGlobals())

  it('dashboard menampilkan kehadiran hari ini, scan terbaru, dan status mesin', async () => {
    const counts = {
      totalEmployees: 25,
      present: 18,
      late: 3,
      incomplete: 2,
      overtimeCandidates: 4,
      absent: 7,
      needsReview: 2,
      ruleMissing: 0,
    }
    const summary: DashboardSummary = {
      date: '2026-10-12',
      totals: counts,
      branches: [{ branchId: 1, branchName: 'Pusat', counts }],
      devices: [device],
      recentScans: [
        { id: 9, scannedAt: '2026-10-12T01:05:00Z', devicePin: '9001', employeeId: 12, employeeName: 'Budi Santoso', deviceName: 'X401 Pusat', flags: [] },
        { id: 10, scannedAt: '2026-10-12T01:06:00Z', devicePin: '1696', employeeId: null, employeeName: null, deviceName: 'X401 Pusat', flags: ['PIN_TIDAK_DIKENAL'] },
      ],
      unmatchedScans: 1,
      heldScans: 0,
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/dashboard/summary': () => ({ status: 200, body: summary }),
    })
    renderAt('/')

    expect(await screen.findByText('dari 25 karyawan aktif')).toBeInTheDocument()
    expect(screen.getByText('18')).toBeInTheDocument()
    expect(screen.getByText('Budi Santoso')).toBeInTheDocument()
    expect(screen.getByText('PIN 1696 (belum dipetakan)')).toBeInTheDocument()
    expect(screen.getByText('Online')).toBeInTheDocument()
    expect(screen.getByText('Scan dengan PIN tak dikenal').closest('a')).toHaveAttribute('href', '/absensi/scan-tak-dikenal')
  })

  it('absensi harian menampilkan status dan pesan aturan yang belum diatur', async () => {
    const date = localDateIso()
    const daily: DailyAttendance = {
      date,
      branchId: null,
      rows: [
        { employeeId: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', devicePin: '9001', branchId: 1, branchName: 'Pusat', categoryName: 'Kantor', day: day() },
        {
          employeeId: 13,
          employeeNumber: 'K-013',
          fullName: 'Siti Aminah',
          devicePin: null,
          branchId: 1,
          branchName: 'Pusat',
          categoryName: 'Kantor',
          day: day({ result: null, ruleMissing: 'Aturan belum diatur: jam masuk', scans: [] }),
        },
      ],
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      [`GET /api/attendance/daily?date=${date}`]: () => ({ status: 200, body: daily }),
    })
    renderAt('/absensi')

    expect((await screen.findAllByText('Budi Santoso')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Tidak lengkap').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Aturan belum diatur: jam masuk').length).toBeGreaterThan(0)
    expect(screen.getByRole('columnheader', { name: 'Jam lembur' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: 'Budi Santoso' })[0]).toHaveAttribute(
      'href',
      `/absensi/karyawan/12?tanggal=${date}`,
    )
  })

  it('absensi harian bisa difilter per kategori karyawan', async () => {
    const date = localDateIso()
    const empty: DailyAttendance = { date, branchId: null, rows: [] }
    const fetchFn = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/employee-categories': () => ({
        status: 200,
        body: [{ id: 3, code: 'SPR', name: 'Supir', active: true }],
      }),
      [`GET /api/attendance/daily?date=${date}`]: () => ({ status: 200, body: empty }),
      [`GET /api/attendance/daily?date=${date}&categoryId=3`]: () => ({ status: 200, body: empty }),
    })
    renderAt('/absensi')

    const select = await screen.findByLabelText('Kategori')
    await screen.findByRole('option', { name: 'Supir' })
    fireEvent.change(select, { target: { value: '3' } })

    await waitFor(() =>
      expect(fetchFn.mock.calls.some(([url]) => url === `/api/attendance/daily?date=${date}&categoryId=3`)).toBe(true),
    )
  })

  it('koreksi absensi mengirim jam dan alasan, lalu memuat ulang rekap', async () => {
    const corrected = day({
      result: {
        ...day().result!,
        status: 'LENGKAP',
        checkIn: { time: '08:10:00', source: 'SCAN' },
        checkOut: { time: '17:00:00', source: 'KOREKSI' },
      },
      correction: {
        id: 5,
        employeeId: 12,
        workDate: '2026-10-05',
        checkIn: null,
        checkOut: '17:00:00',
        reason: 'Lupa scan pulang',
        createdBy: 'hr',
        createdAt: '2026-10-06T02:00:00Z',
        supersededAt: null,
      },
    })
    let current: EmployeeAttendance = {
      employeeId: 12,
      employeeNumber: 'K-012',
      fullName: 'Budi Santoso',
      branchName: 'Pusat',
      timezone: 'Asia/Jakarta',
      devicePin: '9001',
      from: '2026-09-29',
      to: '2026-10-05',
      days: [day()],
      corrections: [],
    }
    const fetchMock = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/attendance/employees/12?from=2026-09-29&to=2026-10-05': () => ({ status: 200, body: current }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/attendance/corrections': () => {
        current = { ...current, days: [corrected], corrections: [corrected.correction!] }
        return { status: 201, body: corrected.correction }
      },
    })
    renderAt('/absensi/karyawan/12?tanggal=2026-10-05')

    fireEvent.click(await screen.findByRole('button', { name: 'Koreksi 5 Okt 2026' }))
    const dialog = await screen.findByRole('dialog')
    pickTime(within(dialog).getByLabelText('Jam pulang'), '17:00')
    fireEvent.change(within(dialog).getByLabelText(/Alasan koreksi/), { target: { value: 'Lupa scan pulang' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(post![1]!.body))).toEqual({
      employeeId: 12,
      date: '2026-10-05',
      checkIn: null,
      checkOut: '17:00',
      reason: 'Lupa scan pulang',
    })
    expect(await screen.findByText('Dikoreksi oleh hr: “Lupa scan pulang”')).toBeInTheDocument()
  })

  it('halaman karyawan membaca rentang from dan to dari alamat (tautan Perlu Ditinjau)', async () => {
    const fetchFn = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/attendance/employees/12?from=2026-10-03&to=2026-10-05': () => ({
        status: 200,
        body: {
          employeeId: 12,
          employeeNumber: 'K-012',
          fullName: 'Budi Santoso',
          branchName: 'Pusat',
          timezone: 'Asia/Jakarta',
          devicePin: '9001',
          from: '2026-10-03',
          to: '2026-10-05',
          days: [day()],
          corrections: [],
        } satisfies EmployeeAttendance,
      }),
    })
    renderAt('/absensi/karyawan/12?from=2026-10-03&to=2026-10-05')

    await screen.findByRole('button', { name: 'Koreksi 5 Okt 2026' })
    expect(
      fetchFn.mock.calls.some(([url]) => url === '/api/attendance/employees/12?from=2026-10-03&to=2026-10-05'),
    ).toBe(true)
  })

  it('halaman mesin menampilkan status koneksi dan mesin yang belum terdaftar', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/devices': () => ({ status: 200, body: [device] }),
      'GET /api/devices/unregistered-attempts': () => ({
        status: 200,
        body: [{ serialNumber: 'SN-BARU-1', ip: '172.35.3.120', lastAttemptAt: '2026-10-12T07:00:00Z', attempts: 4 }],
      }),
    })
    renderAt('/mesin')

    expect(await screen.findByText('Mesin belum terdaftar mencoba terhubung')).toBeInTheDocument()
    expect(screen.getByText('SN-BARU-1')).toBeInTheDocument()
    expect(screen.getAllByText('Online').length).toBeGreaterThan(0)
    expect(screen.getAllByText('16951 scan lama diabaikan').length).toBeGreaterThan(0)
  })
})
