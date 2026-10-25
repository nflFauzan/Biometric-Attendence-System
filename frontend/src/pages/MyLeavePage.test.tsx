import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import { sampleRequest } from '@/components/leave/fixtures'
import type { Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

const employee: Me = { username: 'budi', role: 'EMPLOYEE', employeeId: 12, employeeName: 'Budi Santoso', mustChangePassword: false }

function renderList(path = '/izin-saya') {
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

const draft = sampleRequest({ id: 1, status: 'DRAFT', leaveTypeName: 'Cuti', submittedAt: null, nextStage: null })
const pending = sampleRequest({ id: 2, status: 'PERLU_TINJAU_HR', leaveTypeName: 'Izin', nextStage: 'HR' })
const done = sampleRequest({ id: 3, status: 'DISETUJUI', leaveTypeName: 'Sakit', nextStage: null })

const rows = () =>
  screen
    .queryAllByRole('link')
    .map((a) => a.getAttribute('href'))
    .filter((h): h is string => !!h && /^\/izin-saya\/\d+$/.test(h))

describe('MyLeavePage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('tab Draft hanya berisi DRAFT, Berjalan hanya yang menunggu, Selesai sisanya', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests': () => ({ status: 200, body: [draft, pending, done] }),
    })
    renderList()

    expect(await screen.findByRole('tab', { name: 'Berjalan' })).toHaveAttribute('aria-selected', 'true')
    await screen.findByText(/Izin ·/)
    expect(rows()).toEqual(['/izin-saya/2'])

    fireEvent.click(screen.getByRole('tab', { name: 'Draft' }))
    expect(rows()).toEqual(['/izin-saya/1'])

    fireEvent.click(screen.getByRole('tab', { name: 'Selesai' }))
    expect(rows()).toEqual(['/izin-saya/3'])
  })

  it('tab kosong menampilkan pesan kosong per tab', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests': () => ({ status: 200, body: [pending] }),
    })
    renderList()
    await screen.findByText(/Izin ·/)
    fireEvent.click(screen.getByRole('tab', { name: 'Draft' }))
    expect(screen.getByText('Belum ada draft')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('tab', { name: 'Selesai' }))
    expect(screen.getByText('Belum ada pengajuan selesai')).toBeInTheDocument()
  })

  it('tab Berjalan kosong dan tombol Ajukan izin mengarah ke wizard', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: employee }),
      'GET /api/me/leave-requests': () => ({ status: 200, body: [] }),
    })
    renderList()
    expect(await screen.findByText('Belum ada pengajuan berjalan')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ajukan izin' })).toHaveAttribute('href', '/izin-saya/baru')
  })

  describe('Ajukan ulang (K-126)', () => {
    const rejected = sampleRequest({ id: 7, status: 'DITOLAK', leaveTypeName: 'Izin', nextStage: null })
    const resubmitted = sampleRequest({ id: 9, status: 'DRAFT', submittedAt: null, nextStage: null, resubmittedFromId: 7 })

    it('pengajuan ditolak bisa diajukan ulang lalu membuka wizard draft baru', async () => {
      const fetch = mockFetch({
        'GET /api/auth/me': () => ({ status: 200, body: employee }),
        'GET /api/me/leave-requests/7': () => ({ status: 200, body: rejected }),
        'POST /api/me/leave-requests/7/resubmit': () => ({ status: 201, body: resubmitted }),
        'GET /api/me/leave-requests/9': () => ({ status: 200, body: resubmitted }),
        'GET /api/leave-types': () => ({ status: 200, body: [] }),
      })
      renderList('/izin-saya/7')

      fireEvent.click(await screen.findByRole('button', { name: 'Ajukan ulang' }))

      await waitFor(() =>
        expect(fetch.mock.calls.some(([url, init]) => url === '/api/me/leave-requests/7/resubmit' && init?.method === 'POST')).toBe(true),
      )
      await waitFor(() => expect(fetch.mock.calls.some(([url]) => url === '/api/me/leave-requests/9')).toBe(true))
    })

    it('status selain Ditolak tidak menampilkan tombol Ajukan ulang', async () => {
      mockFetch({
        'GET /api/auth/me': () => ({ status: 200, body: employee }),
        'GET /api/me/leave-requests/3': () => ({ status: 200, body: done }),
      })
      renderList('/izin-saya/3')
      await screen.findByText('Rincian pengajuan')
      expect(screen.queryByRole('button', { name: 'Ajukan ulang' })).not.toBeInTheDocument()
    })

    it('draft hasil ajukan ulang menautkan pengajuan sebelumnya', async () => {
      mockFetch({
        'GET /api/auth/me': () => ({ status: 200, body: employee }),
        'GET /api/me/leave-requests/9': () => ({ status: 200, body: resubmitted }),
      })
      renderList('/izin-saya/9')
      expect(await screen.findByRole('link', { name: 'IZ-000007' })).toHaveAttribute('href', '/izin-saya/7')
    })
  })
})
