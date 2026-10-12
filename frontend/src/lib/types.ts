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

// ---- Fase 2: aturan kerja, kalender libur, simulasi perhitungan ----

export type RuleLevel = 'GLOBAL' | 'CATEGORY' | 'EMPLOYEE'

export const RULE_LEVEL_LABELS: Record<RuleLevel, string> = {
  GLOBAL: 'Global',
  CATEGORY: 'Kategori',
  EMPLOYEE: 'Karyawan',
}

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export const DAYS: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Senin' },
  { value: 'TUESDAY', label: 'Selasa' },
  { value: 'WEDNESDAY', label: 'Rabu' },
  { value: 'THURSDAY', label: 'Kamis' },
  { value: 'FRIDAY', label: 'Jumat' },
  { value: 'SATURDAY', label: 'Sabtu' },
  { value: 'SUNDAY', label: 'Minggu' },
]

/** Parameter aturan; null = mewarisi level di atasnya. Uang dikirim backend sebagai angka desimal. */
export interface RuleValues {
  workStart: string | null
  workEnd: string | null
  breakStart: string | null
  breakEnd: string | null
  lateToleranceMinutes: number | null
  overtimeStart: string | null
  workDays: DayOfWeek[] | null
  dailyWage: number | null
  overtimeRatePerHour: number | null
  baseSalary: number | null
}

export interface WorkRule extends RuleValues {
  id: number
  scope: RuleLevel
  categoryId: number | null
  categoryName: string | null
  employeeId: number | null
  employeeName: string | null
  effectiveFrom: string
  note: string | null
  createdBy: string
  /** Tanggal berlakunya sudah tiba; tidak bisa diubah atau dihapus (K-050). */
  locked: boolean
}

export type RuleField =
  | 'WORK_START'
  | 'WORK_END'
  | 'BREAK'
  | 'LATE_TOLERANCE'
  | 'OVERTIME_START'
  | 'WORK_DAYS'
  | 'DAILY_WAGE'
  | 'OVERTIME_RATE'
  | 'BASE_SALARY'

export interface RuleSource {
  level: RuleLevel
  versionId: number | null
  effectiveFrom: string
}

export interface EffectiveRule {
  employeeId: number
  employeeName: string
  categoryId: number
  categoryName: string
  date: string
  values: RuleValues
  sources: Partial<Record<RuleField, RuleSource>>
  missing: string[]
}

export interface Holiday {
  id: number
  date: string
  name: string
}

export type DayType = 'HARI_KERJA' | 'BUKAN_HARI_KERJA' | 'HARI_LIBUR'
export type AttendanceStatus = 'LENGKAP' | 'TIDAK_LENGKAP' | 'TIDAK_HADIR' | 'TIDAK_ADA_SCAN'
export type CalculationFlag = 'ISTIRAHAT_BELUM_DIATUR' | 'PERLU_TINJAUAN_HARI_LIBUR' | 'URUTAN_TIDAK_WAJAR'

export interface TimeValue {
  time: string
  source: 'SCAN' | 'KOREKSI'
}

/** Durasi dalam detik; null = tidak dihitung. */
export interface DailyResult {
  date: string
  dayType: DayType
  status: AttendanceStatus
  scanTimes: string[]
  checkIn: TimeValue | null
  checkOut: TimeValue | null
  lateSeconds: number | null
  earlyLeaveSeconds: number | null
  regularWorkSeconds: number | null
  overtimeCandidateSeconds: number | null
  totalPresenceSeconds: number | null
  flags: CalculationFlag[]
}

export interface DailyPreview {
  rule: EffectiveRule
  timezone: string
  holiday: boolean
  result: DailyResult
}
