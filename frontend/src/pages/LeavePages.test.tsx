import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { LeaveRequest, LeaveType, LeaveUsage, Me, PendingLeaveSummary } from '@/lib/types'
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

const employee: Me = { username: 'budi', role: 'EMPLOYEE', employeeId: 12, employeeName: 'Budi Santoso', mustChangePassword: false }
const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }

const sakit: LeaveType = { id: 1, name: 'Sakit', requiresDocument: true, paid: null, description: 'Lampirkan surat dokter', active: true }

const request = (overrides: Partial<LeaveRequest> = {}): LeaveRequest => ({
  id: 7,
  employeeId: 12,
  employeeNumber: 'K-012',
  employeeName: 'Budi Santoso',
  position: 'Staf gudang',
  branchId: 1,
  branchName: 'Pusat',
  leaveTypeId: 1,
  leaveTypeName: 'Sakit',
  requiresDocument: true,
  startDate: '2026-10-05',
  endDate: '2026-10-06',
  calendarDays: 2,
  workingDays: 2,
  reason: 'Demam',
  status: 'MENUNGGU',
  submittedBy: 'budi',
  submittedAt: '2026-10-05T01:00:00Z',
  decidedBy: null,
  decidedAt: null,
  decisionNote: null,
  cancelledBy: null,
  cancelledAt: null,
  cancelReason: null,
  attachments: [],
  ...overrides,
})

