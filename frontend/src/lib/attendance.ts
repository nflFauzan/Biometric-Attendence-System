import type { AttendanceStatus, CalculationFlag, DayRecord, ReviewStatus, Scan, ScanFlag } from '@/lib/types'
import { isPendingStatus } from '@/lib/leave'

export const STATUS_LABELS: Record<AttendanceStatus, string> = {
  LENGKAP: 'Lengkap',
  TIDAK_LENGKAP: 'Tidak lengkap',
  TIDAK_HADIR: 'Tidak hadir',
  TIDAK_ADA_SCAN: 'Tidak ada scan',
  IZIN: 'Izin',
}

export const SCAN_FLAG_LABELS: Record<ScanFlag, string> = {
  PIN_TIDAK_DIKENAL: 'PIN belum dipetakan ke karyawan',
  WAKTU_DI_MASA_DEPAN: 'Waktu scan di masa depan, jam mesin mungkin salah',
  CABANG_BERBEDA: 'Scan di cabang tambahan',
  CABANG_TIDAK_DIIZINKAN: 'Cabang tidak diizinkan',
}

export const CALCULATION_FLAG_LABELS: Record<CalculationFlag, string> = {
  ISTIRAHAT_BELUM_DIATUR: 'Istirahat belum diatur',
  PERLU_TINJAUAN_HARI_LIBUR: 'Hadir di hari libur, perlu ditinjau',
  URUTAN_TIDAK_WAJAR: 'Jam pulang tidak setelah jam masuk',
  HADIR_SAAT_IZIN: 'Ada scan padahal izin disetujui, perlu ditinjau',
  IZIN_MENUNGGU: 'Ada pengajuan izin yang belum diputus',
  ADA_SCAN_TERTAHAN: 'Ada scan tertahan',
}

export const REVIEW_STATUS_LABELS: Record<ReviewStatus, string> = {
  BELUM_DITINJAU: 'Belum ditinjau',
  DITERIMA: 'Diterima',
  DIABAIKAN: 'Diabaikan',
}

/** Label scan yang tidak ikut rekap karena tertahan (K-103): "Tertahan" bila belum ditinjau, "Diabaikan" bila ditolak. */
export function heldScanLabel(scan: Scan): string | null {
  if (scan.counted) return null
  return scan.review === 'DIABAIKAN' ? 'Diabaikan' : 'Tertahan'
}

export type Tone = 'success' | 'warning' | 'danger' | 'neutral' | 'info'

/** Status ringkas satu hari untuk badge (DASH-007: valid, belum lengkap, atau perlu pemeriksaan). */
export function dayStatus(day: DayRecord): { label: string; tone: Tone } {
  const approvedLeave = day.leave?.status === 'DISETUJUI' ? day.leave : null
  // Izin disetujui tanpa kehadiran tetap tampil walau aturan kerja belum diatur (K-076).
  if (approvedLeave && (day.result?.status === 'IZIN' || (!day.result && !day.scans.some((s) => s.counted) && !day.correction))) {
    return { label: `Izin: ${approvedLeave.leaveTypeName}`, tone: 'info' }
  }
  if (day.ruleMissing) {
    return { label: 'Aturan belum diatur', tone: 'danger' }
  }
  const status = day.result?.status
  if (!status) {
    return { label: '—', tone: 'neutral' }
  }
  const tone: Tone =
    status === 'LENGKAP' ? 'success' : status === 'TIDAK_LENGKAP' ? 'warning' : status === 'TIDAK_HADIR' ? 'danger' : 'neutral'
  return { label: STATUS_LABELS[status], tone }
}

/** Penanda yang perlu dicek pengguna: penanda scan + penanda perhitungan selain informasi istirahat. */
export function reviewNotes(day: DayRecord): string[] {
  // CABANG_BERBEDA hanya informasi (label per scan sudah menampilkannya), bukan catatan tinjauan.
  const notes = day.scanFlags.filter((flag) => flag !== 'CABANG_BERBEDA').map((flag) => SCAN_FLAG_LABELS[flag])
  day.result?.flags
    .filter((flag) => flag !== 'ISTIRAHAT_BELUM_DIATUR')
    .forEach((flag) => notes.push(CALCULATION_FLAG_LABELS[flag]))
  if (!day.result && day.leave && isPendingStatus(day.leave.status)) {
    notes.push(CALCULATION_FLAG_LABELS.IZIN_MENUNGGU)
  }
  return notes
}
