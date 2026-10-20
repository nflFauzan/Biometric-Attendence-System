import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Device, DeviceCommand, DeviceSettingsResponse, Me, NetworkView } from '@/lib/types'
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

const LOAD = { timeout: 5000 }

const me = (role: Me['role']): Me => ({ username: 'u', role, employeeId: null, employeeName: null, mustChangePassword: false })

const rakitan = (overrides: Partial<Device> = {}): Device => ({
  id: 5,
  name: 'Uji Kampus',
  serialNumber: 'RK-7F2A9C',
  branchId: 1,
  branchName: 'Pusat',
  active: true,
  protocol: 'RAKITAN_V1',
  acceptScansFrom: '2026-01-01T00:00:00Z',
  ignoredScanCount: 0,
  lastSeenAt: '2026-10-20T01:00:00Z',
  lastIp: null,
  firmware: '0.2.0',
  online: true,
  keyIssued: true,
  keyFirstUsedAt: '2026-10-19T01:00:00Z',
  lastInfo: null,
  sensorUsed: null,
  sensorCapacity: null,
  sensorAlmostFull: false,
  settingsVersion: 0,
  settingsAppliedVersion: 0,
  issuedKey: null,
  ...overrides,
})

const settings = (version = 0): DeviceSettingsResponse => ({
  deviceId: 5,
  version,
  appliedVersion: version,
  values: {
    headerText: '',
    greeting: 'Silakan tempelkan jari',
    nameDisplay: 'FULL',
    brightness: 80,
    buzzerVolume: 50,
    duplicateNoticeSeconds: 60,
    heartbeatSeconds: 5,
  },
  updatedAt: null,
  updatedBy: null,
})

const command = (overrides: Partial<DeviceCommand> = {}): DeviceCommand => ({
  id: 'c1',
  type: 'APPLY_NETWORK',
  status: 'MENUNGGU',
  deviceId: 5,
  deviceName: 'Uji Kampus',
  employeeId: null,
  finger: null,
  step: null,
  errorCode: null,
  message: 'Menunggu mesin…',
  createdAt: '2026-10-20T01:00:00Z',
  finishedAt: null,
  ...overrides,
})

const network = (overrides: Partial<NetworkView> = {}): NetworkView => ({
  deviceId: 5,
  currentWifi: [],
  currentServerUrl: null,
  latest: null,
  ...overrides,
})

const routes = (role: Me['role'], devices: Device[] = [rakitan()]) => ({
  'GET /api/auth/me': () => ({ status: 200, body: me(role) }),
  'GET /api/branches': () => ({ status: 200, body: [{ id: 1, code: 'PST', name: 'Pusat', active: true }] }),
  'GET /api/devices': () => ({ status: 200, body: devices }),
  'GET /api/devices/unregistered-attempts': () => ({ status: 200, body: [] }),
  'GET /api/devices/5/settings': () => ({ status: 200, body: settings() }),
  'GET /api/devices/5/network': () => ({ status: 200, body: network() }),
})

function sent(fetchMock: ReturnType<typeof mockFetch>, method: string, url: string) {
  const call = fetchMock.mock.calls.find(([input, init]) => String(input) === url && init?.method === method)
  return call ? JSON.parse(String(call[1]?.body ?? 'null')) : undefined
}

async function openSettings() {
  fireEvent.click((await screen.findAllByRole('button', { name: 'Pengaturan' }, LOAD))[0])
  return screen.findByRole('dialog', { name: /Pengaturan Uji Kampus/ })
}

