import { useQuery, useQueryClient } from '@tanstack/react-query'
import { createContext, useContext, type ReactNode } from 'react'

import { ApiError, api } from '@/lib/api'
import type { Me } from '@/lib/types'

interface AuthState {
  me: Me | null
  loading: boolean
  login: (username: string, password: string) => Promise<Me>
  logout: () => Promise<void>
  refresh: () => Promise<unknown>
}

const AuthContext = createContext<AuthState | null>(null)

const ME_KEY = ['auth', 'me']

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

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { data, isPending, refetch } = useQuery({ queryKey: ME_KEY, queryFn: fetchMe, retry: false })

  const login = async (username: string, password: string) => {
    const me = await api<Me>('/api/auth/login', { method: 'POST', body: { username, password } })
    queryClient.clear()
    queryClient.setQueryData(ME_KEY, me)
    return me
  }

  const logout = async () => {
    await api('/api/auth/logout', { method: 'POST' })
    queryClient.clear()
    queryClient.setQueryData(ME_KEY, null)
  }

  return (
    <AuthContext.Provider value={{ me: data ?? null, loading: isPending, login, logout, refresh: refetch }}>
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
