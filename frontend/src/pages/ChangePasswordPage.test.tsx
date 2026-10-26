import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '@/auth/AuthContext'
import { ChangePasswordPage } from '@/pages/ChangePasswordPage'
import { mockFetch } from '@/test/fetchMock'

function Probe() {
  const l = useLocation()
  return <span data-testid="loc">{l.pathname + l.search}</span>
}

function renderPage(initial: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initial]}>
        <AuthProvider>
          <Routes>
            <Route path="/ganti-password" element={<ChangePasswordPage />} />
            <Route path="*" element={<Probe />} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function setup() {
  let changed = false
  mockFetch({
    'GET /api/auth/me': () => ({
      status: 200,
      body: { username: 'u', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: !changed },
    }),
    'GET /api/auth/csrf': () => ({ status: 204 }),
    'POST /api/auth/change-password': () => {
      changed = true
      return { status: 204 }
    },
  })
}

async function submit() {
  fireEvent.change(await screen.findByLabelText('Password lama'), { target: { value: 'lama-12345' } })
  fireEvent.change(screen.getByLabelText(/Password baru/), { target: { value: 'baru-12345' } })
  fireEvent.change(screen.getByLabelText('Ulangi password baru'), { target: { value: 'baru-12345' } })
  fireEvent.click(screen.getByRole('button', { name: 'Simpan' }))
}

describe('ChangePasswordPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('mendarat di next yang aman setelah berhasil ganti password', async () => {
    setup()
    renderPage('/ganti-password?next=' + encodeURIComponent('/izin/5?tab=a'))
    await submit()
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent('/izin/5?tab=a'))
  })

  it('mengabaikan next yang tidak aman dan memakai beranda peran', async () => {
    setup()
    renderPage('/ganti-password?next=' + encodeURIComponent('//evil.com'))
    await submit()
    await waitFor(() => expect(screen.getByTestId('loc')).toHaveTextContent(/^\/$/))
  })
})
