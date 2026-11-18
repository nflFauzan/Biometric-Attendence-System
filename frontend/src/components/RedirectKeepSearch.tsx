import { Navigate, useLocation, useParams, type Params } from 'react-router'

/**
 * Mengalihkan alamat lama ke alamat baru (K-145) dengan query string lama ikut dibawa, agar bookmark seperti
 * /absensi/karyawan/12?tanggal=2026-11-02 tetap membuka tanggal yang sama. Param milik alamat tujuan menang.
 */
export function RedirectKeepSearch({ to }: { to: string | ((params: Params) => string) }) {
  const params = useParams()
  const { search } = useLocation()
  const target = typeof to === 'function' ? to(params) : to
  const [pathname, targetQuery = ''] = target.split('?')
  const merged = new URLSearchParams(targetQuery)
  new URLSearchParams(search).forEach((value, key) => {
    if (!merged.has(key)) merged.set(key, value)
  })
  const query = merged.toString()
  return <Navigate to={query ? `${pathname}?${query}` : pathname} replace />
}
