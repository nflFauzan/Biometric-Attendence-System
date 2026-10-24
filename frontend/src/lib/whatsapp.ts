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
