import type { Tone } from '@/lib/attendance'
import type { TeamStatus } from '@/lib/types'

/** Label status rekan kerja (K-084). Izin sengaja tanpa jenis. */
export const TEAM_STATUS: Record<TeamStatus, { label: string; tone: Tone }> = {
  HADIR: { label: 'Hadir', tone: 'success' },
  TIDAK_LENGKAP: { label: 'Belum lengkap', tone: 'warning' },
  TIDAK_HADIR: { label: 'Tidak hadir', tone: 'danger' },
  IZIN: { label: 'Izin', tone: 'info' },
  LIBUR: { label: 'Libur', tone: 'neutral' },
  BELUM_DIATUR: { label: 'Belum diatur', tone: 'neutral' },
}

const MONTHS = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember']

const pad = (n: number) => String(n).padStart(2, '0')

/** "2026-10-14" → "2026-10". */
export function monthOf(iso: string): string {
  return iso.slice(0, 7)
}

export function shiftMonth(month: string, delta: number): string {
  const [year, m] = month.split('-').map(Number)
  const index = year * 12 + (m - 1) + delta
  return `${Math.floor(index / 12)}-${pad((index % 12) + 1)}`
}

/** Rentang satu bulan; bulan berjalan dipotong di hari ini agar hari mendatang tidak tampil "Tidak hadir". */
export function monthRange(month: string, today: string): { from: string; to: string } {
  const [year, m] = month.split('-').map(Number)
  const lastDay = new Date(Date.UTC(year, m, 0)).getUTCDate()
  const end = `${month}-${pad(lastDay)}`
  return { from: `${month}-01`, to: end > today ? today : end }
}

export function monthLabel(month: string): string {
  const [year, m] = month.split('-').map(Number)
  return `${MONTHS[m - 1]} ${year}`
}
