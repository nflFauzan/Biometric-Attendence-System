export type Role = 'OWNER' | 'HR_FINANCE' | 'EMPLOYEE'

export const ROLE_LABELS: Record<Role, string> = {
  OWNER: 'Owner',
  HR_FINANCE: 'HR/Finance',
  EMPLOYEE: 'Karyawan',
}

export interface Me {
  username: string
  role: Role
  employeeId: number | null
  employeeName: string | null
  mustChangePassword: boolean
}

export interface Branch {
  id: number
  code: string
  name: string
  address: string | null
  timezone: string
  active: boolean
}

export interface Category {
  id: number
  code: string
  name: string
  active: boolean
}

export interface Employee {
  id: number
  employeeNumber: string
  fullName: string
  phone: string | null
  branchId: number
  branchName: string
  categoryId: number
  categoryName: string
  position: string | null
  joinDate: string
  endDate: string | null
  active: boolean
}

export interface Device {
  id: number
  name: string
  serialNumber: string
  branchId: number
  branchName: string
  active: boolean
}

export interface UserAccount {
  id: number
  username: string
  role: Role
  employeeId: number | null
  employeeName: string | null
  active: boolean
  mustChangePassword: boolean
  lastLoginAt: string | null
}

export interface AuditLog {
  id: number
  occurredAt: string
  actorUsername: string | null
  action: string
  entityType: string
  entityId: string | null
  beforeData: string | null
  afterData: string | null
  reason: string | null
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
