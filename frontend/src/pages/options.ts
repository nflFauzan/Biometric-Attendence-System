import { useQuery } from '@tanstack/react-query'

import { api } from '@/lib/api'
import type { Branch, Category, Employee } from '@/lib/types'

export function useBranchOptions() {
  const { data } = useQuery({ queryKey: ['/api/branches'], queryFn: () => api<Branch[]>('/api/branches') })
  return (data ?? []).map((b) => ({ value: String(b.id), label: b.active ? b.name : `${b.name} (nonaktif)` }))
}

export function useCategoryOptions() {
  const { data } = useQuery({
    queryKey: ['/api/employee-categories'],
    queryFn: () => api<Category[]>('/api/employee-categories'),
  })
  return (data ?? []).map((c) => ({ value: String(c.id), label: c.name }))
}

export function useEmployeeOptions() {
  const { data } = useQuery({ queryKey: ['/api/employees'], queryFn: () => api<Employee[]>('/api/employees') })
  return (data ?? []).map((e) => ({ value: String(e.id), label: `${e.fullName} (${e.employeeNumber})` }))
}
