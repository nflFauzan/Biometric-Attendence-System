import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { Badge } from '@/components/ui/badge'
import { LETTER_TEMPLATE_LABELS } from '@/lib/leave'
import type { LeaveType, LetterTemplate } from '@/lib/types'

const PAID_OPTIONS = [
  { value: '', label: 'Belum ditentukan' },
  { value: 'true', label: 'Dibayar' },
  { value: 'false', label: 'Tidak dibayar' },
]

const TEMPLATE_OPTIONS = (Object.keys(LETTER_TEMPLATE_LABELS) as LetterTemplate[]).map((value) => ({
  value,
  label: LETTER_TEMPLATE_LABELS[value],
}))

function paidLabel(paid: boolean | null): string {
  return paid == null ? 'Belum ditentukan' : paid ? 'Dibayar' : 'Tidak dibayar'
}

/** Jenis izin/cuti diatur Owner (K-070). Tidak dihapus; nonaktifkan bila tidak dipakai lagi. */
export function LeaveTypesPage() {
  return (
    <CrudPage<LeaveType>
      title="Jenis Izin/Cuti"
      description="Atur jenis yang bisa dipilih karyawan. &quot;Dibayar&quot; baru dipakai saat penggajian (Fase 5); kuota belum diterapkan."
      endpoint="/api/leave-types"
      columns={[
        { header: 'Nama', cell: (t) => t.name, role: 'primary' },
        {
          header: 'Surat',
          cell: (t) => (t.requiresDocument ? <Badge variant="warning">Wajib surat</Badge> : <span className="text-muted-foreground">Opsional</span>),
        },
        {
          header: 'Persetujuan',
          cell: (t) => (t.requiresOwnerApproval ? <Badge variant="info">HR lalu Owner</Badge> : <span className="text-muted-foreground">HR/Finance</span>),
        },
        { header: 'Templat surat', cell: (t) => LETTER_TEMPLATE_LABELS[t.letterTemplate] ?? t.letterTemplate },
        { header: 'Gaji', cell: (t) => paidLabel(t.paid) },
        { header: 'Keterangan', cell: (t) => t.description ?? '—' },
        { header: 'Status', cell: (t) => <ActiveBadge active={t.active} />, role: 'status' },
      ]}
      fields={() => [
        { name: 'name', label: 'Nama', required: true, placeholder: 'mis. Sakit, Cuti tahunan' },
        {
          name: 'requiresDocument',
          label: 'Wajib surat pendukung',
          type: 'checkbox',
          hint: 'Pengajuan tetap bisa dikirim dulu, tetapi baru bisa disetujui setelah surat diunggah.',
        },
        {
          name: 'requiresOwnerApproval',
          label: 'Butuh persetujuan Owner',
          type: 'checkbox',
          hint: 'Bila aktif, pengajuan disetujui HR/Finance lalu Owner. Perubahan tidak memengaruhi pengajuan yang sedang berjalan.',
        },
        { name: 'letterTemplate', label: 'Templat surat', type: 'select', options: TEMPLATE_OPTIONS },
        { name: 'paid', label: 'Pengaruh ke gaji', type: 'select', options: PAID_OPTIONS },
        { name: 'description', label: 'Keterangan untuk karyawan' },
        { name: 'active', label: 'Aktif', type: 'checkbox' },
      ]}
      emptyValues={{ name: '', requiresDocument: false, requiresOwnerApproval: false, letterTemplate: 'UMUM', paid: '', description: '', active: true }}
      toValues={(t) => ({
        name: t.name,
        requiresDocument: t.requiresDocument,
        requiresOwnerApproval: t.requiresOwnerApproval,
        letterTemplate: t.letterTemplate,
        paid: t.paid == null ? '' : String(t.paid),
        description: t.description ?? '',
        active: t.active,
      })}
      toBody={(v) => ({
        name: v.name,
        requiresDocument: v.requiresDocument,
        // PUT mengganti seluruh isi: kedua kolom ini harus selalu dikirim agar tidak kembali ke bawaan.
        requiresOwnerApproval: v.requiresOwnerApproval,
        letterTemplate: v.letterTemplate,
        paid: v.paid === '' ? null : v.paid === 'true',
        description: v.description || null,
        active: v.active,
      })}
    />
  )
}
