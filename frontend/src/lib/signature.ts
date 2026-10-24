import { api, apiBlob, apiForm } from '@/lib/api'

export interface SignatureStatus {
  exists: boolean
  updatedAt: string | null
}

export const SIGNATURE_STATUS_KEY = ['/api/me/signature/status']

export function fetchSignatureStatus(): Promise<SignatureStatus> {
  return api<SignatureStatus>('/api/me/signature/status')
}

/** Gambar tanda tangan sendiri (PNG) sebagai Blob; 404 bila belum ada. */
export function fetchSignatureImage(): Promise<Blob> {
  return apiBlob('/api/me/signature')
}

/** Simpan tanda tangan (PNG/JPG, maks. 1 MB) sebagai multipart bagian "file". */
export function uploadSignature(blob: Blob): Promise<SignatureStatus> {
  const form = new FormData()
  form.append('file', blob, blob.type === 'image/jpeg' ? 'tanda-tangan.jpg' : 'tanda-tangan.png')
  return apiForm<SignatureStatus>('/api/me/signature', form, 'PUT')
}

export function deleteSignature(): Promise<void> {
  return api<void>('/api/me/signature', { method: 'DELETE' })
}
