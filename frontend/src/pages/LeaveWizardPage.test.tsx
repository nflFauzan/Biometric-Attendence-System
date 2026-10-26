import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { sampleRequest } from '@/components/leave/fixtures'
import type { LeaveRequest, LeaveType, Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'
import { pickDate } from '@/test/pickers'

const employee: Me = { username: 'budi', role: 'EMPLOYEE', employeeId: 12, employeeName: 'Budi Santoso', mustChangePassword: false }

const izin: LeaveType = { id: 1, name: 'Izin', requiresDocument: false, paid: null, description: 'Keperluan pribadi', active: true, requiresOwnerApproval: false, letterTemplate: 'IZIN' }
const cuti: LeaveType = { id: 2, name: 'Cuti', requiresDocument: true, paid: null, description: 'Cuti tahunan', active: true, requiresOwnerApproval: true, letterTemplate: 'CUTI' }
const lama: LeaveType = { ...izin, id: 3, name: 'Lama', active: false }

const draft = (overrides: Partial<LeaveRequest> = {}): LeaveRequest =>
  sampleRequest({
    id: 9,
    status: 'DRAFT',
    leaveTypeId: 2,
    leaveTypeName: 'Cuti',
    requiresDocument: true,
    submittedAt: null,
    submittedBy: null,
    nextStage: null,
    reason: 'Acara keluarga',
    contactPhone: '08123',
    leaveAddress: 'Jl. Mawar 1',
    ...overrides,
  })

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

const baseRoutes = {
  'GET /api/auth/me': () => ({ status: 200, body: employee }),
  'GET /api/leave-types': () => ({ status: 200, body: [izin, cuti, lama] }),
  'GET /api/me/signature/status': () => ({ status: 200, body: { exists: false, updatedAt: null } }),
}

describe('LeaveWizardPage', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=abc'
  })
  afterEach(() => vi.unstubAllGlobals())

  it('creates draft after choosing type then shows CUTI fields', async () => {
    const fetch = mockFetch({
      ...baseRoutes,
      'POST /api/me/leave-requests': () => ({ status: 201, body: draft({ reason: '', contactPhone: null, leaveAddress: null }) }),
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft({ reason: '', contactPhone: null, leaveAddress: null }) }),
    })
    renderAt('/izin-saya/baru')

    const group = await screen.findByRole('radiogroup', { name: 'Jenis izin' })
    expect(screen.queryByRole('radio', { name: /Lama/ })).not.toBeInTheDocument()
    expect(group).toBeInTheDocument()
    expect(await screen.findByText('Butuh persetujuan HR & Owner')).toBeInTheDocument()
    expect(screen.getByText('Wajib surat')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Lanjut' })).toBeDisabled()

    fireEvent.click(screen.getByRole('radio', { name: /^Cuti/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Lanjut' }))

    expect(await screen.findByLabelText(/Alamat selama cuti/)).toBeInTheDocument()
    expect(screen.getByLabelText(/No\. HP yang bisa dihubungi/)).toHaveAttribute('inputmode', 'tel')
    const post = fetch.mock.calls.find(([url, init]) => url === '/api/me/leave-requests' && init?.method === 'POST')!
    expect(JSON.parse(String(post[1]!.body))).toEqual({ leaveTypeId: 2, draft: true })
  })

  it('menghitung jumlah hari kalender inklusif dan menyimpan draft saat Lanjut', async () => {
    const fetch = mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
    })
    renderAt('/izin-saya/9/ubah?langkah=2')

    const start = await screen.findByLabelText(/Tanggal mulai/)
    pickDate(start, '2026-11-03')
    pickDate(screen.getByLabelText(/Tanggal selesai/), '2026-11-06')
    expect(screen.getByText('4 hari')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Lanjut' }))
    expect(await screen.findByText(/Tanda tangan dipakai di surat/)).toBeInTheDocument()
    const put = fetch.mock.calls.find(([url, init]) => url === '/api/me/leave-requests/9' && init?.method === 'PUT')!
    expect(JSON.parse(String(put[1]!.body))).toMatchObject({
      leaveTypeId: 2,
      startDate: '2026-11-03',
      endDate: '2026-11-06',
      reason: 'Acara keluarga',
      contactPhone: '08123',
      leaveAddress: 'Jl. Mawar 1',
      draft: true,
    })
  })

  it('submit error returns to details step', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft({ leaveAddress: null }) }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft({ leaveAddress: null }) }),
      'POST /api/me/leave-requests/9/submit': () => ({ status: 400, body: { detail: 'Alamat selama cuti wajib diisi' } }),
    })
    renderAt('/izin-saya/9/ubah?langkah=4')

    fireEvent.click(await screen.findByRole('button', { name: 'Kirim' }))

    expect(await screen.findByText('Alamat selama cuti wajib diisi')).toBeInTheDocument()
    expect(await screen.findByLabelText(/Alamat selama cuti/)).toBeInTheDocument()
  })

  it('409 bentrok tanggal kembali ke langkah 2 dengan pesan', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'POST /api/me/leave-requests/9/submit': () => ({ status: 409, body: { detail: 'Bertabrakan dengan pengajuan lain' } }),
    })
    renderAt('/izin-saya/9/ubah?langkah=4')
    fireEvent.click(await screen.findByRole('button', { name: 'Kirim' }))
    expect(await screen.findByText('Bertabrakan dengan pengajuan lain')).toBeInTheDocument()
    expect(await screen.findByLabelText(/Tanggal mulai/)).toBeInTheDocument()
  })

  it('400 soal surat kembali ke langkah 3', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'POST /api/me/leave-requests/9/submit': () => ({ status: 400, body: { detail: 'Jenis ini wajib surat pendukung' } }),
    })
    renderAt('/izin-saya/9/ubah?langkah=4')
    fireEvent.click(await screen.findByRole('button', { name: 'Kirim' }))
    expect(await screen.findByText('Jenis ini wajib surat pendukung')).toBeInTheDocument()
    expect(await screen.findByLabelText('Pilih file lampiran')).toBeInTheDocument()
  })

  it('error lain tetap di pratinjau', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'POST /api/me/leave-requests/9/submit': () => ({ status: 500, body: { detail: 'Server sibuk' } }),
    })
    renderAt('/izin-saya/9/ubah?langkah=4')
    fireEvent.click(await screen.findByRole('button', { name: 'Kirim' }))
    expect(await screen.findByText('Server sibuk')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Kirim' })).toBeInTheDocument()
  })

  it('Kirim berhasil membawa ke detail pengajuan', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'POST /api/me/leave-requests/9/submit': () => ({ status: 200, body: draft({ status: 'PERLU_TINJAU_OWNER' }) }),
    })
    renderAt('/izin-saya/9/ubah?langkah=4')
    fireEvent.click(await screen.findByRole('button', { name: 'Kirim' }))
    expect(await screen.findByText('Cuti · Budi Santoso')).toBeInTheDocument()
  })

  it('Lihat PDF membuka blob di tab baru', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:pdf')
    URL.revokeObjectURL = vi.fn()
    const open = vi.spyOn(window, 'open').mockReturnValue({} as Window)
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'GET /api/me/leave-requests/9/letter.pdf': () => ({ status: 200, body: {} }),
    })
    renderAt('/izin-saya/9/ubah?langkah=4')
    fireEvent.click(await screen.findByRole('button', { name: 'Lihat PDF' }))
    await waitFor(() => expect(open).toHaveBeenCalledWith('blob:pdf', '_blank'))
    open.mockRestore()
  })

  it('pengajuan yang bukan draft tidak bisa diubah', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft({ status: 'PERLU_TINJAU_OWNER' }) }),
    })
    renderAt('/izin-saya/9/ubah')
    expect(await screen.findByText(/Hanya draft yang bisa diubah/)).toBeInTheDocument()
  })

  it('Kembali tetap pindah langkah saat simpan draft gagal dan menampilkan galat di langkah sebelumnya', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 500, body: { detail: 'Server sibuk' } }),
    })
    renderAt('/izin-saya/9/ubah?langkah=3')
    fireEvent.click(await screen.findByRole('button', { name: 'Kembali' }))
    expect(await screen.findByLabelText(/Tanggal mulai/)).toBeInTheDocument()
    expect(await screen.findByText('Server sibuk')).toBeInTheDocument()
    expect(screen.getByLabelText(/Alamat selama cuti/)).toHaveValue('Jl. Mawar 1')
  })

  it('400 Rentang izin maksimal kembali ke langkah 2', async () => {
    mockFetch({
      ...baseRoutes,
      'GET /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'PUT /api/me/leave-requests/9': () => ({ status: 200, body: draft() }),
      'POST /api/me/leave-requests/9/submit': () => ({ status: 400, body: { detail: 'Rentang izin maksimal 30 hari' } }),
    })
    renderAt('/izin-saya/9/ubah?langkah=4')
    fireEvent.click(await screen.findByRole('button', { name: 'Kirim' }))
    expect(await screen.findByText('Rentang izin maksimal 30 hari')).toBeInTheDocument()
    expect(await screen.findByLabelText(/Tanggal mulai/)).toBeInTheDocument()
  })
})
