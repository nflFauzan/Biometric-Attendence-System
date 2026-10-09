/** Mengubah string kosong dari form menjadi null untuk dikirim ke API. */
export function emptyToNull(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value.trim() : null
}

/** Nilai select (string) menjadi id angka, atau null bila kosong. */
export function toId(value: unknown): number | null {
  return typeof value === 'string' && value !== '' ? Number(value) : null
}
