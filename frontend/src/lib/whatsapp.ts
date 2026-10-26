import { formatLeaveRange, letterNumber } from '@/lib/leave'
import type { LeaveRequest } from '@/lib/types'

/** Tautan wa.me dengan pesan permohonan yang diteruskan karyawan ke HR/Owner (spec bagian 10). */
export function buildWhatsAppUrl(
  request: Pick<LeaveRequest, 'id' | 'leaveTypeName' | 'employeeName' | 'startDate' | 'endDate'>,
  origin: string,
): string {
  const text = `Permohonan ${request.leaveTypeName} ${letterNumber(request.id)} a.n. ${request.employeeName}, ${formatLeaveRange(request)}. Mohon ditinjau: ${origin}/izin/${request.id}`
  return `https://wa.me/?text=${encodeURIComponent(text)}`
}

/** Nomor HP ke format internasional wa.me: 08… → 628…, 8… → 628…, 62… tetap; null bila tidak ada angka. */
export function normalizeWhatsAppNumber(phone: string | null | undefined): string | null {
  const digits = (phone ?? '').replace(/\D/g, '')
  if (!digits) return null
  if (digits.startsWith('0')) return '62' + digits.slice(1)
  if (digits.startsWith('8')) return '62' + digits
  return digits
}

/** Tautan wa.me berisi pesan; tanpa nomor, pengguna memilih kontak sendiri di WhatsApp. */
export function whatsAppSendUrl(text: string, phone?: string | null): string {
  const number = normalizeWhatsAppNumber(phone)
  return `https://wa.me/${number ?? ''}?text=${encodeURIComponent(text)}`
}
