import { hashKey, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

import { ApiError, api, onUnauthorized } from '@/lib/api'
import type { Me } from '@/lib/types'

interface AuthState {
  me: Me | null
  loading: boolean
  /** True jika sesi berakhir saat sedang dipakai; halaman login menampilkan pemberitahuan. */
  sessionExpired: boolean
  login: (username: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  refresh: () => Promise<unknown>
}

const AuthContext = createContext<AuthState | null>(null)

const ME_KEY = ['auth', 'me']
const ME_HASH = hashKey(ME_KEY)

async function fetchMe(): Promise<Me | null> {
  try {
    return await api<Me>('/api/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

/**
 * Ganti pengguna sesi: buang data milik sesi lama, kecuali query "me" yang sedang diamati
 * AuthProvider. queryClient.clear() ikut membuang query itu sehingga observer tidak pernah
 * melihat nilai barunya (login sukses tapi tetap terlempar ke /login).
 */
function resetSession(queryClient: QueryClient, me: Me | null) {
  queryClient.removeQueries({ predicate: (query) => query.queryHash !== ME_HASH })
  queryClient.setQueryData(ME_KEY, me)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { data, isPending, refetch } = useQuery({ queryKey: ME_KEY, queryFn: fetchMe, retry: false })
  const [sessionExpired, setSessionExpired] = useState(false)

  // Sesi berakhir atau dicabut di tengah pemakaian: buang data lama dan kembali ke halaman login.
  useEffect(
    () =>
      onUnauthorized(() => {
        if (queryClient.getQueryData(ME_KEY)) {
          setSessionExpired(true)
        }
        resetSession(queryClient, null)
      }),
    [queryClient],
  )

  const login = async (username: string, password: string) => {
    const me = await api<Me>('/api/auth/login', { method: 'POST', body: { username, password } })
    resetSession(queryClient, me)
    setSessionExpired(false)
    return me
  }

  const logout = async () => {
    await api('/api/auth/logout', { method: 'POST' })
    resetSession(queryClient, null)
  }

  return (
    <AuthContext.Provider
      value={{ me: data ?? null, loading: isPending, sessionExpired, login, logout, refresh: refetch }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthState {
  const state = useContext(AuthContext)
  if (!state) {
    throw new Error('useAuth harus dipakai di dalam AuthProvider')
  }
  return state
}
