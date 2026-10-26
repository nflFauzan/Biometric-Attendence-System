/** Usulan kredensial dan pesan WA untuk akun baru / reset password (K-131, K-133). */

/** Nama aplikasi di pesan; sementara sampai nama resmi diputuskan (Q-025). */
export const APP_NAME = 'Sistem Absensi Takka Steel'

export const USERNAME_DOMAIN = '@takka.id'

const PASSWORD_SUFFIX = 'takka'

/** Awalan nama yang dilewati bila masih ada kata sesudahnya (mis. Muhammad Dafa → dafa). */
const NAME_PREFIXES = new Set(['muhammad', 'muhamad', 'mohammad', 'mohamad', 'moh', 'muh', 'm', 'siti'])

const clean = (word: string) => word.toLowerCase().replace(/[^a-z0-9]/g, '')

/** Kata pertama nama setelah awalan umum, huruf kecil a-z0-9; '' bila tidak ada kata. */
export function suggestLocalPart(fullName: string): string {
  const words = fullName.split(/\s+/).map(clean).filter(Boolean)
  const index = words.findIndex((word, i) => !(NAME_PREFIXES.has(word) && i < words.length - 1))
  return index < 0 ? '' : words[index]
}

export function suggestUsername(fullName: string): string {
  const local = suggestLocalPart(fullName)
  return local ? local + USERNAME_DOMAIN : ''
}

/** `nama-ID-takka`, atau `nama-takka` untuk akun tanpa karyawan; nama = bagian username sebelum '@'. */
export function suggestPassword(username: string, employeeNumber?: string | null): string {
  const local = username.trim().split('@')[0]
  if (!local) return ''
  const number = employeeNumber?.trim()
  return number ? `${local}-${number}-${PASSWORD_SUFFIX}` : `${local}-${PASSWORD_SUFFIX}`
}

export type CredentialKind = 'created' | 'reset'

const LINE = '━'.repeat(21)

const row = (label: string, value: string) => `*${label}*`.padEnd(18) + `: ${value}`

/** Teks pesan WA (spec 2026-10-26 §6). Password hanya ada di memori halaman, tidak disimpan. */
export function credentialMessage(p: {
  kind: CredentialKind
  name: string
  username: string
  password: string
  origin: string
}): string {
  const reset = p.kind === 'reset'
  return [
    `*Halo Kak ${p.name}!*`,
    '',
    reset
      ? `Password akun *${APP_NAME}* Kakak telah direset oleh Admin.`
      : `Akun *${APP_NAME}* Kakak sudah dibuat oleh Admin.`,
    '',
    reset ? 'Berikut kredensial baru untuk masuk:' : 'Berikut kredensial untuk masuk:',
    LINE,
    row('Link Login', `${p.origin}/login`),
    row('Username', p.username),
    row(reset ? 'Password Baru' : 'Password', p.password),
    LINE,
    '',
    '*Catatan:*',
    'Silakan login menggunakan password di atas, lalu Kakak akan diminta membuat password baru pilihan sendiri.',
    '',
    '_Salam hangat,_',
    '*Admin Takka Steel*',
  ].join('\n')
}

type Values = Record<string, string | boolean>

/** Aturan usulan: [field, fungsi usulan dari seluruh isian form]. */
export type SuggestionRule = [field: string, suggest: (values: Values) => string]

export const textOf = (value: string | boolean | undefined) => String(value ?? '').trim()

/**
 * Field ikut usulan selama nilainya masih sama dengan usulan sebelumnya (belum diubah manual) dan tidak sedang
 * diketik. Aturan diterapkan berurutan, jadi usulan boleh bergantung pada hasil aturan sebelumnya.
 */
export function followSuggestions<T extends Values>(next: T, prev: T, rules: SuggestionRule[]): T {
  const result: Values = { ...next }
  for (const [field, suggest] of rules) {
    const editedNow = next[field] !== prev[field]
    if (!editedNow && textOf(prev[field]) === suggest(prev)) result[field] = suggest(result)
  }
  return result as T
}
