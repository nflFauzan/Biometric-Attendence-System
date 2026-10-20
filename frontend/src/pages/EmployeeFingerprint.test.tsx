import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Deletion, Device, DeviceCommand, Employee, FingerprintOverview, Me } from '@/lib/types'
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
  deletions: [],
  zkPushDeviceActive: false,
  ...overrides,
})

const CMD2 = '7c1d2e3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f'
const X401_NOTE = 'Hapus juga langsung di mesin X401 jika karyawan pernah didaftarkan di sana.'

const deletion = (overrides: Partial<Deletion> = {}): Deletion => ({
  commandId: CMD,
  type: 'DELETE_TEMPLATE',
  finger: 1,
  deviceId: 5,
  deviceName: 'Rakitan Pusat',
  status: 'MENUNGGU',
  message: 'Menunggu mesin…',
  retryable: false,
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
  sensorUsed: null,
  sensorCapacity: null,
  sensorAlmostFull: false,
  settingsVersion: 0,
  settingsAppliedVersion: 0,
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
        body: command({ status: 'GAGAL', errorCode: 'TIDAK_ADA_HASIL', message: 'Mesin tidak merespons. Periksa koneksi mesin.' }),
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
  it('delete finger confirms in dialog then calls DELETE', async () => {
    const fetchMock = mockFetch({
      ...routes({
        fp: overview({
          fingers: [{ finger: 1, deviceId: 5, deviceName: 'Rakitan Pusat', enrolledAt: '2026-10-17T03:00:00Z' }],
        }),
      }),
      'DELETE /api/employees/12/fingerprints/1': () => ({ status: 200, body: overview({ deletions: [deletion()] }) }),
    })
    renderAt('/karyawan/12')

    const row = await fingerRow(1)
    expect((await fingerRow(2)).queryByRole('button', { name: 'Hapus jari ke-2' })).not.toBeInTheDocument()
    fireEvent.click(await row.findByRole('button', { name: 'Hapus jari ke-1' }))
    const confirm = await screen.findByRole('dialog', { name: 'Hapus sidik jari jari ke-1 Budi Santoso dari semua mesin?' })
    fireEvent.click(within(confirm).getByRole('button', { name: 'Ya, hapus' }))

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url, init]) => url === '/api/employees/12/fingerprints/1' && init?.method === 'DELETE'),
      ).toBe(true),
    )
    expect(await (await fingerRow(1)).findByText('Belum terdaftar')).toBeInTheDocument()
    expect(await screen.findByRole('region', { name: 'Penghapusan di mesin' })).toBeInTheDocument()
  })

  it('deletion list shows machine status and retries failed delete', async () => {
    const fetchMock = mockFetch({
      ...routes({
        fp: overview({
          deletions: [
            deletion(),
            deletion({
              commandId: CMD2,
              deviceId: 6,
              deviceName: 'Rakitan Gudang',
              status: 'GAGAL',
              message: 'Sensor bermasalah. Hubungi teknisi.',
              retryable: true,
            }),
          ],
        }),
      }),
      [`POST /api/device-commands/${CMD2}/retry`]: () => ({ status: 200, body: command({ id: CMD2, type: 'DELETE_TEMPLATE' }) }),
    })
    renderAt('/karyawan/12')

    const list = within(await screen.findByRole('region', { name: 'Penghapusan di mesin' }, LOAD))
    expect(list.getByText('Rakitan Pusat')).toBeInTheDocument()
    expect(list.getByText('Menunggu mesin…')).toBeInTheDocument()
    expect(list.getByText('Gagal: Sensor bermasalah. Hubungi teknisi.')).toBeInTheDocument()
    expect(list.getAllByRole('button', { name: 'Coba lagi' })).toHaveLength(1)

    fireEvent.click(list.getByRole('button', { name: 'Coba lagi' }))

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url, init]) => url === `/api/device-commands/${CMD2}/retry` && init?.method === 'POST'),
      ).toBe(true),
    )
  })

  it('X401 note shows only with pending deletions or inactive employee', async () => {
    mockFetch(routes({ fp: overview({ zkPushDeviceActive: true }) }))
    const first = renderAt('/karyawan/12')
    await fingerRow(1)
    expect(screen.queryByText(X401_NOTE)).not.toBeInTheDocument()
    first.unmount()

    mockFetch(routes({ fp: overview({ zkPushDeviceActive: true, deletions: [deletion()] }) }))
    const second = renderAt('/karyawan/12')
    expect(await screen.findByText(X401_NOTE, {}, LOAD)).toBeInTheDocument()
    second.unmount()

    mockFetch(routes({ emp: employee({ active: false }), fp: overview({ zkPushDeviceActive: true }) }))
    renderAt('/karyawan/12')
    expect(await screen.findByText(X401_NOTE, {}, LOAD)).toBeInTheDocument()
  })
})
