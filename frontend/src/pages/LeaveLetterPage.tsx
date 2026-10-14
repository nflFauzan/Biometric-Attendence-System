import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Printer } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'

import { ErrorAlert, SkeletonLines } from '@/components/feedback'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { formatDate, formatDateTime, localDateIso } from '@/lib/format'
import { formatLeaveDays, formatLeaveRange, LEAVE_STATUS_LABELS, leaveApiBase, leavePageBase, type LeaveMode } from '@/lib/leave'
import type { LeaveRequest } from '@/lib/types'

/** Nomor tampilan surat dari ID pengajuan, mis. IZ-000042. */
function letterNumber(id: number): string {
  return `IZ-${String(id).padStart(6, '0')}`
}

/**
 * Surat permohonan izin/cuti siap cetak (K-082). Dicetak lewat fitur cetak browser; pilih "Simpan sebagai PDF"
 * untuk mendapatkan file PDF. Format kop masih umum dan bisa disesuaikan setelah ada contoh dari perusahaan.
 */
export function LeaveLetterPage({ mode }: { mode: LeaveMode }) {
  const { id } = useParams()
  const base = leaveApiBase(mode)
  const { data, error, isPending } = useQuery({
    queryKey: [base, 'detail', id],
    queryFn: () => api<LeaveRequest>(`${base}/${id}`),
  })

  return (
    <div className="min-h-dvh bg-steel-100 py-6 print:bg-white print:py-0 dark:bg-background">
      <style>{'@page { size: A4; margin: 18mm; }'}</style>
      <div className="mx-auto mb-4 flex max-w-[210mm] flex-wrap items-center justify-between gap-3 px-4 print:hidden">
        <Link
          to={`${leavePageBase(mode)}/${id}`}
          className="inline-flex items-center gap-1 text-sm font-semibold text-primary hover:underline"
        >
          <ArrowLeft className="size-4" />
          Kembali ke pengajuan
        </Link>
        <div className="flex items-center gap-3">
          <p className="hidden text-xs text-muted-foreground sm:block">Untuk PDF, pilih tujuan "Simpan sebagai PDF".</p>
          <Button onClick={() => window.print()} disabled={!data}>
            <Printer />
            Cetak / Simpan PDF
          </Button>
        </div>
      </div>

      {error && <ErrorAlert message={error.message} className="mx-auto max-w-[210mm]" />}
      {isPending && <SkeletonLines count={6} />}
      {data && <Letter request={data} />}
    </div>
  )
}

function Letter({ request }: { request: LeaveRequest }) {
  // Waktu cetak diambil sekali saat surat pertama kali ditampilkan.
  const [printedAt] = useState(() => new Date().toISOString())
  const approved = request.status === 'DISETUJUI'
  return (
    <article className="mx-auto max-w-[210mm] bg-white px-[18mm] py-[16mm] text-[11pt] leading-relaxed text-black shadow-sm print:max-w-none print:p-0 print:shadow-none">
      <header className="flex items-end justify-between border-b-2 border-black pb-3">
        <div>
          <p className="font-heading text-xl font-extrabold tracking-wide">TAKKA STEEL</p>
          <p className="text-[9pt]">{request.branchName}</p>
        </div>
        <p className="text-right text-[9pt]">
          No. {letterNumber(request.id)}
          <br />
          Status: {LEAVE_STATUS_LABELS[request.status]}
        </p>
      </header>

      <h1 className="mt-6 text-center text-[13pt] font-bold tracking-wide uppercase underline underline-offset-4">
        Surat Permohonan Izin / Cuti
      </h1>

      <p className="mt-6">Yang bertanda tangan di bawah ini:</p>
      <table className="mt-2 w-full">
        <tbody>
          <Row label="Nama">{request.employeeName}</Row>
          <Row label="No. karyawan">{request.employeeNumber}</Row>
          <Row label="Jabatan">{request.position || '—'}</Row>
          <Row label="Cabang">{request.branchName}</Row>
        </tbody>
      </table>

      <p className="mt-4">
        dengan ini mengajukan permohonan izin/cuti jenis <strong>{request.leaveTypeName}</strong> selama{' '}
        <strong>{formatLeaveDays(request)}</strong>, yaitu pada <strong>{formatLeaveRange(request)}</strong>, dengan alasan:
      </p>
      <p className="mt-2 rounded border border-black/30 px-3 py-2 whitespace-pre-wrap">{request.reason}</p>

      {request.attachments.length > 0 && (
        <p className="mt-3 text-[10pt]">
          Lampiran: {request.attachments.map((a) => a.originalName).join(', ')}.
        </p>
      )}

      <p className="mt-4">Demikian permohonan ini saya sampaikan. Atas perhatian dan persetujuannya saya ucapkan terima kasih.</p>

      <div className="mt-10 grid grid-cols-2 gap-10 text-center">
        <div>
          <p>Pemohon,</p>
          <p className="text-[9pt]">{formatDate(localDateIso(new Date(request.submittedAt)))}</p>
          <div className="h-20" />
          <p className="border-t border-black pt-1 font-semibold">{request.employeeName}</p>
        </div>
        <div>
          <p>{approved ? 'Disetujui oleh,' : 'Mengetahui / Menyetujui,'}</p>
          <p className="text-[9pt]">{approved && request.decidedAt ? formatDate(localDateIso(new Date(request.decidedAt))) : ' '}</p>
          <div className="h-20" />
          <p className="border-t border-black pt-1 font-semibold">{approved ? request.decidedBy : 'Owner / HR-Finance'}</p>
        </div>
      </div>

      {request.status === 'DITOLAK' && (
        <p className="mt-6 text-[10pt]">
          Pengajuan ini <strong>ditolak</strong> pada {formatDateTime(request.decidedAt)} oleh {request.decidedBy}
          {request.decisionNote ? `: ${request.decisionNote}` : '.'}
        </p>
      )}
      {request.status === 'DIBATALKAN' && (
        <p className="mt-6 text-[10pt]">
          Pengajuan ini <strong>dibatalkan</strong> pada {formatDateTime(request.cancelledAt)} oleh {request.cancelledBy}
          {request.cancelReason ? `: ${request.cancelReason}` : '.'}
        </p>
      )}

      <footer className="mt-10 border-t border-black/30 pt-2 text-[8pt] text-black/60">
        Dibuat dari sistem absensi pada {formatDateTime(printedAt)}. Diajukan {formatDateTime(request.submittedAt)} oleh{' '}
        {request.submittedBy}
        {approved && request.decidedAt ? `; disetujui ${formatDateTime(request.decidedAt)} oleh ${request.decidedBy}` : ''}.
      </footer>
    </article>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <tr>
      <td className="w-40 py-0.5 align-top">{label}</td>
      <td className="w-4 py-0.5 align-top">:</td>
      <td className="py-0.5">{children}</td>
    </tr>
  )
}
