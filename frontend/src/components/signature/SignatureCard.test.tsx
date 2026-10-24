import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { SignatureCard } from '@/components/signature/SignatureCard'
import { mockFetch } from '@/test/fetchMock'

function renderCard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <SignatureCard />
    </QueryClientProvider>,
  )
}

describe('SignatureCard', () => {
  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    document.cookie = 'XSRF-TOKEN=abc'
  })
  afterEach(() => vi.unstubAllGlobals())

  it('404 pada status menampilkan pesan ramah, bukan galat mentah', async () => {
    mockFetch({ 'GET /api/me/signature/status': () => ({ status: 404, body: { detail: 'Not found' } }) })
    renderCard()
    expect(await screen.findByText(/belum terhubung ke data pengguna/)).toBeInTheDocument()
    expect(screen.queryByText('Not found')).not.toBeInTheDocument()
  })

  it('gagal memuat gambar pratinjau menampilkan galat', async () => {
    mockFetch({
      'GET /api/me/signature/status': () => ({ status: 200, body: { exists: true, updatedAt: '2026-10-20T01:00:00Z' } }),
      'GET /api/me/signature': () => ({ status: 500, body: { detail: 'Gagal membaca tanda tangan' } }),
    })
    renderCard()
    expect(await screen.findByText('Gagal membaca tanda tangan')).toBeInTheDocument()
  })

  it('Hapus meminta konfirmasi lewat dialog dan baru menghapus setelah dikonfirmasi', async () => {
    const confirm = vi.spyOn(window, 'confirm')
    let exists = true
    const fetch = mockFetch({
      'GET /api/me/signature/status': () => ({ status: 200, body: { exists, updatedAt: exists ? '2026-10-20T01:00:00Z' : null } }),
      'GET /api/me/signature': () => ({ status: 200, body: {} }),
      'DELETE /api/me/signature': () => {
        exists = false
        return { status: 204 }
      },
    })
    const deleted = () => fetch.mock.calls.some(([, init]) => init?.method === 'DELETE')
    renderCard()
    fireEvent.click(await screen.findByRole('button', { name: 'Hapus' }))
    const dialog = await screen.findByRole('alertdialog')
    expect(deleted()).toBe(false)

    fireEvent.click(within(dialog).getByRole('button', { name: 'Batal' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument())
    expect(deleted()).toBe(false)

    fireEvent.click(screen.getByRole('button', { name: 'Hapus' }))
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Ya, hapus' }))
    await waitFor(() => expect(deleted()).toBe(true))
    expect(await screen.findByText('Belum ada tanda tangan.')).toBeInTheDocument()
    expect(confirm).not.toHaveBeenCalled()
  })
})
