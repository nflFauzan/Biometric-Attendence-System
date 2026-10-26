export type Role = 'OWNER' | 'HR_FINANCE' | 'EMPLOYEE'

export const ROLE_LABELS: Record<Role, string> = {
  OWNER: 'Owner',
  HR_FINANCE: 'Finance & Accounting',
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
  /** PIN/User ID di mesin absensi (K-056). */
  devicePin: string | null
  /** Profil dari template data client (K-129). */
  email: string | null
  gender: Gender | null
  birthDate: string | null
  address: string | null
  notes: string | null
}

export type Gender = 'LAKI_LAKI' | 'PEREMPUAN' | 'TIDAK_DISEBUTKAN'

/** Satu baris pratinjau impor karyawan (K-130); username dan role boleh diubah Owner. */
export interface ImportPreviewRow {
  rowNumber: number
  employeeNumber: string | null
  fullName: string | null
  unit: string | null
  categoryId: number | null
  categoryName: string | null
  position: string | null
  branchId: number | null
  branchName: string | null
  phone: string | null
  email: string | null
  gender: Gender | null
  birthDate: string | null
  address: string | null
  notes: string | null
  accountStatus: string | null
  username: string | null
  role: Role | null
  errors: string[]
}

export interface ImportPreview {
  startDate: string
  rows: ImportPreviewRow[]
  errorCount: number
}

export interface ImportCreatedAccount {
  employeeNumber: string
  fullName: string
  username: string
  role: Role
  initialPassword: string
}

export interface ImportCommitResult {
  employeesCreated: number
  accountsCreated: number
  accounts: ImportCreatedAccount[]
}

export const GENDER_LABELS: Record<Gender, string> = {
  LAKI_LAKI: 'Laki-laki',
  PEREMPUAN: 'Perempuan',
  TIDAK_DISEBUTKAN: 'Tidak ingin menyebutkan',
}

/** Cabang tempat karyawan boleh absen (K-103): penempatan + tambahan. */
export interface BranchAccess {
  placementBranchId: number
  extraBranchIds: number[]
  /** Jumlah catatan jari yang dihapus karena cabangnya dicabut; 0 pada GET. */
  removedFingerprints: number
}

/** Status perintah mesin; "aktif" = MENUNGGU atau DIKIRIM (spec §16). */
export type CommandStatus = 'MENUNGGU' | 'DIKIRIM' | 'BERHASIL' | 'GAGAL' | 'KEDALUWARSA' | 'DIBATALKAN'

/** Perintah ke alat rakitan; `message` adalah teks status siap tampil dari server. */
export interface DeviceCommand {
  id: string
  type: 'ENROLL' | 'DELETE_TEMPLATE' | 'DELETE_EMPLOYEE' | 'APPLY_NETWORK' | 'ROTATE_KEY'
  status: CommandStatus
  deviceId: number
  deviceName: string
  employeeId: number | null
  finger: number | null
  step: number | null
  errorCode: string | null
  message: string
  createdAt: string
  finishedAt: string | null
}

/** Jari yang sudah terdaftar; template tidak pernah dikirim ke web. */
export interface FingerRecord {
  finger: number
  deviceId: number
  deviceName: string
  enrolledAt: string
}

export interface FingerprintOverview {
  /** Persetujuan karyawan (K-091); null = belum disetujui. */
  consentAt: string | null
  consentBy: string | null
  fingers: FingerRecord[]
  activeCommands: DeviceCommand[]
  /** Perintah hapus di mesin yang belum berhasil, satu baris per mesin (spec §17.8). */
  deletions: Deletion[]
  /** Ada mesin X401 aktif; sidik jari di sana harus dihapus langsung di mesin. */
  zkPushDeviceActive: boolean
}

/** Penghapusan sidik jari di satu alat rakitan; `message` sudah dalam Bahasa Indonesia. */
export interface Deletion {
  commandId: string
  type: 'DELETE_TEMPLATE' | 'DELETE_EMPLOYEE'
  /** Null untuk DELETE_EMPLOYEE (semua jari). */
  finger: number | null
  deviceId: number
  deviceName: string
  status: CommandStatus
  message: string
  /** Hanya penghapusan GAGAL yang bisa dicoba lagi. */
  retryable: boolean
}