describe('Halaman izin dan cuti Fase 4', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('karyawan mengisi form, melampirkan surat, lalu dibawa ke detail pengajuannya', async () => {
    const created = request()
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests': () => ({ status: 200, body: [] }),
      'GET /api/leave-types': () => ({ status: 200, body: [sakit, { ...sakit, id: 2, name: 'Lama', active: false }] }),
      'POST /api/me/leave-requests': () => ({ status: 201, body: created }),
      'POST /api/me/leave-requests/7/attachments': () => ({
        status: 201,
        body: { id: 3, originalName: 'surat.pdf', contentType: 'application/pdf', sizeBytes: 9, uploadedBy: 'budi', uploadedAt: '2026-10-05T01:01:00Z' },
      }),
      'GET /api/me/leave-requests/7': () => ({ status: 200, body: created }),
    })
    renderAt('/izin-saya')

    fireEvent.click(await screen.findByRole('button', { name: 'Ajukan izin' }))
    const dialog = await screen.findByRole('dialog')
    const typeSelect = within(dialog).getByLabelText(/Jenis izin/)
    await waitFor(() => expect(within(typeSelect).getByRole('option', { name: 'Sakit (wajib surat)' })).toBeInTheDocument())
    // Jenis yang nonaktif tidak bisa dipilih.
    expect(within(typeSelect).queryByRole('option', { name: /Lama/ })).not.toBeInTheDocument()
    fireEvent.change(typeSelect, { target: { value: '1' } })
    fireEvent.change(within(dialog).getByLabelText(/Tanggal mulai/), { target: { value: '2026-10-05' } })
    fireEvent.change(within(dialog).getByLabelText(/Tanggal selesai/), { target: { value: '2026-10-06' } })
    fireEvent.change(within(dialog).getByLabelText(/Alasan/), { target: { value: 'Demam' } })
    const file = new File(['%PDF-1.4'], 'surat.pdf', { type: 'application/pdf' })
    fireEvent.change(within(dialog).getByLabelText(/Surat pendukung/), { target: { files: [file] } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Kirim pengajuan' }))

    expect(await screen.findByText('Sakit · Budi Santoso')).toBeInTheDocument()
    const post = fetch.mock.calls.find(([url, init]) => url === '/api/me/leave-requests' && init?.method === 'POST')
    expect(JSON.parse(String(post?.[1]?.body))).toEqual({
      employeeId: null,
      leaveTypeId: 1,
      startDate: '2026-10-05',
      endDate: '2026-10-06',
      reason: 'Demam',
    })
    const upload = fetch.mock.calls.find(([url]) => url === '/api/me/leave-requests/7/attachments')
    expect(upload).toBeDefined()
    expect((upload![1]!.body as FormData).get('file')).toBe(file)
  })

  it('HR melihat peringatan wajib surat dan bisa menyetujui dengan catatan', async () => {
    let current = request()
    const usage: LeaveUsage = { employeeId: 12, year: 2026, types: [{ leaveTypeId: 1, leaveTypeName: 'Sakit', requests: 1, calendarDays: 3, workingDays: 3 }], pendingRequests: 0 }
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/leave-requests/7': () => ({ status: 200, body: current }),
      'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: usage }),
      'POST /api/leave-requests/7/approve': () => {
        current = request({ status: 'DISETUJUI', decidedBy: 'hr', decidedAt: '2026-10-05T02:00:00Z', decisionNote: 'Cepat sembuh' })
        return { status: 200, body: current }
      },
    })
    renderAt('/izin/7')

    expect(await screen.findByText(/wajib surat pendukung/)).toBeInTheDocument()
    expect(await screen.findByText(/3 hari · 3 hari kerja · 1×/)).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Setujui' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText(/Catatan/), { target: { value: 'Cepat sembuh' } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Setujui' }))

    expect(await screen.findByText('“Cepat sembuh”')).toBeInTheDocument()
    const approve = fetch.mock.calls.find(([url]) => url === '/api/leave-requests/7/approve')
    expect(JSON.parse(String(approve?.[1]?.body))).toEqual({ note: 'Cepat sembuh' })
    expect(screen.queryByRole('button', { name: 'Setujui' })).not.toBeInTheDocument()
  })

  it('surat pengajuan siap cetak memuat data karyawan dan tombol cetak/PDF', async () => {
    const print = vi.fn()
    vi.stubGlobal('print', print)
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({
        status: 200,
        body: request({ status: 'DISETUJUI', decidedBy: 'hr', decidedAt: '2026-10-05T02:00:00Z', leaveTypeName: 'Cuti' }),
      }),
    })
    renderAt('/izin-saya/7/surat')

    expect(await screen.findByRole('heading', { name: 'Surat Permohonan Izin / Cuti' })).toBeInTheDocument()
    expect(screen.getByText('Cuti', { selector: 'strong' })).toBeInTheDocument()
    expect(screen.getByText(/IZ-000007/)).toBeInTheDocument()
    expect(screen.getByText('Staf gudang')).toBeInTheDocument()
    expect(screen.getByText('Disetujui oleh,')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Cetak / Simpan PDF' }))
    expect(print).toHaveBeenCalled()
  })

  it('lonceng notifikasi menampilkan jumlah dan daftar pengajuan yang menunggu', async () => {
    const pending: PendingLeaveSummary = {
      count: 2,
      items: [
        { id: 7, employeeId: 12, employeeName: 'Budi Santoso', branchName: 'Pusat', leaveTypeName: 'Sakit', startDate: '2026-10-05', endDate: '2026-10-05', submittedAt: '2026-10-05T01:00:00Z' },
        { id: 8, employeeId: 13, employeeName: 'Siti Aminah', branchName: 'Pusat', leaveTypeName: 'Cuti', startDate: '2026-10-07', endDate: '2026-10-09', submittedAt: '2026-10-05T02:00:00Z' },
      ],
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/leave-requests/pending': () => ({ status: 200, body: pending }),
      'GET /api/leave-requests?status=MENUNGGU': () => ({ status: 200, body: [request()] }),
    })
    renderAt('/izin')

    const bell = await screen.findByRole('button', { name: '2 pengajuan izin menunggu persetujuan' })
    fireEvent.click(bell)
    expect(await screen.findByText('Siti Aminah')).toBeInTheDocument()
    expect(screen.getByText('Siti Aminah').closest('a')).toHaveAttribute('href', '/izin/8')
  })
})