describe('Pengaturan mesin', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menyimpan pengaturan ringan satu mesin', async () => {
    const fetchMock = mockFetch({
      ...routes('OWNER'),
      'PUT /api/devices/5/settings': () => ({ status: 200, body: { ...settings(1), appliedVersion: 0 } }),
    })
    renderAt('/mesin')

    const dialog = await openSettings()
    const greeting = await within(dialog).findByLabelText('Sapaan', {}, LOAD)
    await waitFor(() => expect(greeting).toHaveValue('Silakan tempelkan jari'))
    fireEvent.change(greeting, { target: { value: 'Halo' } })
    fireEvent.change(within(dialog).getByLabelText('Nama di layar'), { target: { value: 'FIRST' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan pengaturan' }))

    expect(await within(dialog).findByText(/Tersimpan sebagai versi 1/, {}, LOAD)).toBeInTheDocument()
    expect(sent(fetchMock, 'PUT', '/api/devices/5/settings')).toEqual({
      headerText: '',
      greeting: 'Halo',
      nameDisplay: 'FIRST',
      brightness: 80,
      buzzerVolume: 50,
      duplicateNoticeSeconds: 60,
      heartbeatSeconds: 5,
    })
  })

  it('pengaturan semua mesin', async () => {
    const fetchMock = mockFetch({
      ...routes('HR_FINANCE'),
      'PUT /api/device-settings': () => ({ status: 200, body: [settings(2)] }),
    })
    renderAt('/mesin')

    fireEvent.click(await screen.findByRole('button', { name: 'Pengaturan semua mesin' }, LOAD))
    const dialog = await screen.findByRole('dialog', { name: 'Pengaturan semua mesin' })
    const brightness = await within(dialog).findByLabelText('Kecerahan layar (%)', {}, LOAD)
    await waitFor(() => expect(brightness).toHaveValue(80))
    fireEvent.change(brightness, { target: { value: '60' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan ke semua mesin' }))

    await waitFor(() => expect(sent(fetchMock, 'PUT', '/api/device-settings')).toMatchObject({ brightness: 60 }))
  })

  it('HR tidak melihat bagian Jaringan dan Kunci', async () => {
    const fetchMock = mockFetch(routes('HR_FINANCE'))
    renderAt('/mesin')

    const dialog = await openSettings()
    expect(await within(dialog).findByRole('region', { name: 'Tampilan & suara' }, LOAD)).toBeInTheDocument()
    expect(within(dialog).queryByRole('region', { name: 'Jaringan' })).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('region', { name: 'Kunci' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([input]) => String(input) === '/api/devices/5/network')).toBe(false)
  })

  it('jaringan meminta password lalu POST', async () => {
    const fetchMock = mockFetch({
      ...routes('OWNER'),
      'POST /api/devices/5/network': () => ({ status: 201, body: command() }),
    })
    renderAt('/mesin')

    const dialog = await openSettings()
    const section = await within(dialog).findByRole('region', { name: 'Jaringan' }, LOAD)
    expect(await within(section).findByText('Belum pernah diatur dari web', {}, LOAD)).toBeInTheDocument()
    fireEvent.change(within(section).getByLabelText('Nama Wi-Fi 1'), { target: { value: 'Kantor' } })
    fireEvent.change(within(section).getByLabelText('Password Wi-Fi 1'), { target: { value: 'rahasia123' } })
    fireEvent.change(within(section).getByLabelText('Alamat server baru'), { target: { value: 'http://10.0.0.5:8888' } })
    const apply = within(section).getByRole('button', { name: 'Terapkan jaringan' })
    expect(apply).toBeDisabled()
    fireEvent.change(within(section).getByLabelText('Password Anda'), { target: { value: 'akun-123' } })
    fireEvent.click(apply)

    await waitFor(() =>
      expect(sent(fetchMock, 'POST', '/api/devices/5/network')).toEqual({
        confirmPassword: 'akun-123',
        wifi: [{ ssid: 'Kantor', password: 'rahasia123' }],
        serverUrl: 'http://10.0.0.5:8888',
      }),
    )
    await waitFor(() => expect(within(section).getByLabelText('Password Anda')).toHaveValue(''))
    expect(within(section).getByLabelText('Password Wi-Fi 1')).toHaveValue('')
  })

  it('menampilkan status perintah dan Batalkan saat MENUNGGU', async () => {
    const fetchMock = mockFetch({
      ...routes('OWNER'),
      'GET /api/devices/5/network': () => ({ status: 200, body: network({ currentWifi: ['Kantor'], latest: command() }) }),
      'POST /api/device-commands/c1/cancel': () => ({
        status: 200,
        body: command({ status: 'DIBATALKAN', message: 'Dibatalkan.' }),
      }),
    })
    renderAt('/mesin')

    const dialog = await openSettings()
    const section = await within(dialog).findByRole('region', { name: 'Jaringan' }, LOAD)
    expect(await within(section).findByText('Menunggu mesin…', {}, LOAD)).toBeInTheDocument()
    expect(within(section).getByText(/Kantor/)).toBeInTheDocument()
    fireEvent.click(within(section).getByRole('button', { name: 'Batalkan' }))

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          ([input, init]) => String(input) === '/api/device-commands/c1/cancel' && init?.method === 'POST',
        ),
      ).toBe(true),
    )
  })

  it('ganti kunci meminta password', async () => {
    const fetchMock = mockFetch({
      ...routes('OWNER'),
      'POST /api/devices/5/rotate-key': () => ({ status: 201, body: command({ id: 'c2', type: 'ROTATE_KEY' }) }),
    })
    renderAt('/mesin')

    const dialog = await openSettings()
    const section = await within(dialog).findByRole('region', { name: 'Kunci' }, LOAD)
    const rotate = within(section).getByRole('button', { name: 'Ganti kunci' })
    expect(rotate).toBeDisabled()
    fireEvent.change(within(section).getByLabelText('Password Anda'), { target: { value: 'akun-123' } })
    fireEvent.click(rotate)

    await waitFor(() =>
      expect(sent(fetchMock, 'POST', '/api/devices/5/rotate-key')).toEqual({ confirmPassword: 'akun-123' }),
    )
  })

  it('status versi pengaturan di daftar mesin', async () => {
    mockFetch(
      routes('OWNER', [
        rakitan({ settingsVersion: 2, settingsAppliedVersion: 1 }),
        rakitan({ id: 6, name: 'Gudang', serialNumber: 'RK-000001', settingsVersion: 3, settingsAppliedVersion: 3 }),
      ]),
    )
    renderAt('/mesin')

    expect((await screen.findAllByText('Menunggu mesin menerapkan versi 2', {}, LOAD)).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Pengaturan terbaru sudah diterapkan ✓').length).toBeGreaterThan(0)
  })
})