/** Jenis mesin; tidak bisa diubah setelah dibuat (K-099). */
export type DeviceProtocol = 'ZK_PUSH' | 'RAKITAN_V1'

/** Kunci alat rakitan yang baru diterbitkan; hanya ada di response penerbitan (K-088). */
export interface IssuedKey {
  deviceKey: string
  /** Alamat server bawaan untuk kode pengaturan (DEVICE_PUBLIC_URL), atau null. */
  setupServerUrl: string | null
}

export interface Device {
  id: number
  name: string
  /** Nomor seri ZK-Push, atau ID `RK-…` buatan server untuk alat rakitan. */
  serialNumber: string
  branchId: number
  branchName: string
  active: boolean
  protocol: DeviceProtocol
  /** Scan yang lebih lama tidak disimpan (K-057). */
  acceptScansFrom: string
  ignoredScanCount: number
  lastSeenAt: string | null
  lastIp: string | null
  firmware: string | null
  online: boolean
  /** Alat rakitan yang sudah punya kunci. */
  keyIssued: boolean
  /** Kontak pertama dengan kunci saat ini; null = belum diatur di mesin. */
  keyFirstUsedAt: string | null
  /** Ringkasan heartbeat terakhir, mis. "uptime=3600s; antrean=2; sensor=12/150". */
  lastInfo: string | null
  /** Isi dan kapasitas sensor dari heartbeat terakhir alat rakitan; null = belum dilaporkan. */
  sensorUsed: number | null
  sensorCapacity: number | null
  /** Terisi melewati ambang app.device.sensor-warning-percent. */
  sensorAlmostFull: boolean
  /** Versi paket pengaturan ringan di server; 0 = belum pernah disimpan dari web (spec §18.2). */
  settingsVersion: number
  /** Versi yang terakhir dilaporkan dipakai mesin. */
  settingsAppliedVersion: number
  issuedKey: IssuedKey | null
}

export type NameDisplay = 'FULL' | 'FIRST' | 'PIN'

/** Pengaturan ringan alat rakitan (spec §18.1). */
export interface DeviceSettingsValues {
  /** Kosong = nama cabang. */
  headerText: string
  greeting: string
  nameDisplay: NameDisplay
  brightness: number
  buzzerVolume: number
  duplicateNoticeSeconds: number
  heartbeatSeconds: number
}

export interface DeviceSettingsResponse {
  deviceId: number
  version: number
  appliedVersion: number
  values: DeviceSettingsValues
  updatedAt: string | null
  updatedBy: string | null
}

/** Jaringan yang berlaku (tanpa password) dan perintah jaringan/kunci terakhir (spec §18.5). */
export interface NetworkView {
  deviceId: number
  /** Kosong bersama currentServerUrl null = belum pernah diatur dari web. */
  currentWifi: string[]
  currentServerUrl: string | null
  latest: DeviceCommand | null
}

