import type { Tone } from '@/lib/attendance'
import { formatDate } from '@/lib/format'
import type { LeaveRequest, LeaveStatus } from '@/lib/types'

export const LEAVE_STATUS_LABELS: Record<LeaveStatus, string> = {
  MENUNGGU: 'Menunggu',
  DISETUJUI: 'Disetujui',
  DITOLAK: 'Ditolak',
  DIBATALKAN: 'Dibatalkan',
}

export const LEAVE_STATUS_TONES: Record<LeaveStatus, Tone> = {
  MENUNGGU: 'warning',
  DISETUJUI: 'success',
  DITOLAK: 'danger',
  DIBATALKAN: 'neutral',
}

/** Jenis file yang diterima backend (K-078); jenis sebenarnya tetap diperiksa dari isi file di server. */
export const ATTACHMENT_ACCEPT = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'
export const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
export const MAX_ATTACHMENTS = 5

/**
 * "manager" = Owner/HR lewat /api/leave-requests; "mine" = pengajuan sendiri lewat /api/me/leave-requests.
 * Path halaman web mengikuti mode yang sama.
 */
export type LeaveMode = 'manager' | 'mine'

export function leaveApiBase(mode: LeaveMode): string {
  return mode === 'manager' ? '/api/leave-requests' : '/api/me/leave-requests'
}

export function leavePageBase(mode: LeaveMode): string {
  return mode === 'manager' ? '/izin' : '/izin-saya'
}

export function formatLeaveRange(request: Pick<LeaveRequest, 'startDate' | 'endDate'>): string {
  return request.startDate === request.endDate
    ? formatDate(request.startDate)
    : `${formatDate(request.startDate)} – ${formatDate(request.endDate)}`
}

/** "3 hari (2 hari kerja)"; hari kerja tidak ditampilkan bila belum bisa dihitung. */
export function formatLeaveDays(request: Pick<LeaveRequest, 'calendarDays' | 'workingDays'>): string {
  const calendar = `${request.calendarDays} hari`
  return request.workingDays == null ? calendar : `${calendar} (${request.workingDays} hari kerja)`
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  if (bytes < 1024 * 1024) {
    return `${Math.round(bytes / 1024)} KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

/** Pemeriksaan awal di browser agar pengguna langsung tahu; server tetap memeriksa ulang. */
export function attachmentProblem(file: File): string | null {
  if (file.size === 0) {
    return 'File kosong'
  }
  if (file.size > MAX_ATTACHMENT_BYTES) {
    return 'Ukuran file maksimal 5 MB'
  }
  if (!/\.(pdf|jpe?g|png)$/i.test(file.name)) {
    return 'Format file harus PDF, JPG, atau PNG'
  }
  return null
}
