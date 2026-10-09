/** Error dari API dalam format ProblemDetail (RFC 9457) yang dikirim backend. */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/** Backend mengirim cookie XSRF-TOKEN; kalau belum ada, minta dulu sebelum request yang mengubah data. */
async function csrfToken(): Promise<string> {
  let token = readCookie(CSRF_COOKIE)
  if (!token) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' })
    token = readCookie(CSRF_COOKIE)
  }
  return token ? decodeURIComponent(token) : ''
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE'

export async function api<T>(path: string, options: { method?: Method; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    headers[CSRF_HEADER] = await csrfToken()
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => null)
    throw new ApiError(
      response.status,
      problem?.detail ?? `Terjadi kesalahan (${response.status})`,
      problem?.errors ?? {},
    )
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
