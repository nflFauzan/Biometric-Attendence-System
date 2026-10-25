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
const owner: Me = { username: 'owner', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: false }
const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }

const sakit: LeaveType = { id: 1, name: 'Sakit', requiresDocument: true, paid: null, description: 'Lampirkan surat dokter', active: true, requiresOwnerApproval: true, letterTemplate: 'SAKIT' }

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
  status: 'PERLU_TINJAU_HR',
  submittedBy: 'budi',
  submittedAt: '2026-10-05T01:00:00Z',
  decidedBy: null,
  decidedAt: null,
  decisionNote: null,
  cancelledBy: null,
  cancelledAt: null,
  cancelReason: null,
  attachments: [],
  contactPhone: null,
  leaveAddress: null,
  delegateTo: null,
  healthFacility: null,
  letterTemplate: 'SAKIT',
  requiresOwnerApproval: false,
  createdAt: '2026-10-05T00:59:00Z',
  approvals: [],
  nextStage: 'HR',
  canDecide: true,
  canRecordOwnerDecision: false,
  resubmittedFromId: null,
  ...overrides,
})

describe('Halaman izin dan cuti Fase 4', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('karyawan: pengajuan menunggu menampilkan Kirim ke WhatsApp, Bagikan PDF, Tarik pengajuan, Unduh PDF', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({ status: 200, body: request() }),
    })
    renderAt('/izin-saya/7')
    expect(await screen.findByRole('button', { name: 'Kirim ke WhatsApp' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Bagikan PDF' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tarik pengajuan' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Unduh PDF/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Hapus' })).not.toBeInTheDocument()
  })

  it('karyawan: Kirim ke WhatsApp membuka wa.me dengan pesan permohonan', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null)
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({ status: 200, body: request() }),
    })
    renderAt('/izin-saya/7')
    fireEvent.click(await screen.findByRole('button', { name: 'Kirim ke WhatsApp' }))
    const [url, target] = open.mock.calls[0]
    expect(String(url).startsWith('https://wa.me/?text=')).toBe(true)
    expect(decodeURIComponent(String(url))).toContain(`${window.location.origin}/izin/7`)
    expect(target).toBe('_blank')
    open.mockRestore()
  })

  it('karyawan: draft menampilkan Ubah, Hapus, Kirim; Hapus meminta konfirmasi', async () => {
    let deleted = false
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({ status: 200, body: request({ status: 'DRAFT', submittedAt: null, submittedBy: null, nextStage: null }) }),
      'GET /api/me/leave-requests': () => ({ status: 200, body: [] }),
      'DELETE /api/me/leave-requests/7': () => {
        deleted = true
        return { status: 204 }
      },
    })
    document.cookie = 'XSRF-TOKEN=abc'
    renderAt('/izin-saya/7')
    expect(await screen.findByRole('link', { name: 'Ubah' })).toHaveAttribute('href', '/izin-saya/7/ubah')
    expect(screen.getByRole('button', { name: 'Kirim' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tarik pengajuan' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'Hapus' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(deleted).toBe(false)
    fireEvent.click(within(dialog).getByRole('button', { name: 'Ya, hapus' }))
    await waitFor(() => expect(deleted).toBe(true))
    expect(await screen.findByText('Belum ada pengajuan berjalan')).toBeInTheDocument()
  })

  it('karyawan: Kirim dari detail draft menampilkan pesan validasi server', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({ status: 200, body: request({ status: 'DRAFT', submittedAt: null, submittedBy: null, nextStage: null }) }),
      'POST /api/me/leave-requests/7/submit': () => ({ status: 400, body: { detail: 'Alamat selama cuti wajib diisi' } }),
    })
    document.cookie = 'XSRF-TOKEN=abc'
    renderAt('/izin-saya/7')
    fireEvent.click(await screen.findByRole('button', { name: 'Kirim' }))
    expect(await screen.findByText('Alamat selama cuti wajib diisi')).toBeInTheDocument()
  })

  it('karyawan: pengajuan disetujui hanya punya Unduh PDF tanpa Tarik pengajuan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({ status: 200, body: request({ status: 'DISETUJUI', nextStage: null }) }),
    })
    renderAt('/izin-saya/7')
    expect(await screen.findByRole('button', { name: /Unduh PDF/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Tarik pengajuan' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Kirim ke WhatsApp' })).not.toBeInTheDocument()
  })

  it('HR melihat peringatan wajib surat dan bisa menyetujui dengan catatan', async () => {
    let current = request()
    const usage: LeaveUsage = { employeeId: 12, year: 2026, types: [{ leaveTypeId: 1, leaveTypeName: 'Sakit', requests: 1, calendarDays: 3, workingDays: 3 }], pendingRequests: 0 }
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/leave-requests/7': () => ({ status: 200, body: current }),
      'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: usage }),
      'POST /api/leave-requests/7/approve': () => {
        current = request({ status: 'DISETUJUI', nextStage: null, canDecide: false, decidedBy: 'hr', decidedAt: '2026-10-05T02:00:00Z', decisionNote: 'Cepat sembuh' })
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

  it('rute surat lama diarahkan ke halaman detail', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({ status: 200, body: request() }),
    })
    renderAt('/izin-saya/7/surat')
    expect(await screen.findByText('Sakit · Budi Santoso')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Unduh PDF/ })).toBeInTheDocument()
  })

  it('HR melihat Catat keputusan Owner tanpa tombol Setujui saat menunggu Owner', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/leave-requests/7': () => ({
        status: 200,
        body: request({ status: 'PERLU_TINJAU_OWNER', nextStage: 'OWNER', requiresOwnerApproval: true, canDecide: false, canRecordOwnerDecision: true }),
      }),
      'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: { employeeId: 12, year: 2026, types: [], pendingRequests: 0 } }),
    })
    renderAt('/izin/7')
    expect(await screen.findByRole('button', { name: 'Catat keputusan Owner' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Setujui' })).not.toBeInTheDocument()
  })

  it('Owner melihat Setujui saat menunggu Owner, dan tidak melihat Catat keputusan Owner', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/leave-requests/7': () => ({
        status: 200,
        body: request({ status: 'PERLU_TINJAU_OWNER', nextStage: 'OWNER', requiresOwnerApproval: true }),
      }),
      'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: { employeeId: 12, year: 2026, types: [], pendingRequests: 0 } }),
    })
    renderAt('/izin/7')
    expect(await screen.findByRole('button', { name: 'Setujui' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Catat keputusan Owner' })).not.toBeInTheDocument()
  })

  it('HR tidak melihat Setujui pada tahap Owner dan Owner melihatnya pada jenis satu persetujuan di tahap HR', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/leave-requests/7': () => ({ status: 200, body: request() }),
      'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: { employeeId: 12, year: 2026, types: [], pendingRequests: 0 } }),
    })
    renderAt('/izin/7')
    expect(await screen.findByRole('button', { name: 'Setujui' })).toBeInTheDocument()
  })

