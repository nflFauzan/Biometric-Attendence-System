import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { dayStatus, reviewNotes } from '@/lib/attendance'
import { addDays, localDateIso } from '@/lib/format'
import type { DashboardSummary, DayRecord, HeldScan, Me, Scan } from '@/lib/types'
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

const scan = (id: number, overrides: Partial<Scan> = {}): Scan => ({
  id,
  scannedAt: '2026-10-20T01:10:00Z',
  deviceLocalTime: '2026-10-20T08:10:00',
  deviceId: 3,
  deviceName: 'X401 Cikarang',
  devicePin: '9001',
  employeeId: 12,
  flags: ['CABANG_TIDAK_DIIZINKAN'],
  receivedAt: '2026-10-20T01:10:02Z',
  review: 'BELUM_DITINJAU',
  counted: false,
  ...overrides,
})

const held = (id: number, name: string): HeldScan => ({
  scan: scan(id),
  employeeName: name,
  placementBranchName: 'Pusat',
  deviceBranchName: 'Cikarang',
  status: 'BELUM_DITINJAU',
  reason: null,
  decidedBy: null,
  decidedAt: null,
})

describe('Halaman Scan Tertahan (K-103)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('memuat 30 hari terakhir berstatus belum ditinjau, meninjau dua baris dengan alasan wajib, lalu memuat ulang', async () => {
    const to = localDateIso()
    const from = addDays(to, -29)
    const listKey = `GET /api/attendance/held-scans?from=${from}&to=${to}&status=BELUM_DITINJAU`
    let rows = [held(1, 'Budi Santoso'), held(2, 'Siti Aminah')]
    let posted: unknown = null
    const fetchMock = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      [listKey]: () => ({ status: 200, body: rows }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/attendance/held-scans/review': (init) => {
        posted = JSON.parse(String(init?.body))
        rows = []
        return { status: 200, body: [] }
      },
    })
    renderAt('/absensi/scan-tertahan')

    expect(await screen.findByText('Budi Santoso')).toBeInTheDocument()
    expect(screen.getByText('Siti Aminah')).toBeInTheDocument()
    expect(screen.getByLabelText('Status')).toHaveValue('BELUM_DITINJAU')
    expect(screen.getAllByText('Cikarang').length).toBeGreaterThan(0)

    fireEvent.click(screen.getByLabelText('Pilih scan Budi Santoso'))
    fireEvent.click(screen.getByLabelText('Pilih scan Siti Aminah'))
    fireEvent.click(screen.getByRole('button', { name: 'Masukkan ke rekap' }))

    const dialog = await screen.findByRole('dialog')
    const save = within(dialog).getByRole('button', { name: 'Simpan' })
    expect(save).toBeDisabled()
    fireEvent.change(within(dialog).getByLabelText(/Alasan/), { target: { value: '   ' } })
    expect(save).toBeDisabled()
    fireEvent.change(within(dialog).getByLabelText(/Alasan/), { target: { value: 'Dinas ke Cikarang' } })
    expect(save).toBeEnabled()
    fireEvent.click(save)

    await waitFor(() => expect(posted).toEqual({ scanIds: [1, 2], decision: 'DITERIMA', reason: 'Dinas ke Cikarang' }))
    await waitFor(() => expect(screen.queryByText('Budi Santoso')).not.toBeInTheDocument())
    const listCalls = fetchMock.mock.calls.filter(([url]) => String(url) === listKey.slice(4))
    expect(listCalls.length).toBeGreaterThanOrEqual(2)
  })

  it('memilih lebih dari 200 scan menonaktifkan aksi massal dan menampilkan petunjuk', async () => {
    const to = localDateIso()
    const from = addDays(to, -29)
    const rows = Array.from({ length: 201 }, (_, i) => held(i + 1, `Karyawan ${i + 1}`))
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      [`GET /api/attendance/held-scans?from=${from}&to=${to}&status=BELUM_DITINJAU`]: () => ({ status: 200, body: rows }),
    })
    renderAt('/absensi/scan-tertahan')
    await screen.findByText('Karyawan 1')
    expect(screen.queryByText(/Maksimal 200 scan sekali tinjau/)).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Pilih semua scan'))
    expect(await screen.findByText('Maksimal 200 scan sekali tinjau. Kurangi pilihan.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Masukkan ke rekap' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Abaikan' })).toBeDisabled()

    fireEvent.click(screen.getByLabelText('Pilih scan Karyawan 201'))
    expect(screen.queryByText(/Maksimal 200 scan sekali tinjau/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Masukkan ke rekap' })).toBeEnabled()
  })

  it('status Semua tidak mengirim parameter status', async () => {
    const to = localDateIso()
    const from = addDays(to, -29)
    const fetchMock = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      [`GET /api/attendance/held-scans?from=${from}&to=${to}&status=BELUM_DITINJAU`]: () => ({ status: 200, body: [] }),
      [`GET /api/attendance/held-scans?from=${from}&to=${to}`]: () => ({ status: 200, body: [held(3, 'Rina')] }),
    })
    renderAt('/absensi/scan-tertahan')
    await screen.findByText(/Tidak ada scan tertahan/)
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: '' } })
    expect(await screen.findByText('Rina')).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalled()
  })
})

