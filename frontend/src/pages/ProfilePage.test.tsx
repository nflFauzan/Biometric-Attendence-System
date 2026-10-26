import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Me } from '@/lib/types'
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

const owner: Me = { username: 'owner', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: false }
const hr: Me = { username: 'sari', role: 'HR_FINANCE', employeeId: 3, employeeName: 'Sari Wulandari', mustChangePassword: false }

const sari = {
  id: 3, employeeNumber: 'K-003', fullName: 'Sari Wulandari', branchName: 'Pusat', categoryName: 'Staf',
  position: 'Finance & Accounting', phone: null, joinDate: '2024-01-02',
}

const noSignature = () => ({ status: 200, body: { exists: false, updatedAt: null } })
const noPending = () => ({ status: 200, body: { count: 0, items: [] } })

describe('Profil Owner dan HR (spesimen tanda tangan K-115)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('Owner tanpa data karyawan tetap bisa mengatur tanda tangan', async () => {
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'GET /api/leave-requests/pending': noPending,
      'GET /api/me/signature/status': noSignature,
    })
    renderAt('/profil')

    expect(await screen.findByText('Akun ini tidak terhubung ke data karyawan.')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Gambar' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Unggah gambar' })).toBeInTheDocument()
    // Akun tanpa karyawan tidak meminta data karyawan (pasti 404).
    expect(fetch.mock.calls.some(([url]) => String(url) === '/api/me/employee')).toBe(false)
  })

  it('HR yang terhubung ke karyawan melihat data dirinya dan tanda tangan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      'GET /api/leave-requests/pending': noPending,
      'GET /api/me/employee': () => ({ status: 200, body: sari }),
      'GET /api/me/signature/status': noSignature,
    })
    renderAt('/profil')

    expect(await screen.findByText('K-003')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Gambar' })).toBeInTheDocument()
  })
})