it('tombol keputusan mengikuti flag server, bukan peran: Owner melihat Setujui di tahap HR bila HR tunggal', async () => {
  const usage = { 'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: { employeeId: 12, year: 2026, types: [], pendingRequests: 0 } }) }
  mockFetch({
    'GET /api/auth/me': () => ({ status: 200, body: owner }),
    'GET /api/leave-requests/7': () => ({ status: 200, body: request({ requiresOwnerApproval: true, canDecide: true }) }),
    ...usage,
  })
  renderAt('/izin/7')
  expect(await screen.findByRole('button', { name: 'Setujui' })).toBeInTheDocument()
})

it('Owner tidak melihat Setujui di tahap HR bila server menyatakan canDecide false', async () => {
  mockFetch({
    'GET /api/auth/me': () => ({ status: 200, body: owner }),
    'GET /api/leave-requests/7': () => ({ status: 200, body: request({ requiresOwnerApproval: true, canDecide: false }) }),
    'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: { employeeId: 12, year: 2026, types: [], pendingRequests: 0 } }),
  })
  renderAt('/izin/7')
  expect(await screen.findByText('Sakit · Budi Santoso')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Setujui' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Catat keputusan Owner' })).not.toBeInTheDocument()
})

it('form Jenis Izin selalu mengirim requiresOwnerApproval dan letterTemplate saat mengubah', async () => {
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/leave-types': () => ({ status: 200, body: [sakit] }),
      'PUT /api/leave-types/1': () => ({ status: 200, body: sakit }),
    })
    document.cookie = 'XSRF-TOKEN=abc'
    renderAt('/jenis-izin')
    fireEvent.click((await screen.findAllByRole('button', { name: /Ubah/ }))[0])
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('Butuh persetujuan Owner')).toBeChecked()
    expect(within(dialog).getByLabelText('Templat surat')).toHaveValue('SAKIT')
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan' }))
    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => url === '/api/leave-types/1' && init?.method === 'PUT')).toBe(true))
    const put = fetch.mock.calls.find(([url, init]) => url === '/api/leave-types/1' && init?.method === 'PUT')!
    const body = JSON.parse(String(put[1]!.body))
    expect(body.requiresOwnerApproval).toBe(true)
    expect(body.letterTemplate).toBe('SAKIT')
  })

  it('lonceng notifikasi menampilkan jumlah dan daftar pengajuan yang menunggu', async () => {
    const pending: PendingLeaveSummary = {
      count: 2,
      items: [
        { id: 7, employeeId: 12, employeeName: 'Budi Santoso', branchName: 'Pusat', leaveTypeName: 'Sakit', startDate: '2026-10-05', endDate: '2026-10-05', submittedAt: '2026-10-05T01:00:00Z', status: 'PERLU_TINJAU_HR' },
        { id: 8, employeeId: 13, employeeName: 'Siti Aminah', branchName: 'Pusat', leaveTypeName: 'Cuti', startDate: '2026-10-07', endDate: '2026-10-09', submittedAt: '2026-10-05T02:00:00Z', status: 'PERLU_TINJAU_OWNER' },
      ],
      recordedForOwner: [],
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/leave-requests/pending': () => ({ status: 200, body: pending }),
      'GET /api/leave-requests?status=PERLU_TINJAU_HR': () => ({ status: 200, body: [request()] }),
    })
    renderAt('/izin')

    const bell = await screen.findByRole('button', { name: '2 pengajuan izin menunggu persetujuan' })
    fireEvent.click(bell)
    expect(await screen.findByText('Siti Aminah')).toBeInTheDocument()
    expect(screen.getByText('Siti Aminah').closest('a')).toHaveAttribute('href', '/izin/8')
  })

  it('lonceng Owner menampilkan keputusan yang dicatat HR dan badge hanya menghitung yang belum dilihat', async () => {
    window.localStorage.removeItem('absensi.recordedSeen.owner')
    const pending: PendingLeaveSummary = {
      count: 1,
      items: [
        { id: 8, employeeId: 13, employeeName: 'Siti Aminah', branchName: 'Pusat', leaveTypeName: 'Cuti', startDate: '2026-10-07', endDate: '2026-10-09', submittedAt: '2026-10-05T02:00:00Z', status: 'PERLU_TINJAU_OWNER' },
      ],
      recordedForOwner: [
        { approvalId: 31, requestId: 7, employeeName: 'Budi Santoso', leaveTypeName: 'Sakit', approved: true, method: 'WHATSAPP', recorderName: 'Sari', recordedAt: '2026-10-05T03:00:00Z', status: 'DISETUJUI' },
        { approvalId: 32, requestId: 9, employeeName: 'Dewi', leaveTypeName: 'Cuti', approved: false, method: 'LISAN', recorderName: 'Sari', recordedAt: '2026-10-05T04:00:00Z', status: 'DITOLAK' },
      ],
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/leave-requests/pending': () => ({ status: 200, body: pending }),
      'GET /api/leave-requests?': () => ({ status: 200, body: [] }),
    })
    renderAt('/izin')

    fireEvent.click(await screen.findByRole('button', { name: '3 notifikasi izin' }))
    expect(await screen.findByText('Keputusan Anda yang dicatat HR')).toBeInTheDocument()
    expect(screen.getByText('Sari mencatat persetujuan Anda (WhatsApp)').closest('a')).toHaveAttribute('href', '/izin/7')
    expect(screen.getByText('Sari mencatat penolakan Anda (Lisan)').closest('a')).toHaveAttribute('href', '/izin/9')
    const recordedLink = screen.getByText('Sari mencatat persetujuan Anda (WhatsApp)').closest('a')!
    expect(within(recordedLink).getByText('Disetujui')).toBeInTheDocument()
    const rejectedLink = screen.getByText('Sari mencatat penolakan Anda (Lisan)').closest('a')!
    expect(within(rejectedLink).getByText('Ditolak')).toBeInTheDocument()
    expect(JSON.parse(window.localStorage.getItem('absensi.recordedSeen.owner') ?? '[]')).toEqual([31, 32])

    fireEvent.keyDown(document.activeElement ?? document.body, { key: 'Escape' })
    expect(await screen.findByRole('button', { name: '1 pengajuan izin menunggu persetujuan' })).toBeInTheDocument()
    window.localStorage.removeItem('absensi.recordedSeen.owner')
  })

  it('unduh bukti keputusan memakai ekstensi dari tipe berkas', async () => {
    const approval = {
      stage: 'OWNER' as const, approved: true, decidedBy: 'owner', decidedByName: 'Owner', decidedAt: '2026-10-05T03:00:00Z',
      method: 'WHATSAPP' as const, recordedBy: 'hr', recordedByName: 'Sari HR', recordedAt: '2026-10-05T03:05:00Z', note: null, hasEvidence: true,
    }
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/leave-requests/7': () => ({ status: 200, body: request({ status: 'DISETUJUI', nextStage: null, approvals: [approval] }) }),
      'GET /api/leave-requests/usage?employeeId=12&year=2026': () => ({ status: 200, body: { employeeId: 12, year: 2026, types: [], pendingRequests: 0 } }),
      'GET /api/leave-requests/7/approvals/OWNER/evidence': () => ({ status: 200, body: new Blob(['x'], { type: 'image/png' }) }),
    })
    URL.createObjectURL = vi.fn(() => 'blob:bukti')
    URL.revokeObjectURL = vi.fn()
    const names: string[] = []
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      names.push(this.download)
    })
    renderAt('/izin/7')
    fireEvent.click(await screen.findByRole('button', { name: /Unduh bukti tahap Owner/ }))
    await waitFor(() => expect(names).toEqual(['bukti-IZ-000007-owner.png']))
    click.mockRestore()
  })

  it('Owner secara bawaan melihat semua pengajuan yang menunggu, termasuk satu persetujuan di tahap HR', async () => {
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
      'GET /api/leave-requests/pending': () => ({ status: 200, body: { count: 0, items: [], recordedForOwner: [] } }),
      'GET /api/leave-requests?': () => ({
        status: 200,
        body: [
          request({ id: 1, employeeName: 'Satu Hr', status: 'PERLU_TINJAU_HR' }),
          request({ id: 2, employeeName: 'Dua Owner', status: 'PERLU_TINJAU_OWNER' }),
          request({ id: 3, employeeName: 'Tiga Setuju', status: 'DISETUJUI' }),
        ],
      }),
    })
    renderAt('/izin')
    expect((await screen.findAllByText('Satu Hr')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Dua Owner').length).toBeGreaterThan(0)
    expect(screen.queryByText('Tiga Setuju')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Semua menunggu' })).toHaveAttribute('aria-pressed', 'true')
    expect(fetch.mock.calls.some(([url]) => String(url).includes('status='))).toBe(false)
  })

  it('draft tanpa tanggal menampilkan Tanggal belum diisi', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests/7': () => ({
        status: 200,
        body: request({ status: 'DRAFT', startDate: null, endDate: null, calendarDays: 0, workingDays: null, submittedAt: null, submittedBy: null, nextStage: null }),
      }),
    })
    renderAt('/izin-saya/7')
    expect((await screen.findAllByText('Tanggal belum diisi')).length).toBeGreaterThan(0)
    expect(screen.queryByText(/0 hari/)).not.toBeInTheDocument()
  })
})
