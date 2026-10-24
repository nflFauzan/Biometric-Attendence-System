import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { PublicLetter } from '@/lib/types'
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

const letter: PublicLetter = {
  letterNumber: 'IZ-000123',
  employeeName: 'Budi Santoso',
  branchName: 'Pusat',
  leaveTypeName: 'Cuti',
  startDate: '2026-11-03',
  endDate: '2026-11-05',
  status: 'DISETUJUI',
  approvals: [
    { stage: 'HR', approved: true, decidedByName: 'Sari Dewi', decidedAt: '2026-10-24T02:00:00Z', method: 'WEB' },
    { stage: 'OWNER', approved: true, decidedByName: 'Owner', decidedAt: '2026-10-24T05:00:00Z', method: 'WHATSAPP' },
  ],
}

describe('VerifyLetterPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menampilkan surat disetujui tanpa sesi login dan tanpa kerangka aplikasi', async () => {
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 401, body: { detail: 'Belum login' } }),
      'GET /api/public/letters/abc123': () => ({ status: 200, body: letter }),
    })
    renderAt('/verifikasi/abc123')

    expect(await screen.findByText('IZ-000123')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Verifikasi Surat' })).toBeInTheDocument()
    expect(screen.getByText('Budi Santoso')).toBeInTheDocument()
    expect(screen.getByText('Pusat')).toBeInTheDocument()
    expect(screen.getByText('Disetujui', { selector: '[data-slot=badge]' })).toBeInTheDocument()
    const list = screen.getByRole('list', { name: 'Persetujuan' })
    expect(within(list).getAllByRole('listitem')).toHaveLength(2)
    expect(within(list).getByText(/Sari Dewi/)).toBeInTheDocument()
    expect(within(list).getByText(/Owner · Disetujui/)).toBeInTheDocument()
    // Tidak dialihkan ke login dan tidak memakai navigasi aplikasi.
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    expect(fetch.mock.calls.some(([url]) => url === '/api/public/letters/abc123')).toBe(true)
  })

  it('404 menampilkan surat tidak ditemukan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 401, body: {} }),
      'GET /api/public/letters/salah': () => ({ status: 404, body: { detail: 'Surat tidak ditemukan' } }),
    })
    renderAt('/verifikasi/salah')
    expect(await screen.findByText('Surat tidak ditemukan. Periksa kembali kode QR atau link.')).toBeInTheDocument()
  })

  it('429 menampilkan pesan terlalu banyak permintaan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 401, body: {} }),
      'GET /api/public/letters/x': () => ({ status: 429, body: { detail: 'Terlalu banyak permintaan, coba lagi sebentar' } }),
    })
    renderAt('/verifikasi/x')
    expect(await screen.findByText('Terlalu banyak permintaan, coba lagi sebentar.')).toBeInTheDocument()
  })

  it('401 dari endpoint tidak mengalihkan ke login', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 401, body: {} }),
      'GET /api/public/letters/y': () => ({ status: 401, body: { detail: 'x' } }),
    })
    renderAt('/verifikasi/y')
    expect(await screen.findByRole('heading', { name: 'Verifikasi Surat' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Masuk' })).not.toBeInTheDocument()
  })
})
