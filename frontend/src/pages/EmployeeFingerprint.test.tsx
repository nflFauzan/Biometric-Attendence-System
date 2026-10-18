import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Device, DeviceCommand, Employee, FingerprintOverview, Me } from '@/lib/types'
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

// Render pertama di worker baru (cold start) bisa lebih dari batas default 1 detik.
const LOAD = { timeout: 5000 }
// Status perintah berganti lewat polling 3 detik (tanpa EventSource di jsdom).
const POLL = { timeout: 10000 }
const CMD = '0b6f6c7e-3a43-4c47-9d0e-6d2b8d1f0a11'

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }

const employee = (overrides: Partial<Employee> = {}): Employee => ({
  id: 12,
  employeeNumber: 'K-012',
  fullName: 'Budi Santoso',
  phone: null,
  branchId: 1,
  branchName: 'Cabang Pusat',
  categoryId: 2,
  categoryName: 'Supir/Kenek',
  position: 'Supir',
  joinDate: '2026-01-05',
  endDate: null,
  active: true,
  devicePin: '12',
  ...overrides,
})

const overview = (overrides: Partial<FingerprintOverview> = {}): FingerprintOverview => ({
  consentAt: '2026-10-18T02:00:00Z',
  consentBy: 'owner',
  fingers: [],
  activeCommands: [],
  ...overrides,
})

const device = (overrides: Partial<Device> = {}): Device => ({
  id: 5,
  name: 'Rakitan Pusat',
  serialNumber: 'RK-7F2A9C',
  branchId: 1,
  branchName: 'Cabang Pusat',
  active: true,
  protocol: 'RAKITAN_V1',
  acceptScansFrom: '2026-01-01T00:00:00Z',
  ignoredScanCount: 0,
  lastSeenAt: '2026-10-18T02:00:00Z',
  lastIp: null,
  firmware: null,
  online: true,
  keyIssued: true,
  keyFirstUsedAt: null,
  lastInfo: null,
  issuedKey: null,
  ...overrides,
})

const command = (overrides: Partial<DeviceCommand> = {}): DeviceCommand => ({
  id: CMD,
  type: 'ENROLL',
  status: 'MENUNGGU',
  deviceId: 5,
  deviceName: 'Rakitan Pusat',
  employeeId: 12,
  finger: 1,
  step: null,
  errorCode: null,
  message: 'Menunggu mesin…',
  createdAt: '2026-10-18T02:00:00Z',
  finishedAt: null,
  ...overrides,
})

const routes = (opts: { emp?: Employee; fp?: FingerprintOverview; devices?: Device[] } = {}) => ({
  'GET /api/auth/me': () => ({ status: 200, body: hr }),
  'GET /api/employees/12': () => ({ status: 200, body: opts.emp ?? employee() }),
  'GET /api/users': () => ({ status: 200, body: [] }),
  'GET /api/employees/12/fingerprints': () => ({ status: 200, body: opts.fp ?? overview() }),
  'GET /api/devices': () => ({ status: 200, body: opts.devices ?? [device()] }),
  'POST /api/employees/12/fingerprints/enroll': () => ({ status: 201, body: command() }),
})

/** Tombol di footer dialog (tombol X bawaan dialog juga bernama "Tutup"). */
function footer(dialog: HTMLElement) {
  return within(dialog.querySelector<HTMLElement>('[data-slot="dialog-footer"]')!)
}

async function fingerRow(n: number) {
  return within(await screen.findByRole('group', { name: `Jari ke-${n}` }, LOAD))
}

