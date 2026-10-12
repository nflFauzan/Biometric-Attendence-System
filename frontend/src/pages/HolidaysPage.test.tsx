import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { Holiday, Me } from '@/lib/types'
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

describe('HolidaysPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menampilkan libur per tahun dan menghapus setelah konfirmasi', async () => {
    const year = new Date().getFullYear()
    let holidays: Holiday[] = [{ id: 7, date: `${year}-12-25`, name: 'Hari Natal' }]
    const fetchMock = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: hr }),
      [`GET /api/holidays?year=${year}`]: () => ({ status: 200, body: holidays }),
      [`GET /api/holidays?year=${year + 1}`]: () => ({ status: 200, body: [] }),
      'GET /api/auth/csrf': () => ({ status: 204 }),
      'DELETE /api/holidays/7': () => {
        holidays = []
        return { status: 204 }
      },
    })
    renderAt('/kalender-libur')

    expect((await screen.findAllByText('Hari Natal', undefined, LOAD)).length).toBeGreaterThan(0)

    fireEvent.click(screen.getAllByRole('button', { name: `Hapus hari libur 25 Des ${year}` })[0])
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
    fireEvent.click(screen.getAllByRole('button', { name: 'Ya, hapus' })[0])

    await waitFor(() => expect(screen.queryAllByText('Hari Natal')).toHaveLength(0))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)

    fireEvent.click(screen.getByRole('button', { name: 'Tahun berikutnya' }))
    expect(await screen.findByText(String(year + 1))).toBeInTheDocument()
  })
})
