import { afterEach, describe, expect, it, vi } from 'vitest'

import { ApiError, api } from '@/lib/api'
import { mockFetch } from '@/test/fetchMock'

describe('api', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('mengirim token CSRF dari cookie pada request yang mengubah data', async () => {
    document.cookie = 'XSRF-TOKEN=token-123'
    const fetchFn = mockFetch({ 'POST /api/branches': () => ({ status: 201, body: { id: 1 } }) })

    await api('/api/branches', { method: 'POST', body: { code: 'A' } })

    const init = fetchFn.mock.calls[0][1] as RequestInit
    expect((init.headers as Record<string, string>)['X-XSRF-TOKEN']).toBe('token-123')
    expect(init.body).toBe('{"code":"A"}')
  })

  it('mengubah ProblemDetail menjadi ApiError beserta error per field', async () => {
    mockFetch({
      'GET /api/branches': () => ({
        status: 400,
        body: { detail: 'Data tidak valid', errors: { code: 'Kode wajib diisi' } },
      }),
    })

    const error = (await api('/api/branches').catch((e: unknown) => e)) as ApiError

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.message).toBe('Data tidak valid')
    expect(error.fieldErrors).toEqual({ code: 'Kode wajib diisi' })
  })
})
