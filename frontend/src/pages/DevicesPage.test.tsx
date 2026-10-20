import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Device, Me } from '@/lib/types'
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
const KEY = 'k'.repeat(43)

const me = (role: Me['role']): Me => ({ username: 'u', role, employeeId: null, employeeName: null, mustChangePassword: false })

const device = (overrides: Partial<Device> = {}): Device => ({
  id: 4,
  name: 'X401 Pusat',
  serialNumber: 'SN-X401',
  branchId: 1,
  branchName: 'Pusat',
  active: true,
  protocol: 'ZK_PUSH',
  acceptScansFrom: '2026-01-01T00:00:00Z',
  ignoredScanCount: 0,
  lastSeenAt: null,
  lastIp: null,
  firmware: null,
  online: false,
  keyIssued: false,
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

const rakitan = (overrides: Partial<Device> = {}) =>
  device({ id: 5, name: 'Uji Kampus', serialNumber: 'RK-7F2A9C', protocol: 'RAKITAN_V1', keyIssued: true, ...overrides })

const routes = (role: Me['role'], devices: Device[]) => ({
  'GET /api/auth/me': () => ({ status: 200, body: me(role) }),
  'GET /api/branches': () => ({ status: 200, body: [{ id: 1, code: 'PST', name: 'Pusat', active: true }] }),
  'GET /api/devices': () => ({ status: 200, body: devices }),
  'GET /api/devices/unregistered-attempts': () => ({ status: 200, body: [] }),
})

function body(fetchMock: ReturnType<typeof mockFetch>, method: string) {
  const call = fetchMock.mock.calls.find(([, init]) => init?.method === method)
  return JSON.parse(String(call?.[1]?.body))
}

describe('DevicesPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('owner adds rakitan device and sees setup code once', async () => {
    const fetchMock = mockFetch({
      ...routes('OWNER', [device()]),
      'POST /api/devices': () => ({
        status: 201,
        body: rakitan({ issuedKey: { deviceKey: KEY, setupServerUrl: 'http://192.168.43.20:8888' } }),
      }),
    })
    renderAt('/mesin')

    fireEvent.click(await screen.findByRole('button', { name: /^Tambah$/ }, LOAD))
    const form = await screen.findByRole('dialog')
    fireEvent.change(within(form).getByLabelText(/Jenis mesin/), { target: { value: 'RAKITAN_V1' } })
    fireEvent.change(within(form).getByLabelText(/^Nama/), { target: { value: 'Uji Kampus' } })
    fireEvent.change(within(form).getByLabelText(/Cabang/), { target: { value: '1' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Simpan' }))

    const setup = await screen.findByRole('dialog', { name: /Kode pengaturan/ }, LOAD)
    expect(body(fetchMock, 'POST')).toMatchObject({ protocol: 'RAKITAN_V1', name: 'Uji Kampus', branchId: 1 })
    expect(within(setup).getByText('RK-7F2A9C')).toBeInTheDocument()
    const code = within(setup).getByLabelText('Kode pengaturan') as HTMLTextAreaElement
    expect(code.value.startsWith('BAS1.')).toBe(true)
    expect(within(setup).getByLabelText('Alamat server')).toHaveValue('http://192.168.43.20:8888')

    fireEvent.click(within(setup).getByRole('button', { name: 'Selesai' }))
    await waitFor(() => expect(screen.queryByLabelText('Kode pengaturan')).not.toBeInTheDocument())
  })

  it('hr finance only sees ZK-Push option', async () => {
    mockFetch(routes('HR_FINANCE', [device()]))
    renderAt('/mesin')

    fireEvent.click(await screen.findByRole('button', { name: /^Tambah$/ }, LOAD))
    const select = within(await screen.findByRole('dialog')).getByLabelText(/Jenis mesin/)
    expect(within(select).getByRole('option', { name: 'Mesin ZK-Push (X401)' })).toBeInTheDocument()
    expect(within(select).queryByRole('option', { name: 'Alat rakitan' })).not.toBeInTheDocument()
  })

  it('edit form has no protocol field and sends no protocol', async () => {
    const fetchMock = mockFetch({
      ...routes('OWNER', [rakitan()]),
      'PUT /api/devices/5': () => ({ status: 200, body: rakitan({ name: 'Baru' }) }),
    })
    renderAt('/mesin')

    fireEvent.click((await screen.findAllByRole('button', { name: 'Ubah' }, LOAD))[0])
    const form = await screen.findByRole('dialog')
    expect(within(form).queryByLabelText(/Jenis mesin/)).not.toBeInTheDocument()
    expect(within(form).queryByLabelText(/Serial number/)).not.toBeInTheDocument()
    fireEvent.change(within(form).getByLabelText(/^Nama/), { target: { value: 'Baru' } })
    fireEvent.click(within(form).getByRole('button', { name: 'Simpan' }))

    await waitFor(() => expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(true))
    expect(body(fetchMock, 'PUT')).not.toHaveProperty('protocol')
  })

  it('new code asks for confirmation then posts to key endpoint', async () => {
    const fetchMock = mockFetch({
      ...routes('OWNER', [rakitan(), device()]),
      'POST /api/devices/5/key': () => ({
        status: 200,
        body: rakitan({ issuedKey: { deviceKey: KEY, setupServerUrl: null } }),
      }),
    })
    renderAt('/mesin')

    const buttons = await screen.findAllByRole('button', { name: 'Buat kode baru' }, LOAD)
    // Satu baris alat rakitan (tabel + kartu HP); mesin ZK-Push tidak punya tombol ini.
    expect(buttons).toHaveLength(2)
    fireEvent.click(buttons[0])
    const confirm = await screen.findByRole('dialog', { name: 'Buat kode pengaturan baru?' })
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
    // Mencabut kunci mesin: wajib konfirmasi ulang password (K-098).
    const submit = within(confirm).getByRole('button', { name: 'Ya, buat kode baru' })
    expect(submit).toBeDisabled()
    fireEvent.change(within(confirm).getByLabelText('Password Anda'), { target: { value: 'rahasia-owner' } })
    fireEvent.click(submit)

    const setup = await screen.findByRole('dialog', { name: /Kode pengaturan/ }, LOAD)
    expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/devices/5/key' && init?.method === 'POST')).toBe(
      true,
    )
    expect(body(fetchMock, 'POST')).toEqual({ confirmPassword: 'rahasia-owner' })
    expect((within(setup).getByLabelText('Kode pengaturan') as HTMLTextAreaElement).value).toMatch(/^BAS1\./)
  })

  it('hr finance has no new code button', async () => {
    mockFetch(routes('HR_FINANCE', [rakitan()]))
    renderAt('/mesin')

    expect((await screen.findAllByText('Uji Kampus', {}, LOAD)).length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Buat kode baru' })).not.toBeInTheDocument()
  })

  it('rakitan without first contact shows Menunggu pengaturan', async () => {
    mockFetch(routes('OWNER', [rakitan()]))
    renderAt('/mesin')

    expect((await screen.findAllByText('Menunggu pengaturan', {}, LOAD)).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Alat rakitan').length).toBeGreaterThan(0)
  })

  async function openNewCode() {
    mockFetch({
      ...routes('OWNER', [rakitan()]),
      'POST /api/devices/5/key': () => ({
        status: 200,
        body: rakitan({ issuedKey: { deviceKey: KEY, setupServerUrl: 'http://192.168.43.20:8888' } }),
      }),
    })
    renderAt('/mesin')
    fireEvent.click((await screen.findAllByRole('button', { name: 'Buat kode baru' }, LOAD))[0])
    const confirm = await screen.findByRole('dialog', { name: 'Buat kode pengaturan baru?' })
    fireEvent.change(within(confirm).getByLabelText('Password Anda'), { target: { value: 'rahasia-owner' } })
    fireEvent.click(within(confirm).getByRole('button', { name: 'Ya, buat kode baru' }))
    return screen.findByRole('dialog', { name: /Kode pengaturan/ }, LOAD)
  }

  it('copy without clipboard access selects the code and asks to copy manually', async () => {
    // Web lewat http:// ke IP LAN bukan secure context: navigator.clipboard tidak tersedia.
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })
    const setup = await openNewCode()

    fireEvent.click(within(setup).getByRole('button', { name: 'Salin kode' }))

    expect(await within(setup).findByText(/Tidak bisa menyalin otomatis/)).toBeInTheDocument()
    expect(within(setup).queryByText('Tersalin')).not.toBeInTheDocument()
    const code = within(setup).getByLabelText('Kode pengaturan') as HTMLTextAreaElement
    expect(document.activeElement).toBe(code)
  })

  it('copy with clipboard access writes the code', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    const setup = await openNewCode()

    fireEvent.click(within(setup).getByRole('button', { name: 'Salin kode' }))

    expect(await within(setup).findByText('Tersalin')).toBeInTheDocument()
    expect(writeText).toHaveBeenCalledWith(expect.stringMatching(/^BAS1\./))
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })
  })

  it('invalid server address hides the code', async () => {
    mockFetch({
      ...routes('OWNER', [rakitan()]),
      'POST /api/devices/5/key': () => ({
        status: 200,
        body: rakitan({ issuedKey: { deviceKey: KEY, setupServerUrl: null } }),
      }),
    })
    renderAt('/mesin')

    fireEvent.click((await screen.findAllByRole('button', { name: 'Buat kode baru' }, LOAD))[0])
    const confirm = await screen.findByRole('dialog', { name: 'Buat kode pengaturan baru?' })
    fireEvent.change(within(confirm).getByLabelText('Password Anda'), { target: { value: 'rahasia-owner' } })
    fireEvent.click(within(confirm).getByRole('button', { name: 'Ya, buat kode baru' }))
    const setup = await screen.findByRole('dialog', { name: /Kode pengaturan/ }, LOAD)
    fireEvent.change(within(setup).getByLabelText('Alamat server'), { target: { value: '10.0.0.5:8888' } })

    expect(within(setup).getByText('Alamat harus diawali http:// atau https://')).toBeInTheDocument()
    expect(within(setup).queryByLabelText('Kode pengaturan')).not.toBeInTheDocument()
  })
  it('rakitan shows sensor usage and warns when almost full', async () => {
    mockFetch(
      routes('OWNER', [
        rakitan({ keyFirstUsedAt: '2026-10-18T02:00:00Z', sensorUsed: 12, sensorCapacity: 150 }),
        rakitan({ id: 6, name: 'Uji Gudang', keyFirstUsedAt: '2026-10-18T02:00:00Z', sensorUsed: 140, sensorCapacity: 150, sensorAlmostFull: true }),
      ]),
    )
    renderAt('/mesin')

    expect((await screen.findAllByText('Sensor 12/150', {}, LOAD)).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Sensor 140/150').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Kapasitas sensor hampir penuh.').length).toBe(screen.getAllByText('Sensor 140/150').length)
  })
})
