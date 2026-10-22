import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { BranchAccessCard } from '@/components/BranchAccessCard'
import { EnrollDialog } from '@/components/EnrollDialog'
import type { Branch, BranchAccess, Device, Employee, FingerprintOverview } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

const LOAD = { timeout: 5000 }

const employee: Employee = {
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
}

const branch = (id: number, name: string, active = true): Branch => ({
  id,
  code: `C${id}`,
  name,
  address: null,
  timezone: 'Asia/Jakarta',
  active,
})

const BRANCHES = [branch(1, 'Cabang Pusat'), branch(2, 'Cabang Gudang'), branch(3, 'Cabang Lama', false)]

const device = (id: number, name: string, branchId: number): Device =>
  ({
    id,
    name,
    serialNumber: `RK-${id}`,
    branchId,
    branchName: `Cabang ${branchId}`,
    active: true,
    protocol: 'RAKITAN_V1',
    online: true,
  }) as Device

const finger = (n: number, deviceId: number, deviceName: string) => ({
  finger: n,
  deviceId,
  deviceName,
  enrolledAt: '2026-10-17T03:00:00Z',
})

const overview = (fingers: FingerprintOverview['fingers'] = []): FingerprintOverview => ({
  consentAt: '2026-10-18T02:00:00Z',
  consentBy: 'owner',
  fingers,
  activeCommands: [],
  deletions: [],
  zkPushDeviceActive: false,
})

const access = (extra: number[], removed = 0): BranchAccess => ({
  placementBranchId: 1,
  extraBranchIds: extra,
  removedFingerprints: removed,
})

function client() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } })
}

function renderCard() {
  return render(
    <QueryClientProvider client={client()}>
      <BranchAccessCard employee={employee} />
    </QueryClientProvider>,
  )
}

const baseRoutes = (extra: number[], fingers: FingerprintOverview['fingers'] = []) => ({
  'GET /api/employees/12/branches': () => ({
    status: 200,
    body: access(extra),
  }),
  'GET /api/branches': () => ({ status: 200, body: BRANCHES }),
  'GET /api/devices': () => ({
    status: 200,
    body: [device(5, 'Rakitan Pusat', 1), device(6, 'Rakitan Gudang', 2)],
  }),
  'GET /api/employees/12/fingerprints': () => ({
    status: 200,
    body: overview(fingers),
  }),
})

const putCalls = (fetchMock: ReturnType<typeof mockFetch>) =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT')

function LocationProbe() {
  return <output data-testid="path">{useLocation().pathname}</output>
}

describe('Kartu Cabang absen', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('placement is checked, disabled and labelled', async () => {
    mockFetch(baseRoutes([]))
    renderCard()

    const placement = await screen.findByRole('checkbox', { name: 'Cabang Pusat' }, LOAD)
    expect(placement).toBeChecked()
    expect(placement).toBeDisabled()
    expect(screen.getByText('Penempatan')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Cabang Gudang' })).not.toBeChecked()
    expect(screen.queryByRole('checkbox', { name: 'Cabang Lama' })).not.toBeInTheDocument()
  })

  it('checking a branch and saving sends PUT with extraBranchIds', async () => {
    const fetchMock = mockFetch({
      ...baseRoutes([]),
      'PUT /api/employees/12/branches': () => ({
        status: 200,
        body: access([2]),
      }),
    })
    renderCard()

    expect(await screen.findByRole('button', { name: 'Simpan' }, LOAD)).toBeDisabled()
    fireEvent.click(await screen.findByRole('checkbox', { name: 'Cabang Gudang' }))
    fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))

    expect(await screen.findByText('Cabang absen disimpan.')).toBeInTheDocument()
    expect(JSON.parse(String(putCalls(fetchMock)[0][1]?.body))).toEqual({
      extraBranchIds: [2],
    })
  })

  it('unchecking a branch with fingers asks for confirmation; cancel sends nothing', async () => {
    const fetchMock = mockFetch({
      ...baseRoutes([2], [finger(1, 6, 'Rakitan Gudang'), finger(2, 6, 'Rakitan Gudang')]),
      'PUT /api/employees/12/branches': () => ({
        status: 200,
        body: access([], 2),
      }),
    })
    renderCard()

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Cabang Gudang' }, LOAD))
    // Simpan baru aktif setelah data mesin dan jari termuat.
    await waitFor(() => expect(screen.getByRole('button', { name: 'Simpan' })).toBeEnabled())
    fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('2 sidik jari di mesin Rakitan Gudang akan dihapus.')).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Batal' }))
    expect(putCalls(fetchMock)).toHaveLength(0)

    fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))
    fireEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', {
        name: 'Ya, simpan',
      }),
    )
    expect(await screen.findByText('Cabang absen disimpan. 2 sidik jari dihapus.')).toBeInTheDocument()
    expect(JSON.parse(String(putCalls(fetchMock)[0][1]?.body))).toEqual({
      extraBranchIds: [],
    })
  })
})

