import { vi } from 'vitest'

type Handler = (init: RequestInit | undefined) => { status: number; body?: unknown }

/** Mock fetch berdasarkan "METHOD path". Request yang tidak terdaftar dibalas 404. */
export function mockFetch(routes: Record<string, Handler>) {
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    const key = `${init?.method ?? 'GET'} ${url}`
    const handler = routes[key]
    const { status, body } = handler ? handler(init) : { status: 404, body: { detail: `Tidak ada mock: ${key}` } }
    return new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { 'Content-Type': 'application/json' },
    })
  })
  vi.stubGlobal('fetch', fn)
  return fn
}
