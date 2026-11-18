import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { formatCount, useFixCounts, type FixFilter } from '@/lib/fixCounts'
import { mockFetch } from '@/test/fetchMock'

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

const filter: FixFilter = { from: '2026-11-01', to: '2026-11-07' }
const range = 'from=2026-11-01&to=2026-11-07'
const overtime = (status: string) => ({ employeeId: 1, date: '2026-11-02', status })

function routes(overrides: Record<string, () => { status: number; body?: unknown }> = {}) {
  return {
    [`GET /api/attendance/review-queue?${range}`]: () => ({ status: 200, body: [{}, {}] }),
    [`GET /api/overtime?${range}`]: () => ({
      status: 200,
      body: [overtime('BELUM_DIVALIDASI'), overtime('BELUM_DIVALIDASI'), overtime('TERVALIDASI')],
    }),
    [`GET /api/attendance/held-scans?${range}&status=BELUM_DITINJAU`]: () => ({ status: 200, body: [{}] }),
    [`GET /api/attendance/unmatched-scans?${range}`]: () => ({ status: 200, body: [] }),
    ...overrides,
  }
}

describe('useFixCounts', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('menghitung hal yang belum beres per bagian dan totalnya', async () => {
    mockFetch(routes())
    const { result } = renderHook(() => useFixCounts(filter), { wrapper })
    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.reviewDays.count).toBe(2)
    expect(result.current.overtimePending.count).toBe(2)
    expect(result.current.heldPending.count).toBe(1)
    expect(result.current.unmatched.count).toBe(0)
    expect(result.current.total).toBe(5)
  })

  it('lembur yang perlu divalidasi ulang setelah koreksi ikut dihitung (K-140)', async () => {
    mockFetch(
      routes({
        [`GET /api/overtime?${range}`]: () => ({
          status: 200,
          body: [overtime('BELUM_DIVALIDASI'), overtime('PERLU_VALIDASI_ULANG'), overtime('TERVALIDASI')],
        }),
      }),
    )
    const { result } = renderHook(() => useFixCounts(filter), { wrapper })
    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.overtimePending.count).toBe(2)
  })

  it('scan tertahan memakai rentang sendiri bila diberikan (heldFrom)', async () => {
    const fetchFn = mockFetch(
      routes({
        [`GET /api/attendance/held-scans?from=2000-01-01&to=2026-11-07&status=BELUM_DITINJAU`]: () => ({ status: 200, body: [{}, {}] }),
      }),
    )
    const { result } = renderHook(() => useFixCounts({ ...filter, heldFrom: '2000-01-01' }), { wrapper })
    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.heldPending.count).toBe(2)
    expect(fetchFn.mock.calls.some(([url]) => url === `/api/attendance/held-scans?${range}&status=BELUM_DITINJAU`)).toBe(false)
  })

  it('scan tak dikenal yang mencapai batas server tampil sebagai 500+', async () => {
    mockFetch(
      routes({
        [`GET /api/attendance/unmatched-scans?${range}`]: () => ({ status: 200, body: Array.from({ length: 500 }, () => ({})) }),
      }),
    )
    const { result } = renderHook(() => useFixCounts(filter), { wrapper })
    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.unmatched.capped).toBe(true)
    expect(formatCount(result.current.unmatched)).toBe('500+')
    expect(formatCount(result.current.reviewDays)).toBe('2')
  })

  it('bagian yang gagal dimuat punya pesan error dan tidak ikut total', async () => {
    mockFetch(
      routes({
        [`GET /api/attendance/held-scans?${range}&status=BELUM_DITINJAU`]: () => ({ status: 500, body: { detail: 'Server error' } }),
      }),
    )
    const { result } = renderHook(() => useFixCounts(filter), { wrapper })
    await waitFor(() => expect(result.current.isPending).toBe(false))
    expect(result.current.heldPending.count).toBeNull()
    expect(result.current.heldPending.error).toBeTruthy()
    expect(result.current.total).toBe(4)
  })

  it('filter cabang dan kategori hanya diteruskan ke endpoint yang mendukungnya', async () => {
    const withBranch = `${range}&branchId=3&categoryId=2`
    const fetchMock = mockFetch({
      [`GET /api/attendance/review-queue?${withBranch}`]: () => ({ status: 200, body: [] }),
      [`GET /api/overtime?${withBranch}`]: () => ({ status: 200, body: [] }),
      [`GET /api/attendance/held-scans?${range}&status=BELUM_DITINJAU`]: () => ({ status: 200, body: [] }),
      [`GET /api/attendance/unmatched-scans?${range}`]: () => ({ status: 200, body: [] }),
    })
    const { result } = renderHook(() => useFixCounts({ ...filter, branchId: '3', categoryId: '2' }), { wrapper })
    await waitFor(() => expect(result.current.isPending).toBe(false))
    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    expect(urls).toContain(`/api/attendance/review-queue?${withBranch}`)
    expect(urls).toContain(`/api/attendance/held-scans?${range}&status=BELUM_DITINJAU`)
    expect(result.current.total).toBe(0)
  })
})