describe('Konfirmasi hapus jari tidak bisa terlewat', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('Simpan stays disabled while devices or fingerprints are pending, and warns when they fail', async () => {
    const fetchMock = mockFetch({
      ...baseRoutes([2], [finger(1, 6, 'Rakitan Gudang')]),
      'GET /api/devices': () => ({ status: 500, body: { detail: 'gagal' } }),
      'PUT /api/employees/12/branches': () => ({ status: 200, body: access([]) }),
    })
    renderCard()

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Cabang Gudang' }, LOAD))
    expect(screen.getByRole('button', { name: 'Simpan' })).toBeDisabled()
    expect(await screen.findByText('Data sidik jari belum bisa dimuat. Muat ulang halaman.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Simpan' })).toBeDisabled()
    expect(putCalls(fetchMock)).toHaveLength(0)
  })

  it('revoking with a finger on an unknown device still asks for confirmation', async () => {
    const fetchMock = mockFetch({
      ...baseRoutes([2], [finger(1, 99, 'Mesin Hilang')]),
      'PUT /api/employees/12/branches': () => ({ status: 200, body: access([], 1) }),
    })
    renderCard()

    fireEvent.click(await screen.findByRole('checkbox', { name: 'Cabang Gudang' }, LOAD))
    const save = screen.getByRole('button', { name: 'Simpan' })
    await waitFor(() => expect(save).toBeEnabled())
    fireEvent.click(save)

    const dialog = await screen.findByRole('dialog')
    expect(
      within(dialog).getByText('1 sidik jari di mesin yang tidak dikenal mungkin ikut dihapus.'),
    ).toBeInTheDocument()
    expect(putCalls(fetchMock)).toHaveLength(0)
    fireEvent.click(within(dialog).getByRole('button', { name: 'Ya, simpan' }))
    await waitFor(() => expect(putCalls(fetchMock)).toHaveLength(1))
  })
})

describe('Akses kartu', () => {
  afterEach(() => vi.unstubAllGlobals())

  const renderPage = () =>
    render(
      <QueryClientProvider client={client()}>
        <MemoryRouter initialEntries={['/karyawan/12']}>
          <LocationProbe />
          <AuthProvider>
            <App />
          </AuthProvider>
        </MemoryRouter>
      </QueryClientProvider>,
    )

  it('HR sees the card on the employee detail page', async () => {
    mockFetch({
      ...baseRoutes([]),
      'GET /api/auth/me': () => ({
        status: 200,
        body: {
          username: 'hr',
          role: 'HR_FINANCE',
          employeeId: null,
          employeeName: null,
          mustChangePassword: false,
        },
      }),
      'GET /api/employees/12': () => ({ status: 200, body: employee }),
      'GET /api/users': () => ({ status: 200, body: [] }),
    })
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Cabang absen' }, LOAD)).toBeInTheDocument()
  })

  it('an employee account never reaches the card', async () => {
    const fetchMock = mockFetch({
      ...baseRoutes([]),
      'GET /api/auth/me': () => ({
        status: 200,
        body: {
          username: 'budi',
          role: 'EMPLOYEE',
          employeeId: 12,
          employeeName: 'Budi',
          mustChangePassword: false,
        },
      }),
      'GET /api/employees/12': () => ({ status: 200, body: employee }),
    })
    renderPage()
    await waitFor(() => expect(fetchMock.mock.calls.some(([u]) => u === '/api/auth/me')).toBe(true))
    await waitFor(() => expect(screen.getByTestId('path')).not.toHaveTextContent('/karyawan/12'))
    expect(screen.queryByRole('heading', { name: 'Cabang absen' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([u]) => u === '/api/employees/12/branches')).toBe(false)
  })
})

describe('EnrollDialog', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('lists only devices in allowed branches', async () => {
    mockFetch({
      'GET /api/devices': () => ({
        status: 200,
        body: [device(5, 'Rakitan Pusat', 1), device(6, 'Rakitan Gudang', 2), device(7, 'Rakitan Timur', 3)],
      }),
    })
    render(
      <QueryClientProvider client={client()}>
        <EnrollDialog employee={employee} finger={1} allowedBranchIds={[1, 3]} onClose={() => {}} />
      </QueryClientProvider>,
    )
    const select = await screen.findByRole('combobox', { name: 'Mesin' }, LOAD)
    const labels = within(select)
      .getAllByRole('option')
      .map((o) => o.textContent)
    expect(labels).toEqual(['Rakitan Pusat (Cabang 1)', 'Rakitan Timur (Cabang 3)'])
  })
})
