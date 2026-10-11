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

/**
 * Dipanggil ketika backend membalas 401 untuk request yang membutuhkan login, misalnya karena
 * sesi berakhir (8 jam) atau dicabut setelah password di-reset / akun dinonaktifkan / role diubah.
 * Login dan pengecekan sesi awal tidak ikut, karena 401 di sana adalah hasil normal.
 */
const unauthorizedListeners = new Set<() => void>()
const SESSION_CHECK_PATHS = ['/api/auth/login', '/api/auth/me']

export function onUnauthorized(listener: () => void): () => void {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

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
    if (response.status === 401 && !SESSION_CHECK_PATHS.includes(path)) {
      unauthorizedListeners.forEach((listener) => listener())
    }
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
