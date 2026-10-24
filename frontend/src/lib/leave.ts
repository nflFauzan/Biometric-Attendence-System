import type { Tone } from '@/lib/attendance'
import { ApiError } from '@/lib/api'
import { formatDate } from '@/lib/format'
import type { DecisionMethod, LeaveRequest, LeaveStatus, LetterTemplate } from '@/lib/types'

export const LEAVE_STATUS_LABELS: Record<LeaveStatus, string> = {
  DRAFT: 'Draft',
  PERLU_TINJAU_HR: 'Perlu ditinjau HR',
  PERLU_TINJAU_OWNER: 'Perlu ditinjau Owner',
  DISETUJUI: 'Disetujui',
  DITOLAK: 'Ditolak',
  DIBATALKAN: 'Dibatalkan',
}

export const LEAVE_STATUS_TONES: Record<LeaveStatus, Tone> = {
  DRAFT: 'neutral',
  PERLU_TINJAU_HR: 'warning',
  PERLU_TINJAU_OWNER: 'warning',
  DISETUJUI: 'success',
  DITOLAK: 'danger',
  DIBATALKAN: 'neutral',
}

/** Menunggu keputusan HR atau Owner. */
export function isPendingStatus(status: LeaveStatus): boolean {
  return status === 'PERLU_TINJAU_HR' || status === 'PERLU_TINJAU_OWNER'
}

export const DECISION_METHOD_LABELS: Record<DecisionMethod, string> = {
  WEB: 'Web',
  WHATSAPP: 'WhatsApp',
  LISAN: 'Lisan',
  TERTULIS: 'Tertulis',
}

export const LETTER_TEMPLATE_LABELS: Record<LetterTemplate, string> = {
  UMUM: 'Umum',
  IZIN: 'Izin',
  CUTI: 'Cuti',
  SAKIT: 'Sakit',
}

export type LetterFieldKey = 'contactPhone' | 'leaveAddress' | 'delegateTo' | 'healthFacility'

export interface LetterField {
  key: LetterFieldKey
  label: string
  required: boolean
  inputMode?: 'tel'
}

const PHONE = 'No. HP yang bisa dihubungi'

/** Isian surat per templat (spec bagian 7). Kewajiban final divalidasi server saat pengajuan dikirim. */
export const LETTER_FIELDS: Record<LetterTemplate, { reasonLabel: string; fields: LetterField[] }> = {
  UMUM: { reasonLabel: 'Alasan', fields: [{ key: 'contactPhone', label: PHONE, required: false, inputMode: 'tel' }] },
  IZIN: { reasonLabel: 'Keperluan', fields: [{ key: 'contactPhone', label: PHONE, required: false, inputMode: 'tel' }] },
  CUTI: {
    reasonLabel: 'Keperluan cuti',
    fields: [
      { key: 'contactPhone', label: PHONE, required: true, inputMode: 'tel' },
      { key: 'leaveAddress', label: 'Alamat selama cuti', required: true },
      { key: 'delegateTo', label: 'Tugas dilimpahkan kepada', required: false },
    ],
  },
  SAKIT: {
    reasonLabel: 'Keluhan / keterangan sakit',
    fields: [
      { key: 'contactPhone', label: PHONE, required: false, inputMode: 'tel' },
      { key: 'healthFacility', label: 'Nama faskes / dokter', required: false },
    ],
  },
}

/** Nomor tampilan surat dari ID pengajuan, mis. IZ-000123. */
export function letterNumber(id: number): string {
  return `IZ-${String(id).padStart(6, '0')}`
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

export const NO_DATES = 'Tanggal belum diisi'

export function formatLeaveRange(request: Pick<LeaveRequest, 'startDate' | 'endDate'>): string {
  if (!request.startDate || !request.endDate) {
    return NO_DATES
  }
  return request.startDate === request.endDate
    ? formatDate(request.startDate)
    : `${formatDate(request.startDate)} – ${formatDate(request.endDate)}`
}

/** "3 hari (2 hari kerja)"; hari kerja tidak ditampilkan bila belum bisa dihitung. */
export function formatLeaveDays(request: Pick<LeaveRequest, 'startDate' | 'endDate' | 'calendarDays' | 'workingDays'>): string {
  if (!request.startDate || !request.endDate) {
    return NO_DATES
  }
  const calendar = `${request.calendarDays} hari`
  return request.workingDays == null ? calendar : `${calendar} (${request.workingDays} hari kerja)`
}

/** Ekstensi berkas bukti dari tipe blob; kosong bila tipenya tidak dikenal. */
export function evidenceExtension(blob: Blob): string {
  switch (blob.type) {
    case 'application/pdf':
      return '.pdf'
    case 'image/png':
      return '.png'
    case 'image/jpeg':
    case 'image/jpg':
      return '.jpg'
    default:
      return ''
  }
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

/** Selisih label "lewat ..." untuk riwayat persetujuan. */
export const DECISION_METHOD_PHRASES: Record<DecisionMethod, string> = {
  WEB: 'lewat web',
  WHATSAPP: 'lewat WhatsApp',
  LISAN: 'secara lisan',
  TERTULIS: 'secara tertulis',
}

/** Jumlah hari kalender inklusif (akhir - mulai + 1); null bila tanggal kosong, tidak valid, atau terbalik. */
export function inclusiveDays(startDate: string, endDate: string): number | null {
  const parse = (iso: string) => {
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
    return match ? Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])) : null
  }
  const start = parse(startDate)
  const end = parse(endDate)
  if (start == null || end == null || end < start) {
    return null
  }
  return Math.round((end - start) / 86_400_000) + 1
}

export type WizardStep = 1 | 2 | 3 | 4

const STEP_TWO_WORDS = [
  'rentang',
  'tanggal',
  'alasan',
  'keperluan',
  'keluhan',
  'no. hp',
  'nomor hp',
  'alamat',
  'tugas',
  'faskes',
  ...Object.values(LETTER_FIELDS).flatMap((t) => [t.reasonLabel, ...t.fields.map((f) => f.label)]),
].map((w) => w.toLowerCase())
const STEP_THREE_WORDS = ['surat', 'lampiran', 'dokumen', 'unggah']

/**
 * Langkah wizard yang harus dibuka ulang setelah pengiriman ditolak server: 409 (bentrok tanggal) dan 400 soal
 * isian/tanggal ke langkah 2, 400 soal surat atau lampiran ke langkah 3, selain itu tetap di pratinjau (4).
 */
export function wizardStepForError(error: unknown): WizardStep {
  if (!(error instanceof ApiError)) {
    return 4
  }
  if (error.status === 409) {
    return 2
  }
  if (error.status === 400) {
    const message = error.message.toLowerCase()
    if (STEP_TWO_WORDS.some((w) => message.includes(w))) {
      return 2
    }
    if (STEP_THREE_WORDS.some((w) => message.includes(w))) {
      return 3
    }
  }
  return 4
}

/** Isian surat yang namanya disebut pesan galat; dipakai untuk menaruh pesan di bawah isian yang bersangkutan. */
export function letterFieldForError(message: string, template: LetterTemplate): LetterFieldKey | 'reason' | null {
  const text = message.toLowerCase()
  const config = LETTER_FIELDS[template]
  const field = config.fields.find((f) => text.includes(f.label.toLowerCase()) || (f.key === 'contactPhone' && text.includes('no. hp')))
  if (field) {
    return field.key
  }
  if (text.includes(config.reasonLabel.toLowerCase()) || text.includes('alasan')) {
    return 'reason'
  }
  return null
}
