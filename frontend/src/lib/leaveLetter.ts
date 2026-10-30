import { apiBlob } from '@/lib/api'
import { saveBlob } from '@/lib/download'
import { leaveApiBase, letterNumber, type LeaveMode } from '@/lib/leave'
import type { LeaveRequest } from '@/lib/types'

/** Ambil PDF surat dari server. Pemilik memakai /api/me, Owner/HR memakai endpoint manajer. */
export function downloadLetter(mode: LeaveMode, id: number): Promise<Blob> {
  return apiBlob(`${leaveApiBase(mode)}/${id}/letter.pdf`)
}

/** Tetap diekspor dari sini agar pemakai lama tidak berubah. */
export { saveBlob }

export function letterFileName(request: Pick<LeaveRequest, 'id'>): string {
  return `${letterNumber(request.id)}.pdf`
}

export async function saveLetter(mode: LeaveMode, request: Pick<LeaveRequest, 'id'>): Promise<void> {
  saveBlob(await downloadLetter(mode, request.id), letterFileName(request))
}

/**
 * Bagikan PDF lewat lembar berbagi bawaan HP (mis. ke WhatsApp). Bila browser tidak bisa berbagi file,
 * PDF diunduh. Pengguna yang menutup lembar berbagi tidak dianggap galat.
 */
export async function shareLetter(mode: LeaveMode, request: Pick<LeaveRequest, 'id'>): Promise<'shared' | 'downloaded'> {
  const blob = await downloadLetter(mode, request.id)
  const fileName = letterFileName(request)
  const file = new File([blob], fileName, { type: 'application/pdf' })
  if (navigator.canShare?.({ files: [file] }) && navigator.share) {
    try {
      await navigator.share({ files: [file], title: letterNumber(request.id) })
      return 'shared'
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        return 'shared'
      }
      // Berbagi gagal selain dibatalkan: lanjut ke unduhan.
    }
  }
  saveBlob(blob, fileName)
  return 'downloaded'
}
