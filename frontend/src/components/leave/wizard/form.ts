import { LETTER_FIELDS, type LetterFieldKey } from '@/lib/leave'
import type { LeaveRequest, LetterTemplate } from '@/lib/types'

/** Isian wizard; teks kosong berarti belum diisi. Draft boleh belum lengkap, server memvalidasi saat Kirim. */
export interface WizardForm {
  leaveTypeId: number | null
  startDate: string
  endDate: string
  reason: string
  extra: Record<LetterFieldKey, string>
}

export const EMPTY_FORM: WizardForm = {
  leaveTypeId: null,
  startDate: '',
  endDate: '',
  reason: '',
  extra: { contactPhone: '', leaveAddress: '', delegateTo: '', healthFacility: '' },
}

export function formFromRequest(request: LeaveRequest): WizardForm {
  return {
    leaveTypeId: request.leaveTypeId,
    startDate: request.startDate ?? '',
    endDate: request.endDate ?? '',
    reason: request.reason ?? '',
    extra: {
      contactPhone: request.contactPhone ?? '',
      leaveAddress: request.leaveAddress ?? '',
      delegateTo: request.delegateTo ?? '',
      healthFacility: request.healthFacility ?? '',
    },
  }
}

/** Badan PUT draft: hanya isian milik templat jenis terpilih yang dikirim, lainnya dikosongkan. */
export function draftBody(form: WizardForm, template: LetterTemplate) {
  const allowed = new Set<LetterFieldKey>(LETTER_FIELDS[template].fields.map((f) => f.key))
  const extra = Object.fromEntries(
    (Object.keys(form.extra) as LetterFieldKey[]).map((key) => [key, allowed.has(key) ? form.extra[key].trim() || null : null]),
  )
  return {
    leaveTypeId: form.leaveTypeId,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    reason: form.reason.trim() || null,
    ...extra,
    draft: true,
  }
}
