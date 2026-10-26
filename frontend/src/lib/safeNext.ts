/**
 * Tujuan kembali setelah login (`?next=`). Hanya path relatif internal yang diterima agar
 * parameter ini tidak bisa dipakai untuk mengalihkan pengguna ke situs lain (open redirect).
 */
export function safeNextPath(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) {
    return null
  }
  // Backslash dan karakter kontrol ditafsirkan peramban sebagai pemisah host atau disaring.
  // eslint-disable-next-line no-control-regex
  if (/[\\\u0000-\u001f\u007f]/.test(raw)) {
    return null
  }
  const path = raw.split(/[?#]/, 1)[0].toLowerCase().replace(/\/+$/, '')
  if (path === '/login' || path === '/ganti-password') {
    return null
  }
  return raw
}

/** Menambahkan `?next=` (ter-encode) ke path tujuan; tanpa next (null) path dikembalikan apa adanya. */
export function appendNext(target: string, next: string | null): string {
  return next ? `${target}?next=${encodeURIComponent(next)}` : target
}

/** Seperti appendNext, dari lokasi asal; dilewati bila lokasi asal bukan tujuan yang berarti. */
export function withNext(target: string, location: { pathname: string; search: string; hash: string }): string {
  if (location.pathname === '/') {
    return target
  }
  return appendNext(target, safeNextPath(location.pathname + location.search + location.hash))
}
