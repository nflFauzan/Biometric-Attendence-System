/** Mengubah string kosong dari form menjadi null untuk dikirim ke API. */
export function emptyToNull(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null
}

/** "08:00, 17.30 12:00" → ["08:00", "17:30", "12:00"]; null jika ada jam tidak valid. Titik diterima sebagai pemisah. */
export function parseScanTimes(text: string): string[] | null {
  const parts = text
    .split(/[\s,;]+/)
    .map((part) => part.trim().replace('.', ':'))
    .filter(Boolean)
  return parts.every((part) => /^([01]\d|2[0-3]):[0-5]\d$/.test(part)) ? parts : null
}

/** Nilai select (string) menjadi id angka, atau null bila kosong. */
export function toId(value: unknown): number | null {
  return typeof value === 'string' && value !== '' ? Number(value) : null
}