export interface UnregisteredAttempt {
  serialNumber: string
  ip: string | null
  lastAttemptAt: string
  attempts: number
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
  /** K-092: scan berdekatan dihitung satu kejadian; null = tanpa penggabungan. */
  minScanGapMinutes: number | null
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
  | 'MIN_SCAN_GAP'

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
export type AttendanceStatus = 'LENGKAP' | 'TIDAK_LENGKAP' | 'TIDAK_HADIR' | 'TIDAK_ADA_SCAN' | 'IZIN'
export type CalculationFlag =
  | 'ISTIRAHAT_BELUM_DIATUR'
  | 'PERLU_TINJAUAN_HARI_LIBUR'
  | 'URUTAN_TIDAK_WAJAR'
  | 'HADIR_SAAT_IZIN'
  | 'IZIN_MENUNGGU'
  | 'ADA_SCAN_TERTAHAN'

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

// ---- Fase 3: absensi, koreksi, dashboard ----

export type ScanFlag = 'PIN_TIDAK_DIKENAL' | 'WAKTU_DI_MASA_DEPAN' | 'CABANG_BERBEDA' | 'CABANG_TIDAK_DIIZINKAN'

/** Status peninjauan scan yang tertahan karena cabang (K-103). */
export type ReviewStatus = 'BELUM_DITINJAU' | 'DITERIMA' | 'DIABAIKAN'

/** Scan asli dari mesin; deviceLocalTime persis seperti dikirim mesin. */
export interface Scan {
  id: number
  scannedAt: string
  deviceLocalTime: string
  deviceId: number
  deviceName: string
  devicePin: string
  employeeId: number | null
  flags: ScanFlag[]
  receivedAt: string
  /** Null bila scan tidak tertahan karena cabang. */
  review: ReviewStatus | null
  /** False bila tertahan dan belum diterima, sehingga tidak masuk rekap. */
  counted: boolean
}

export interface HeldScan {
  scan: Scan
  employeeName: string | null
  placementBranchName: string | null
  deviceBranchName: string | null
  status: ReviewStatus
  reason: string | null
  decidedBy: string | null
  decidedAt: string | null
}

export interface Correction {
  id: number
  employeeId: number
  workDate: string
  checkIn: string | null
  checkOut: string | null
  reason: string
  createdBy: string
  createdAt: string
  supersededAt: string | null
}

/** Rekap satu hari; result null bila aturan belum lengkap (ruleMissing berisi pesannya). */
export interface DayRecord {
  date: string
  result: DailyResult | null
  ruleMissing: string | null
  scans: Scan[]
  correction: Correction | null
  scanFlags: ScanFlag[]
  /** Izin/cuti disetujui (diutamakan) atau menunggu yang mencakup tanggal ini (K-076). */
  leave?: LeaveInfo | null
}

export interface LeaveInfo {
  requestId: number
  leaveTypeName: string
  status: LeaveStatus
  startDate: string
  endDate: string
}

export interface DailyRow {
  employeeId: number
  employeeNumber: string
  fullName: string
  devicePin: string | null
  branchId: number
  branchName: string
  categoryName: string
  day: DayRecord
}

export interface DailyAttendance {
  date: string
  branchId: number | null
  rows: DailyRow[]
}

export interface EmployeeAttendance {
  employeeId: number
  employeeNumber: string
  fullName: string
  branchName: string
  timezone: string
  devicePin: string | null
  from: string
  to: string
  days: DayRecord[]
  corrections: Correction[]
}

export interface AttendanceCounts {
  totalEmployees: number
  present: number
  late: number
  incomplete: number
  overtimeCandidates: number
  absent: number
  needsReview: number
  ruleMissing: number
  /** Izin/cuti disetujui pada tanggal tersebut. */
  onLeave?: number
}

export interface DashboardSummary {
  date: string
  totals: AttendanceCounts
  branches: { branchId: number; branchName: string; counts: AttendanceCounts }[]
  devices: Device[]
  recentScans: {
    id: number
    scannedAt: string
    devicePin: string
    employeeId: number | null
    employeeName: string | null
    deviceName: string
    flags: ScanFlag[]
  }[]
  unmatchedScans: number
  /** Scan tertahan karena cabang yang belum ditinjau (K-103). */
  heldScans: number
  /** Pengajuan izin/cuti yang menunggu keputusan (semua tanggal). */
  pendingLeaveRequests?: number
}

// ---- Fase 4: izin dan cuti ----

export type LeaveStatus = 'DRAFT' | 'PERLU_TINJAU_HR' | 'PERLU_TINJAU_OWNER' | 'DISETUJUI' | 'DITOLAK' | 'DIBATALKAN'
export type LetterTemplate = 'UMUM' | 'IZIN' | 'CUTI' | 'SAKIT'
export type DecisionMethod = 'WEB' | 'WHATSAPP' | 'LISAN' | 'TERTULIS'
export type ApprovalStage = 'HR' | 'OWNER'

/** Satu tahap persetujuan. Keputusan Owner yang dicatat HR punya decidedBy "OWNER" dan recordedBy HR. */
export interface LeaveApproval {
  stage: ApprovalStage
  approved: boolean
  decidedBy: string
  decidedByName: string
  decidedAt: string
  method: DecisionMethod
  recordedBy: string
  recordedByName: string
  recordedAt: string
  note: string | null
  hasEvidence: boolean
}

/** paid null = belum ditentukan (dipakai di Fase 5). */
export interface LeaveType {
  id: number
  name: string
  requiresDocument: boolean
  paid: boolean | null
  description: string | null
  active: boolean
  requiresOwnerApproval: boolean
  letterTemplate: LetterTemplate
}

export interface LeaveAttachment {
  id: number
  originalName: string
  contentType: string
  sizeBytes: number
  uploadedBy: string
  uploadedAt: string
}

/** workingDays null = tidak dihitung (daftar) atau hari kerja belum diatur. */
export interface LeaveRequest {
  id: number
  employeeId: number
  employeeNumber: string
  employeeName: string
  position: string | null
  branchId: number
  branchName: string
  leaveTypeId: number
  leaveTypeName: string
  requiresDocument: boolean
  startDate: string | null
  endDate: string | null
  calendarDays: number
  workingDays: number | null
  reason: string
  status: LeaveStatus
  /** Null selama masih draft. */
  submittedBy: string | null
  submittedAt: string | null
  decidedBy: string | null
  decidedAt: string | null
  decisionNote: string | null
  cancelledBy: string | null
  cancelledAt: string | null
  cancelReason: string | null
  attachments: LeaveAttachment[]
  contactPhone: string | null
  leaveAddress: string | null
  delegateTo: string | null
  healthFacility: string | null
  letterTemplate: LetterTemplate
  requiresOwnerApproval: boolean
  createdAt: string
  approvals: LeaveApproval[]
  /** Tahap yang harus memutus; null bila tidak sedang menunggu. */
  nextStage: ApprovalStage | null
  /** Apakah penampil saat ini boleh menyetujui/menolak sekarang; dihitung server (endpoint karyawan selalu false). */
  canDecide: boolean
  /** Apakah penampil saat ini boleh mencatat keputusan Owner di luar web. */
  canRecordOwnerDecision: boolean
  /** Pengajuan ditolak yang menjadi asal draft ini (K-126). */
  resubmittedFromId: number | null
}

/** Jawaban verifikasi publik surat (tanpa login); sengaja minimal. */
export interface PublicLetter {
  letterNumber: string
  employeeName: string
  branchName: string
  leaveTypeName: string
  startDate: string | null
  endDate: string | null
  status: LeaveStatus
  approvals: { stage: ApprovalStage; approved: boolean; decidedByName: string; decidedAt: string; method: DecisionMethod }[]
}

export interface LeaveUsage {
  employeeId: number
  year: number
  types: { leaveTypeId: number; leaveTypeName: string; requests: number; calendarDays: number; workingDays: number | null }[]
  pendingRequests: number
}

export interface PendingLeaveSummary {
  count: number
  items: {
    id: number
    employeeId: number
    employeeName: string
    branchName: string
    leaveTypeName: string
    startDate: string
    endDate: string
    submittedAt: string | null
    status: LeaveStatus
  }[]
  /** Keputusan Owner yang dicatat HR dalam 7 hari terakhir; selalu kosong untuk non-Owner. */
  recordedForOwner: RecordedDecision[]
}

export interface RecordedDecision {
  approvalId: number
  requestId: number
  employeeName: string
  leaveTypeName: string | null
  approved: boolean
  method: DecisionMethod
  recorderName: string
  recordedAt: string
  status: LeaveStatus
}

/** Absensi Saya (K-127; bentuk dari daftar rekan kerja K-083/K-084): hanya kolom yang diizinkan. */
export type TeamStatus = 'HADIR' | 'TIDAK_LENGKAP' | 'TIDAK_HADIR' | 'IZIN' | 'LIBUR' | 'BELUM_DIATUR'

export interface TeamDay {
  date: string
  status: TeamStatus
  checkIn: string | null
  checkOut: string | null
  lateSeconds: number | null
  overtimeSeconds: number | null
}

export interface TeamMember {
  employeeId: number
  employeeNumber: string
  fullName: string
  branchName: string
  position: string | null
  from: string
  to: string
  days: TeamDay[]
}
