import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { localDateIso } from '@/lib/format'
import type { Me, OvertimeRow } from '@/lib/types'
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

const hr: Me = { username: 'hr', role: 'HR_FINANCE', employeeId: null, employeeName: null, mustChangePassword: false }

const row = (overrides: Partial<OvertimeRow> = {}): OvertimeRow => ({
  employeeId: 12,
  fullName: 'Budi Santoso',
  branchName: 'Pusat',
  categoryName: 'Supir',
  date: '2026-10-05',
  checkOut: '19:00:00',
  candidateSeconds: 7200,
  approvedSeconds: null,
  note: null,
  validatedByName: null,
  validatedAt: null,
  status: 'BELUM_DIVALIDASI',
  ...overrides,
})

describe('Halaman Validasi Lembur (FR-REV-003)', () => {
  afterEach(() => vi.unstubAllGlobals())

  const to = localDateIso()
  const from = `${to.slice(0, 8)}01`
  const base = `/api/overtime?from=${from}&to=${to}`
  const common = {
    'GET /api/auth/me': () => ({ status: 200, body: hr }),
    'GET /api/branches': () => ({ status: 200, body: [] }),
    'GET /api/employee-categories': () => ({ status: 200, body: [] }),
  }

  it('menampilkan kandidat dalam jam-menit dan mengisi menit disetujui dengan kandidat', async () => {
    mockFetch({ ...common, [`GET ${base}`]: () => ({ status: 200, body: [row({ candidateSeconds: 5400 })] }) })
    renderAt('/absensi/lembur')

    expect(await screen.findByText('Budi Santoso')).toBeInTheDocument()
    expect(screen.getByText('1 jam 30 menit')).toBeInTheDocument()
    expect(screen.getByText('Belum divalidasi')).toBeInTheDocument()
    expect(screen.getByLabelText('Menit disetujui Budi Santoso 2026-10-05')).toHaveValue(90)
  })

  it('mencentang dua baris lalu Validasi terpilih mengirim satu POST berisi 2 item', async () => {
    const fetchFn = mockFetch({
      ...common,
      [`GET ${base}`]: () => ({
        status: 200,
        body: [row(), row({ employeeId: 13, fullName: 'Sari Dewi', candidateSeconds: 3600 })],
      }),
      'POST /api/overtime/validations': () => ({ status: 200, body: [] }),
    })
    renderAt('/absensi/lembur')

    await screen.findByText('Sari Dewi')
    fireEvent.change(screen.getByLabelText('Menit disetujui Sari Dewi 2026-10-05'), { target: { value: '30' } })
    fireEvent.change(screen.getByLabelText('Catatan Sari Dewi 2026-10-05'), { target: { value: 'Setengah jam saja' } })
    fireEvent.click(screen.getByLabelText('Pilih lembur Budi Santoso 2026-10-05'))
    fireEvent.click(screen.getByLabelText('Pilih lembur Sari Dewi 2026-10-05'))
    fireEvent.click(screen.getByRole('button', { name: 'Validasi terpilih' }))

    await waitFor(() =>
      expect(fetchFn.mock.calls.filter(([url]) => url === '/api/overtime/validations')).toHaveLength(1),
    )
    const [, init] = fetchFn.mock.calls.find(([url]) => url === '/api/overtime/validations')!
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      items: [
        { employeeId: 12, workDate: '2026-10-05', approvedMinutes: 120, expectedCandidateSeconds: 7200, note: '' },
        {
          employeeId: 13,
          workDate: '2026-10-05',
          approvedMinutes: 30,
          expectedCandidateSeconds: 3600,
          note: 'Setengah jam saja',
        },
      ],
    })
  })

  it('kandidat dengan sisa detik: isian menit = menit penuh kandidat (setuju seluruhnya)', async () => {
    mockFetch({ ...common, [`GET ${base}`]: () => ({ status: 200, body: [row({ candidateSeconds: 7237 })] }) })
    renderAt('/absensi/lembur')

    await screen.findByText('Budi Santoso')
    expect(screen.getByLabelText('Menit disetujui Budi Santoso 2026-10-05')).toHaveValue(120)
  })

  it('menit bukan bilangan bulat tidak dikirim dan menampilkan pesan', async () => {
    const fetchFn = mockFetch({
      ...common,
      [`GET ${base}`]: () => ({ status: 200, body: [row()] }),
      'POST /api/overtime/validations': () => ({ status: 200, body: [] }),
    })
    renderAt('/absensi/lembur')

    await screen.findByText('Budi Santoso')
    fireEvent.change(screen.getByLabelText('Menit disetujui Budi Santoso 2026-10-05'), { target: { value: '1.5' } })
    fireEvent.click(screen.getByLabelText('Pilih lembur Budi Santoso 2026-10-05'))
    fireEvent.click(screen.getByRole('button', { name: 'Validasi terpilih' }))

    expect(await screen.findByText('Lembur disetujui harus bilangan bulat menit')).toBeInTheDocument()
    expect(fetchFn.mock.calls.filter(([url]) => url === '/api/overtime/validations')).toHaveLength(0)
  })

  it('menampilkan error 400 dari server', async () => {
    mockFetch({
      ...common,
      [`GET ${base}`]: () => ({ status: 200, body: [row()] }),
      'POST /api/overtime/validations': () => ({
        status: 400,
        body: { detail: 'Catatan wajib bila lembur dikurangi atau ditolak' },
      }),
    })
    renderAt('/absensi/lembur')

    await screen.findByText('Budi Santoso')
    fireEvent.change(screen.getByLabelText('Menit disetujui Budi Santoso 2026-10-05'), { target: { value: '60' } })
    fireEvent.click(screen.getByLabelText('Pilih lembur Budi Santoso 2026-10-05'))
    fireEvent.click(screen.getByRole('button', { name: 'Validasi terpilih' }))

    expect(await screen.findByText('Catatan wajib bila lembur dikurangi atau ditolak')).toBeInTheDocument()
  })

  it('status PERLU_VALIDASI_ULANG tampil sebagai badge "Perlu validasi ulang"', async () => {
    mockFetch({
      ...common,
      [`GET ${base}`]: () => ({
        status: 200,
        body: [
          row({
            candidateSeconds: 10800,
            approvedSeconds: 7200,
            validatedByName: 'Rina',
            validatedAt: '2026-10-06T03:00:00Z',
            status: 'PERLU_VALIDASI_ULANG',
          }),
        ],
      }),
    })
    renderAt('/absensi/lembur')

    expect(await screen.findByText('Perlu validasi ulang')).toBeInTheDocument()
    expect(screen.getByText('Rina')).toBeInTheDocument()
  })

  it('validasi 0 detik tampil sebagai "Ditolak", bukan hanya badge Tervalidasi', async () => {
    mockFetch({
      ...common,
      [`GET ${base}`]: () => ({
        status: 200,
        body: [
          row({ approvedSeconds: 0, note: 'Tidak diminta', validatedByName: 'Rina', status: 'TERVALIDASI' }),
          row({ employeeId: 13, fullName: 'Sari Dewi', approvedSeconds: 7200, status: 'TERVALIDASI' }),
        ],
      }),
    })
    renderAt('/absensi/lembur')

    await screen.findByText('Sari Dewi')
    expect(screen.getAllByText('Tervalidasi')).toHaveLength(2)
    expect(screen.getAllByText('Ditolak')).toHaveLength(1)
  })

  it('"Pilih semua" hanya mencentang yang belum tervalidasi atau perlu validasi ulang', async () => {
    mockFetch({
      ...common,
      [`GET ${base}`]: () => ({
        status: 200,
        body: [
          row(),
          row({ employeeId: 13, fullName: 'Sari Dewi', approvedSeconds: 7200, status: 'TERVALIDASI' }),
          row({ employeeId: 14, fullName: 'Andi', candidateSeconds: 3600, approvedSeconds: 7200, status: 'PERLU_VALIDASI_ULANG' }),
        ],
      }),
    })
    renderAt('/absensi/lembur')

    await screen.findByText('Sari Dewi')
    const all = screen.getByLabelText('Pilih semua lembur yang belum tervalidasi')
    fireEvent.click(all)
    expect(screen.getByLabelText('Pilih lembur Budi Santoso 2026-10-05')).toBeChecked()
    expect(screen.getByLabelText('Pilih lembur Andi 2026-10-05')).toBeChecked()
    expect(screen.getByLabelText('Pilih lembur Sari Dewi 2026-10-05')).not.toBeChecked()
    expect(all).toBeChecked()

    // Baris tervalidasi tetap bisa dicentang manual, dan tidak ikut terhapus saat "Pilih semua" dilepas.
    fireEvent.click(screen.getByLabelText('Pilih lembur Sari Dewi 2026-10-05'))
    fireEvent.click(all)
    expect(screen.getByLabelText('Pilih lembur Budi Santoso 2026-10-05')).not.toBeChecked()
    expect(screen.getByLabelText('Pilih lembur Andi 2026-10-05')).not.toBeChecked()
    expect(screen.getByLabelText('Pilih lembur Sari Dewi 2026-10-05')).toBeChecked()
  })

  it('kandidat di bawah 1 menit tampil "< 1 menit" dengan keterangan hanya bisa disetujui seluruhnya', async () => {
    mockFetch({ ...common, [`GET ${base}`]: () => ({ status: 200, body: [row({ candidateSeconds: 45 })] }) })
    renderAt('/absensi/lembur')

    await screen.findByText('Budi Santoso')
    expect(screen.getByText('< 1 menit')).toBeInTheDocument()
    expect(screen.getByText('Kurang dari 1 menit, hanya bisa disetujui seluruhnya.')).toBeInTheDocument()
    expect(screen.getByLabelText('Menit disetujui Budi Santoso 2026-10-05')).toHaveValue(0)
  })
})
