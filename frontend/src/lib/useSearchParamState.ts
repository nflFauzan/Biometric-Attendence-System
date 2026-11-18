import { useCallback } from 'react'
import { useSearchParams } from 'react-router'

/**
 * Nilai filter yang disimpan di query string, agar tautan (mis. dari daftar cek gaji) bisa membuka periode yang sama
 * dan bookmark tetap bekerja. Nilai bawaan tidak ditulis ke URL. Perubahan memakai replace, jadi tombol Kembali tidak
 * melewati setiap perubahan filter.
 */
export function useSearchParamState(key: string, fallback: string): [string, (value: string) => void] {
  const [searchParams, setSearchParams] = useSearchParams()
  const value = searchParams.get(key) ?? fallback

  const set = useCallback(
    (next: string) => {
      setSearchParams(
        (prev) => {
          const params = new URLSearchParams(prev)
          if (next === '' || next === fallback) {
            params.delete(key)
          } else {
            params.set(key, next)
          }
          return params
        },
        { replace: true },
      )
    },
    [key, fallback, setSearchParams],
  )

  return [value, set]
}