describe('Kartu dashboard dan label scan tertahan', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('kartu "Scan tertahan" menaut ke halaman peninjauan', async () => {
    const counts = {
      totalEmployees: 25, present: 18, late: 3, incomplete: 2, overtimeCandidates: 4, absent: 7, needsReview: 2, ruleMissing: 0,
    }
    const summary: DashboardSummary = {
      date: '2026-10-12', totals: counts, branches: [], devices: [], recentScans: [], unmatchedScans: 1, heldScans: 4,
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/dashboard/summary': () => ({ status: 200, body: summary }),
    })
    renderAt('/')
    expect((await screen.findByText('Scan tertahan')).closest('a')).toHaveAttribute('href', '/absensi/scan-tertahan')
  })

  it('scan counted=false tampil "Tertahan" atau "Diabaikan" di detail karyawan', async () => {
    const day: DayRecord = {
      date: '2026-10-05',
      result: null,
      ruleMissing: null,
      scans: [scan(1), scan(2, { review: 'DIABAIKAN', deviceName: 'X401 Bekasi' }), scan(3, { review: 'DITERIMA', counted: true })],
      correction: null,
      scanFlags: [],
      leave: null,
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/attendance/employees/12?from=2026-09-29&to=2026-10-05': () => ({
        status: 200,
        body: {
          employeeId: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', branchName: 'Pusat', timezone: 'Asia/Jakarta',
          devicePin: '9001', from: '2026-09-29', to: '2026-10-05', days: [day], corrections: [],
        },
      }),
    })
    renderAt('/absensi/karyawan/12?tanggal=2026-10-05')
    expect(await screen.findByText('Tertahan')).toBeInTheDocument()
    expect(screen.getByText('Diabaikan')).toBeInTheDocument()
    expect(screen.getAllByText('Tertahan')).toHaveLength(1)
  })

  it('penanda hari ADA_SCAN_TERTAHAN berlabel "Ada scan tertahan"', () => {
    const day = {
      date: '2026-10-05', ruleMissing: null, scans: [], correction: null, scanFlags: [], leave: null,
      result: { flags: ['ADA_SCAN_TERTAHAN'] },
    } as unknown as DayRecord
    expect(reviewNotes(day)).toContain('Ada scan tertahan')
  })

  it('CABANG_BERBEDA bukan catatan tinjauan, penanda lain tetap', () => {
    const day = (scanFlags: string[]) =>
      ({ date: '2026-10-05', ruleMissing: null, scans: [], correction: null, scanFlags, leave: null, result: null }) as unknown as DayRecord
    expect(reviewNotes(day(['CABANG_BERBEDA']))).toEqual([])
    expect(reviewNotes(day(['CABANG_BERBEDA', 'PIN_TIDAK_DIKENAL']))).toEqual(['PIN belum dipetakan ke karyawan'])
  })

  it('tanpa aturan kerja, scan tertahan bukan kehadiran: izin disetujui tetap "Izin"', () => {
    const leave = { requestId: 1, leaveTypeName: 'Sakit', status: 'DISETUJUI' }
    const day = (scans: Scan[]) =>
      ({ date: '2026-10-05', ruleMissing: 'Aturan belum diatur', scans, correction: null, scanFlags: [], leave, result: null }) as unknown as DayRecord
    expect(dayStatus(day([scan(1)])).label).toBe('Izin: Sakit')
    expect(dayStatus(day([scan(2, { counted: true, flags: [] })])).label).toBe('Aturan belum diatur')
  })
})
