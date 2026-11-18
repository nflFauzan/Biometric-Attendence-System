import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

function LocationProbe() {
  const l = useLocation()
  return <span data-testid="loc">{l.pathname + l.search}</span>
}

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
          <LocationProbe />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const me = (role: Me['role']): Me => ({ username: 'u', role, employeeId: null, employeeName: null, mustChangePassword: false })

describe('Halaman Pengaturan (K-145)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('Owner melihat 9 kartu dalam 3 kelompok', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }) })
    renderAt('/pengaturan')
    expect(await screen.findByRole('heading', { level: 1, name: 'Pengaturan' })).toBeInTheDocument()
    for (const group of ['Perusahaan', 'Aturan & gaji', 'Akses & keamanan']) {
      expect(screen.getByRole('heading', { level: 2, name: group })).toBeInTheDocument()
    }
    const main = screen.getByRole('main')
    expect(within(main).getAllByRole('link')).toHaveLength(9)
    expect(within(main).getByRole('link', { name: /Audit Log/ })).toHaveAttribute('href', '/pengaturan/audit-log')
    expect(within(main).getByRole('link', { name: /Simulasi Perhitungan/ })).toHaveAttribute('href', '/pengaturan/simulasi')
  })

  it('Finance melihat 7 kartu, tanpa Jenis Izin/Cuti dan Audit Log, dan Aturan Kerja ditandai hanya lihat', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 200, body: me('HR_FINANCE') }) })
    renderAt('/pengaturan')
    await screen.findByRole('heading', { level: 1, name: 'Pengaturan' })
    const main = screen.getByRole('main')
    expect(within(main).getAllByRole('link')).toHaveLength(7)
    expect(within(main).queryByRole('link', { name: /Jenis Izin/ })).not.toBeInTheDocument()
    expect(within(main).queryByRole('link', { name: /Audit Log/ })).not.toBeInTheDocument()
    expect(within(main).getByRole('link', { name: /Aturan Kerja/ })).toHaveTextContent('Hanya lihat')
  })

  it('Finance yang membuka /pengaturan/audit-log langsung dialihkan ke beranda', async () => {
    mockFetch({ 'GET /api/auth/me': () => ({ status: 200, body: me('HR_FINANCE') }) })
    renderAt('/pengaturan/audit-log')
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(/^\/$/))
  })

  it('header sub-halaman menampilkan jejak Pengaturan / Cabang', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: me('OWNER') }),
      'GET /api/branches': () => ({ status: 200, body: [] }),
    })
    renderAt('/pengaturan/cabang')
    const header = await screen.findByRole('banner')
    expect(within(header).getByText('Pengaturan')).toBeInTheDocument()
    expect(within(header).getByText('Cabang')).toBeInTheDocument()
  })
})