describe('Sidik jari di detail karyawan', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('consent locks enroll until checked', async () => {
    let consented = false
    const fetchMock = mockFetch({
      ...routes(),
      'GET /api/employees/12/fingerprints': () => ({
        status: 200,
        body: consented ? overview() : overview({ consentAt: null, consentBy: null }),
      }),
      'POST /api/employees/12/fingerprint-consent': () => {
        consented = true
        return { status: 200, body: overview() }
      },
    })
    renderAt('/karyawan/12')

    expect(await screen.findByRole('heading', { name: 'Sidik Jari' }, LOAD)).toBeInTheDocument()
    const row = await fingerRow(1)
    expect(row.getByRole('button', { name: 'Daftarkan' })).toBeDisabled()
    expect(screen.getByText('Centang persetujuan karyawan dulu.')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('checkbox', { name: 'Karyawan sudah menyetujui penyimpanan sidik jari' }))

    await waitFor(() =>
      expect(within(screen.getByRole('group', { name: 'Jari ke-1' })).getByRole('button', { name: 'Daftarkan' })).toBeEnabled(),
    )
    expect(
      fetchMock.mock.calls.some(([url, init]) => url === '/api/employees/12/fingerprint-consent' && init?.method === 'POST'),
    ).toBe(true)
    expect(screen.getByText(/Disetujui oleh owner/)).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Karyawan sudah menyetujui penyimpanan sidik jari' })).toBeDisabled()
  })

  it('missing PIN blocks enroll with reason', async () => {
    mockFetch(routes({ emp: employee({ devicePin: null }) }))
    renderAt('/karyawan/12')

    expect((await fingerRow(1)).getByRole('button', { name: 'Daftarkan' })).toBeDisabled()
    expect(await screen.findByText('Isi PIN mesin karyawan dulu.')).toBeInTheDocument()
  })

  it('no online rakitan shows X401 message', async () => {
    mockFetch(
      routes({
        devices: [
          device({ id: 4, name: 'X401 Pusat', protocol: 'ZK_PUSH', online: true }),
          device({ id: 6, name: 'Rakitan Gudang', online: false }),
        ],
      }),
    )
    renderAt('/karyawan/12')

    fireEvent.click((await fingerRow(1)).getByRole('button', { name: 'Daftarkan' }))
    const dialog = await screen.findByRole('dialog')
    expect(
      await within(dialog).findByText(
        'Tidak ada alat rakitan yang online. Mesin X401 tidak mendukung pendaftaran dari web; daftarkan langsung di mesin.',
      ),
    ).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Mulai' })).not.toBeInTheDocument()
  })

  it('dialog follows command status to success', { timeout: 20000 }, async () => {
    const replies = [
      command({ status: 'DIKIRIM', step: 1, message: 'Tempelkan jari (1/3)' }),
      command({ status: 'BERHASIL', step: 3, message: 'Berhasil', finishedAt: '2026-10-18T02:00:20Z' }),
    ]
    const fetchMock = mockFetch({
      ...routes(),
      [`GET /api/device-commands/${CMD}`]: () => ({ status: 200, body: replies.length > 1 ? replies.shift() : replies[0] }),
    })
    renderAt('/karyawan/12')

    fireEvent.click((await fingerRow(1)).getByRole('button', { name: 'Daftarkan' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(await within(dialog).findByRole('button', { name: 'Mulai' }))

    expect(await within(dialog).findByText('Menunggu mesin…')).toBeInTheDocument()
    const enrollCall = fetchMock.mock.calls.find(([url]) => url === '/api/employees/12/fingerprints/enroll')
    expect(JSON.parse(String(enrollCall?.[1]?.body))).toEqual({ deviceId: 5, finger: 1 })
    expect(await within(dialog).findByText('Tempelkan jari (1/3)', {}, POLL)).toBeInTheDocument()
    expect(await within(dialog).findByText('Berhasil', {}, POLL)).toBeInTheDocument()
    expect(footer(dialog).getByRole('button', { name: 'Tutup' })).toBeInTheDocument()
    expect(footer(dialog).queryByRole('button', { name: 'Batal' })).not.toBeInTheDocument()
  })

  it('cancel posts and shows cancelled', async () => {
    const fetchMock = mockFetch({
      ...routes(),
      [`GET /api/device-commands/${CMD}`]: () => ({ status: 200, body: command() }),
      [`POST /api/device-commands/${CMD}/cancel`]: () => ({
        status: 200,
        body: command({ status: 'DIBATALKAN', message: 'Pendaftaran dibatalkan.' }),
      }),
    })
    renderAt('/karyawan/12')

    fireEvent.click((await fingerRow(1)).getByRole('button', { name: 'Daftarkan' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(await within(dialog).findByRole('button', { name: 'Mulai' }))
    fireEvent.click(await within(dialog).findByRole('button', { name: 'Batal' }))

    expect(await within(dialog).findByText('Pendaftaran dibatalkan.')).toBeInTheDocument()
    expect(
      fetchMock.mock.calls.some(([url, init]) => url === `/api/device-commands/${CMD}/cancel` && init?.method === 'POST'),
    ).toBe(true)
    expect(footer(dialog).getByRole('button', { name: 'Tutup' })).toBeInTheDocument()
  })

  it('dialog shows timeout message', async () => {
    mockFetch({
      ...routes(),
      'POST /api/employees/12/fingerprints/enroll': () => ({
        status: 201,
        body: command({ status: 'GAGAL', errorCode: 'WAKTU_HABIS', message: 'Mesin tidak merespons. Periksa koneksi mesin.' }),
      }),
    })
    renderAt('/karyawan/12')

    fireEvent.click((await fingerRow(1)).getByRole('button', { name: 'Daftarkan' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(await within(dialog).findByRole('button', { name: 'Mulai' }))

    expect(await within(dialog).findByText('Mesin tidak merespons. Periksa koneksi mesin.')).toBeInTheDocument()
    expect(within(dialog).getByRole('status')).toHaveTextContent('Mesin tidak merespons.')
  })

  it('enroll conflict is shown as error', async () => {
    mockFetch({
      ...routes(),
      'POST /api/employees/12/fingerprints/enroll': () => ({
        status: 409,
        body: { status: 409, detail: 'Mesin sedang dipakai untuk pendaftaran.' },
      }),
    })
    renderAt('/karyawan/12')

    fireEvent.click((await fingerRow(1)).getByRole('button', { name: 'Daftarkan' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(await within(dialog).findByRole('button', { name: 'Mulai' }))

    expect(await within(dialog).findByText('Mesin sedang dipakai untuk pendaftaran.')).toBeInTheDocument()
  })

  it('re-enroll asks for confirmation', async () => {
    mockFetch(
      routes({
        fp: overview({
          fingers: [{ finger: 1, deviceId: 5, deviceName: 'Rakitan Pusat', enrolledAt: '2026-10-17T03:00:00Z' }],
        }),
      }),
    )
    renderAt('/karyawan/12')

    const row = await fingerRow(1)
    expect(await row.findByText(/Terdaftar di Rakitan Pusat/)).toBeInTheDocument()
    expect((await fingerRow(2)).getByText('Belum terdaftar')).toBeInTheDocument()
    fireEvent.click(row.getByRole('button', { name: 'Daftar ulang' }))

    const confirm = await screen.findByRole('dialog', { name: 'Daftar ulang jari ke-1?' })
    fireEvent.click(within(confirm).getByRole('button', { name: 'Lanjutkan' }))
    expect(await screen.findByRole('button', { name: 'Mulai' })).toBeInTheDocument()
  })
})
