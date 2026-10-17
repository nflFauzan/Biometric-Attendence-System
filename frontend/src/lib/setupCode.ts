import type { IssuedKey } from '@/lib/types'

/** Port mesin bawaan (K-060/K-086). */
const DEVICE_PORT = 8888

function base64Url(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  bytes.forEach((b) => {
    binary += String.fromCharCode(b)
  })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/**
 * Kode pengaturan alat rakitan: `BAS1.` + base64url tanpa padding dari JSON {server, device, key}
 * (docs/protokol-alat-rakitan.md §1). Disusun di browser; server tidak menyimpan alamat ini.
 */
export function buildSetupCode(server: string, deviceId: string, key: string): string {
  return `BAS1.${base64Url(JSON.stringify({ server, device: deviceId, key }))}`
}

/** Alamat dasar server tanpa garis miring di akhir, atau null jika bukan http(s) dengan host. */
export function normalizeServerUrl(input: string): string | null {
  const value = input.trim().replace(/\/+$/, '')
  if (!/^https?:\/\/[^/\s]+/.test(value)) {
    return null
  }
  return value
}

/** Isian awal alamat server: dari pengaturan server, atau alamat web ini dengan port mesin. */
export function defaultServerUrl(issued: IssuedKey): string {
  return issued.setupServerUrl ?? `http://${window.location.hostname}:${DEVICE_PORT}`
}
