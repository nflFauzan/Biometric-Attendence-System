import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { EffectiveRule, Employee, Me, WorkRule } from '@/lib/types'
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

const rule = (overrides: Partial<WorkRule> = {}): WorkRule => ({
  id: 1,
  scope: 'GLOBAL',
  categoryId: null,
  categoryName: null,
  employeeId: null,
  employeeName: null,
  effectiveFrom: '2026-10-01',
  workStart: '08:00:00',
  workEnd: '17:00:00',
  breakStart: null,
  breakEnd: null,
  lateToleranceMinutes: 0,
  overtimeStart: '17:00:00',
  workDays: ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'],
  dailyWage: null,
  overtimeRatePerHour: null,
  baseSalary: null,
  minScanGapMinutes: null,
  note: null,
  createdBy: 'owner',
  locked: true,
  ...overrides,
})

const employee: Employee = {
  id: 12,
  employeeNumber: 'K-012',
  fullName: 'Budi Santoso',
  phone: null,
  branchId: 1,
  branchName: 'Pusat',
  categoryId: 2,
  categoryName: 'Supir/Kenek',
  position: null,
  joinDate: '2026-01-05',
  endDate: null,
  active: true,
  devicePin: null,
}

const baseRoutes = (role: Me['role'], rules: WorkRule[]) => ({
  'GET /api/auth/me': () => ({ status: 200, body: me(role) }),
  'GET /api/employee-categories': () => ({ status: 200, body: [] }),
  'GET /api/employees': () => ({ status: 200, body: [employee] }),
  'GET /api/work-rules?scope=GLOBAL': () => ({ status: 200, body: rules }),
})

describe('WorkRulesPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('HR/Finance hanya melihat aturan tanpa tombol ubah', async () => {
    mockFetch(baseRoutes('HR_FINANCE', [rule({ locked: false, effectiveFrom: '2099-01-01' })]))
    renderAt('/aturan-kerja')

    expect(await screen.findByText('Berlaku mulai 1 Jan 2099', undefined, LOAD)).toBeInTheDocument()
    expect(screen.getByText('Hanya Owner yang dapat mengubah aturan kerja dan tarif.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Tambah versi/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ubah' })).not.toBeInTheDocument()
  })

  it('Owner hanya bisa mengubah versi yang belum berlaku (K-050)', async () => {
    mockFetch(
      baseRoutes('OWNER', [
        rule({ id: 2, effectiveFrom: '2099-01-01', locked: false, workStart: '09:00:00' }),
        rule({ id: 1, effectiveFrom: '2026-10-01', locked: true }),
      ]),
    )
    renderAt('/aturan-kerja')

    const scheduled = (await screen.findByText('Berlaku mulai 1 Jan 2099', undefined, LOAD)).closest('li')!
    const locked = screen.getByText('Berlaku mulai 1 Okt 2026').closest('li')!
    expect(within(scheduled).getByText('Terjadwal')).toBeInTheDocument()
    expect(within(scheduled).getByRole('button', { name: 'Ubah' })).toBeInTheDocument()
    expect(within(scheduled).getByText('09.00')).toBeInTheDocument()
    expect(within(locked).getByText('Terkunci')).toBeInTheDocument()
    expect(within(locked).queryByRole('button', { name: 'Ubah' })).not.toBeInTheDocument()
    expect(within(locked).getByText('Sen, Sel, Rab, Kam, Jum, Sab')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tambah versi/ })).toBeInTheDocument()
  })

  it('Owner menambah versi dengan parameter kosong dikirim sebagai null', async () => {
    const fetchMock = mockFetch({
      ...baseRoutes('OWNER', []),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/work-rules': () => ({ status: 201, body: rule({ id: 3 }) }),
    })
    renderAt('/aturan-kerja')

    fireEvent.click(await screen.findByRole('button', { name: /Tambah versi/ }, LOAD))
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText(/Berlaku mulai/), { target: { value: '2099-02-01' } })
    fireEvent.change(within(dialog).getByLabelText('Jam masuk'), { target: { value: '08:00' } })
    fireEvent.click(within(dialog).getByLabelText('Senin'))
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    const body = JSON.parse(String(post?.[1]?.body))
    expect(body).toMatchObject({ scope: 'GLOBAL', effectiveFrom: '2099-02-01', workStart: '08:00', workDays: ['MONDAY'] })
    expect(body.workEnd).toBeNull()
    expect(body.dailyWage).toBeNull()
    expect(body.minScanGapMinutes).toBeNull()
  })

  it('menampilkan jeda minimum antar scan (K-092)', async () => {
    mockFetch(baseRoutes('HR_FINANCE', [rule({ minScanGapMinutes: 5 })]))
    renderAt('/aturan-kerja')

    const version = (await screen.findByText('Berlaku mulai 1 Okt 2026', undefined, LOAD)).closest('li')!
    expect(within(version).getByText('Jeda minimum antar scan')).toBeInTheDocument()
    expect(within(version).getByText('5 menit')).toBeInTheDocument()
  })

  it('jeda minimum yang dikosongkan dikirim sebagai null, bukan 0', async () => {
    const fetchMock = mockFetch({
      ...baseRoutes('OWNER', []),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'POST /api/work-rules': () => ({ status: 201, body: rule({ id: 3 }) }),
    })
    renderAt('/aturan-kerja')

    fireEvent.click(await screen.findByRole('button', { name: /Tambah versi/ }, LOAD))
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText(/Berlaku mulai/), { target: { value: '2099-02-01' } })
    const gap = within(dialog).getByLabelText(/Jeda minimum antar scan/)
    fireEvent.change(gap, { target: { value: '7' } })
    fireEvent.change(gap, { target: { value: '' } })
    fireEvent.change(within(dialog).getByLabelText('Jam masuk'), { target: { value: '08:00' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body)).minScanGapMinutes).toBeNull()
  })

  it('panel aturan berlaku menampilkan asal setiap nilai', async () => {
    const effective: EffectiveRule = {
      employeeId: 12,
      employeeName: 'Budi Santoso',
      categoryId: 2,
      categoryName: 'Supir/Kenek',
      date: '2026-10-12',
      values: { ...rule(), workStart: '09:00:00', workDays: null, overtimeStart: null },
      sources: {
        WORK_START: { level: 'EMPLOYEE', versionId: 9, effectiveFrom: '2026-10-05' },
        WORK_END: { level: 'GLOBAL', versionId: 1, effectiveFrom: '2026-10-01' },
      },
      missing: ['batas mulai lembur', 'hari kerja'],
    }
    mockFetch({
      ...baseRoutes('HR_FINANCE', []),
      'GET /api/work-rules/effective?employeeId=12&date=2026-10-12': () => ({ status: 200, body: effective }),
    })
    renderAt('/aturan-kerja')

    await screen.findByRole('option', { name: /Budi Santoso/ }, LOAD)
    fireEvent.change(screen.getByLabelText('Karyawan'), { target: { value: '12' } })
    fireEvent.change(screen.getByLabelText('Tanggal'), { target: { value: '2026-10-12' } })

    expect(await screen.findByText('Karyawan · sejak 5 Okt 2026')).toBeInTheDocument()
    expect(screen.getByText('Global · sejak 1 Okt 2026')).toBeInTheDocument()
    expect(screen.getByText(/Aturan belum lengkap: batas mulai lembur, hari kerja/)).toBeInTheDocument()
  })
})
