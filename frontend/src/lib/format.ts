const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'Mei', 'Jun', 'Jul', 'Agu', 'Sep', 'Okt', 'Nov', 'Des']

/** "2026-10-09" → "9 Okt 2026". Diurai manual agar tanggal tidak bergeser karena zona waktu browser. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!match) {
    return iso
  }
  return `${Number(match[3])} ${MONTHS[Number(match[2]) - 1]} ${match[1]}`
}

/** Waktu kejadian (instant) → "9 Okt 2026, 08.14". */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  return date.toLocaleString('id-ID', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/** Tanggal lokal perangkat sebagai "YYYY-MM-DD" (bukan UTC), untuk nilai awal input tanggal. */
export function localDateIso(date: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** Jam dari API ("08:00" atau "08:00:30") → "08.00" atau "08.00.30". */
export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return '—'
  }
  const [hours, minutes, seconds] = value.split(':')
  return seconds && Number(seconds) !== 0 ? `${hours}.${minutes}.${seconds.slice(0, 2)}` : `${hours}.${minutes}`
}

/** Durasi dalam detik → "2 jam 5 menit". Detik ditampilkan bila ada, karena durasi tidak dibulatkan (K-052). */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) {
    return '—'
  }
  if (seconds === 0) {
    return '0 menit'
  }
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  const parts = []
  if (hours) parts.push(`${hours} jam`)
  if (minutes) parts.push(`${minutes} menit`)
  if (rest) parts.push(`${rest} detik`)
  return parts.join(' ')
}

/** Nominal rupiah: 80000 → "Rp80.000". Desimal hanya tampil bila ada. */
export function formatRupiah(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return new Intl.NumberFormat('id-ID', {
    style: 'currency',
    currency: 'IDR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  })
    .format(value)
    .replace(/\s/g, '')
}

/** Inisial untuk avatar: "Budi Santoso" → "BS". */
export function initials(name: string | null | undefined): string {
  const parts = (name ?? '').trim().split(/\s+/).filter(Boolean)
  return parts
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join('')
}
