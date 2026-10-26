import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Employee, Me } from '@/lib/types'
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

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }
const owner: Me = { username: 'owner', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: false }

const budi: Employee = {
  id: 12, employeeNumber: 'K-012', fullName: 'Budi Santoso', phone: null, branchId: 1, branchName: 'Cabang Pusat',
  categoryId: 2, categoryName: 'Staf', position: null, joinDate: '2026-01-05', endDate: null, active: true, devicePin: '12',
  email: null, gender: 'PEREMPUAN', birthDate: '1990-05-01', address: 'Jl. Lama 1', notes: null,
}

const routes = (me: Me) => ({
  'PUT /api/employees/12': () => ({ status: 200, body: budi }),
  'POST /api/employees': () => ({
    status: 201,
    body: { ...budi, id: 13, employeeNumber: '006', fullName: 'Eni Daniyati' },
  }),
  'GET /api/auth/me': () => ({ status: 200, body: me }),
  'GET /api/employees': () => ({ status: 200, body: [budi] }),
  'GET /api/branches': () => ({ status: 200, body: [] }),
  'GET /api/employee-categories': () => ({ status: 200, body: [] }),
})

/** K-135: Owner dan F&A menambah/mengubah karyawan; K-133: akun + pesan WA; K-134: tanggal masuk otomatis. */
describe('Daftar karyawan', () => {
  afterEach(() => vi.unstubAllGlobals())

  async function openCreate(me: Me) {
    const fetch = mockFetch(routes(me))
    renderAt('/karyawan')
    fireEvent.click(await screen.findByRole('button', { name: 'Tambah' }, LOAD))
    await screen.findByLabelText(/Nama lengkap/)
    return fetch
  }

  function fillIdentity() {
    fireEvent.change(screen.getByLabelText(/No\. karyawan/), { target: { value: '006' } })
    fireEvent.change(screen.getByLabelText(/Nama lengkap/), { target: { value: 'Eni Daniyati' } })
  }

  async function submitAndReadBody(fetch: ReturnType<typeof mockFetch>, url: string, method: string) {
    fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))
    const matches = ([u, init]: Parameters<typeof fetch>) => u === url && init?.method === method
    await waitFor(() => expect(fetch.mock.calls.some(matches)).toBe(true))
    return JSON.parse(String(fetch.mock.calls.find(matches)![1]!.body))
  }

  it('Finance & Accounting bisa menambah dan mengubah', async () => {
    mockFetch(routes(hr))
    renderAt('/karyawan')
    expect((await screen.findAllByRole('link', { name: 'Budi Santoso' }, LOAD)).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: 'Tambah' })).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Ubah' }).length).toBeGreaterThan(0)
    expect(screen.queryByRole('link', { name: 'Impor dari Excel' })).not.toBeInTheDocument()
  })

  it('form tambah tanpa tanggal masuk/keluar dan usulan akun mengikuti nama', async () => {
    await openCreate(owner)
    expect(screen.queryByLabelText(/Tanggal masuk/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Tanggal keluar/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('Buatkan akun login')).toBeChecked()
    fillIdentity()
    expect(screen.getByLabelText(/Username/)).toHaveValue('eni@takka.id')
    expect(screen.getByLabelText(/Password sementara/)).toHaveValue('eni-006-takka')
    expect(screen.getByLabelText('PIN mesin')).toHaveValue('006')
  })

  it('username yang diubah manual tidak ditimpa', async () => {
    await openCreate(owner)
    fillIdentity()
    fireEvent.change(screen.getByLabelText(/Username/), { target: { value: 'eni.d@takka.id' } })
    fireEvent.change(screen.getByLabelText(/Nama lengkap/), { target: { value: 'Eni Daniyati Putri' } })
    expect(screen.getByLabelText(/Username/)).toHaveValue('eni.d@takka.id')
    expect(screen.getByLabelText(/Password sementara/)).toHaveValue('eni.d-006-takka')
  })

  it('menyimpan karyawan dengan akun lalu menampilkan pesan', async () => {
    const fetch = await openCreate(owner)
    fillIdentity()
    const body = await submitAndReadBody(fetch, '/api/employees', 'POST')
    expect(body).toMatchObject({
      employeeNumber: '006',
      joinDate: null,
      devicePin: '006',
      account: { username: 'eni@takka.id', password: 'eni-006-takka', role: 'EMPLOYEE' },
    })
    expect(await screen.findByDisplayValue(/Halo Kak Eni Daniyati!/, {}, LOAD)).toBeInTheDocument()
  })

  it('tanpa centang akun, body tanpa account dan tanpa jendela pesan', async () => {
    const fetch = await openCreate(owner)
    fillIdentity()
    fireEvent.click(screen.getByLabelText('Buatkan akun login'))
    expect(screen.queryByLabelText(/Username/)).not.toBeInTheDocument()
    const body = await submitAndReadBody(fetch, '/api/employees', 'POST')
    expect(body.account).toBeNull()
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Simpan' })).not.toBeInTheDocument())
    expect(screen.queryByDisplayValue(/Halo Kak/)).not.toBeInTheDocument()
  })

  it('F&A bisa menambah karyawan tetapi tidak memilih role', async () => {
    const fetch = await openCreate(hr)
    expect(screen.queryByLabelText(/^Role/)).not.toBeInTheDocument()
    fillIdentity()
    const body = await submitAndReadBody(fetch, '/api/employees', 'POST')
    expect(body.account.role).toBe('EMPLOYEE')
  })

  it('Owner bisa memilih role akun', async () => {
    const fetch = await openCreate(owner)
    fillIdentity()
    fireEvent.change(screen.getByLabelText(/^Role/), { target: { value: 'HR_FINANCE' } })
    const body = await submitAndReadBody(fetch, '/api/employees', 'POST')
    expect(body.account.role).toBe('HR_FINANCE')
  })

  it('form ubah: ada Tanggal keluar dan Aktif, tanpa Tanggal masuk dan bagian akun', async () => {
    const fetch = mockFetch(routes(owner))
    renderAt('/karyawan')
    fireEvent.click((await screen.findAllByRole('button', { name: 'Ubah' }, LOAD))[0])
    await screen.findByLabelText('Alamat')
    expect(screen.getByLabelText(/Tanggal keluar/)).toBeInTheDocument()
    expect(screen.getByLabelText('Aktif')).toBeInTheDocument()
    expect(screen.queryByLabelText(/Tanggal masuk/)).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Buatkan akun login')).not.toBeInTheDocument()
    const sent = await submitAndReadBody(fetch, '/api/employees/12', 'PUT')
    expect(sent.joinDate).toBe('2026-01-05')
    expect(sent.account).toBeNull()
  })

  it('form memuat dan mengirim profil lengkap (K-129)', async () => {
    const fetch = mockFetch(routes(owner))
    renderAt('/karyawan')
    fireEvent.click((await screen.findAllByRole('button', { name: 'Ubah' }, LOAD))[0])
    const address = await screen.findByLabelText('Alamat')
    expect(address).toHaveValue('Jl. Lama 1')
    fireEvent.change(address, { target: { value: 'Jl. Baru 2' } })
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'budi@contoh.id' } })
    const sent = await submitAndReadBody(fetch, '/api/employees/12', 'PUT')
    expect(sent).toMatchObject({
      address: 'Jl. Baru 2',
      email: 'budi@contoh.id',
      gender: 'PEREMPUAN',
      birthDate: '1990-05-01',
      notes: null,
    })
  })
})
