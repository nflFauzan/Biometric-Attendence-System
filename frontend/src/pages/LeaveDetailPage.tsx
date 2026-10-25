import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check, Download, FileCheck2, Paperclip, Upload, UserCheck, X } from 'lucide-react'
import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'

import { ApprovalTimeline } from '@/components/leave/ApprovalTimeline'
import { LeaveStatusBadge } from '@/components/leave/LeaveStatusBadge'
import { LetterDownloadButton } from '@/components/leave/LetterDownloadButton'
import { MyLeaveActions } from '@/components/leave/MyLeaveActions'
import { OwnerDecisionDialog } from '@/components/leave/OwnerDecisionDialog'
import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { api, ApiError, apiBlob, apiUpload } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import {
  ATTACHMENT_ACCEPT,
  evidenceExtension,
  attachmentProblem,
  formatFileSize,
  formatLeaveDays,
  formatLeaveRange,
  isPendingStatus,
  leaveApiBase,
  leavePageBase,
  letterNumber,
  LETTER_FIELDS,
  MAX_ATTACHMENTS,
  type LeaveMode,
} from '@/lib/leave'
import { saveBlob } from '@/lib/leaveLetter'
import type { ApprovalStage, LeaveRequest, LeaveUsage } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { useDashboardStream } from '@/lib/useDashboardStream'

type Decision = 'approve' | 'reject' | 'cancel'

const DECISIONS: Record<Decision, { title: string; button: string; noteLabel: string; noteRequired: boolean }> = {
  approve: { title: 'Setujui pengajuan', button: 'Setujui', noteLabel: 'Catatan (opsional)', noteRequired: false },
  reject: { title: 'Tolak pengajuan', button: 'Tolak', noteLabel: 'Alasan penolakan', noteRequired: true },
  cancel: { title: 'Batalkan pengajuan', button: 'Batalkan pengajuan', noteLabel: 'Alasan pembatalan', noteRequired: true },
}

/** Detail pengajuan izin/cuti. Owner/HR memutus (K-074); karyawan melihat status dan menambah surat. */
export function LeaveDetailPage({ mode }: { mode: LeaveMode }) {
  const { id } = useParams()
  const base = leaveApiBase(mode)
  const queryClient = useQueryClient()
  const live = mode === 'manager'
  const { data, error, isPending } = useQuery({
    queryKey: [base, 'detail', id],
    queryFn: () => api<LeaveRequest>(`${base}/${id}`),
  })
  const [decision, setDecision] = useState<Decision | null>(null)
  const [ownerDialog, setOwnerDialog] = useState(false)

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: [base] })
    void queryClient.invalidateQueries({ queryKey: ['/api/dashboard/summary'] })
    void queryClient.invalidateQueries({ queryKey: ['/api/attendance'] })
  }

  const pending = data ? isPendingStatus(data.status) : false
  // Hak memutus dihitung server dengan aturan yang sama dengan endpoint aksi (K-079 dan tahap berjalan).
  const canDecide = mode === 'manager' && !!data?.canDecide
  const canRecordOwner = mode === 'manager' && !!data?.canRecordOwnerDecision
  const canCancel = mode === 'manager' && !!data && (pending || data.status === 'DISETUJUI')

  return (
    <div className="flex flex-col gap-5">
      {live && <LiveUpdates />}
      <Link to={leavePageBase(mode)} className="inline-flex items-center gap-1 text-sm font-semibold text-primary hover:underline">
        <ArrowLeft className="size-4" />
        Kembali ke daftar
      </Link>

      {error && <ErrorAlert message={error.message} />}
      {isPending && <SkeletonLines count={5} />}

      {data && (
        <>
          <PageHeader
            title={`${data.leaveTypeName} · ${data.employeeName}`}
            description={data.startDate && data.endDate ? `${formatLeaveRange(data)} · ${formatLeaveDays(data)}` : formatLeaveRange(data)}
            actions={
              <div className="flex flex-wrap gap-2">
                {mode === 'mine' ? (
                  <MyLeaveActions request={data} onWithdraw={() => setDecision('cancel')} />
                ) : (
                  <LetterDownloadButton mode={mode} request={data} />
                )}
                {canRecordOwner && (
                  <Button className="min-h-11" onClick={() => setOwnerDialog(true)}>
                    <UserCheck />
                    Catat keputusan Owner
                  </Button>
                )}
                {canDecide && (
                  <>
                    <Button className="min-h-11" onClick={() => setDecision('approve')}>
                      <Check />
                      Setujui
                    </Button>
                    <Button variant="outline" className="min-h-11" onClick={() => setDecision('reject')}>
                      <X />
                      Tolak
                    </Button>
                  </>
                )}
                {canCancel && (
                  <Button variant="ghost" className="min-h-11" onClick={() => setDecision('cancel')}>
                    Batalkan
                  </Button>
                )}
              </div>
            }
          />

          {mode === 'manager' && data.nextStage && data.requiresDocument && data.attachments.length === 0 && (
            <ErrorAlert message={`Jenis "${data.leaveTypeName}" wajib surat pendukung. Pengajuan baru bisa disetujui setelah surat diunggah.`} />
          )}

          <div className="grid gap-4 lg:grid-cols-12">
            <section className="rounded-lg border glass lg:col-span-7">
              <div className="flex items-center justify-between border-b px-5 py-4">
                <h2 className="text-base font-semibold">Rincian pengajuan</h2>
                <LeaveStatusBadge status={data.status} size={mode === 'mine' ? 'lg' : 'sm'} />
              </div>
              <dl className="grid gap-x-6 gap-y-4 p-5 sm:grid-cols-2">
                <Item label="Karyawan">
                  {data.employeeName} <span className="text-muted-foreground">({data.employeeNumber})</span>
                </Item>
                <Item label="Cabang">{data.branchName}</Item>
                <Item label="Jenis">{data.leaveTypeName}</Item>
                <Item label="Tanggal">{formatLeaveRange(data)}</Item>
                <Item label="Jumlah hari">{formatLeaveDays(data)}</Item>
                <Item label="Nomor surat">{letterNumber(data.id)}</Item>
                {data.resubmittedFromId != null && (
                  <Item label="Pengajuan ulang dari">
                    <Link to={`${leavePageBase(mode)}/${data.resubmittedFromId}`} className="font-semibold text-primary hover:underline">
                      {letterNumber(data.resubmittedFromId)}
                    </Link>
                  </Item>
                )}
                <Item label="Diajukan">
                  {data.submittedAt ? `${formatDateTime(data.submittedAt)} oleh ${data.submittedBy ?? '-'}` : 'Belum dikirim'}
                </Item>
                <Item label={LETTER_FIELDS[data.letterTemplate]?.reasonLabel ?? 'Alasan'} wide>
                  <span className="whitespace-pre-wrap">{data.reason}</span>
                </Item>
                {(LETTER_FIELDS[data.letterTemplate]?.fields ?? [])
                  .filter((f) => data[f.key])
                  .map((f) => (
                    <Item key={f.key} label={f.label}>
                      {data[f.key]}
                    </Item>
                  ))}
                {data.decidedAt && (
                  <Item label={data.status === 'DITOLAK' ? 'Ditolak' : 'Disetujui'} wide>
                    {formatDateTime(data.decidedAt)} oleh {data.decidedBy}
                    {data.decisionNote && <span className="block whitespace-pre-wrap text-muted-foreground">“{data.decisionNote}”</span>}
                  </Item>
                )}
                {data.cancelledAt && (
                  <Item label="Dibatalkan" wide>
                    {formatDateTime(data.cancelledAt)} oleh {data.cancelledBy}
                    {data.cancelReason && <span className="block whitespace-pre-wrap text-muted-foreground">“{data.cancelReason}”</span>}
                  </Item>
                )}
              </dl>
            </section>

            <div className="flex flex-col gap-4 lg:col-span-5">
              <section className="rounded-lg border glass">
                <h2 className="border-b px-5 py-4 text-base font-semibold">Riwayat persetujuan</h2>
                <div className="p-5">
                  <ApprovalTimeline request={data} />
                </div>
                <Evidence request={data} mode={mode} />
              </section>
              <Attachments request={data} mode={mode} onUploaded={refresh} />
              {mode === 'manager' && <Usage employeeId={data.employeeId} year={Number((data.startDate ?? data.createdAt).slice(0, 4))} />}
            </div>
          </div>

          {ownerDialog && (
            <OwnerDecisionDialog
              request={data}
              onClose={() => setOwnerDialog(false)}
              onDone={() => {
                setOwnerDialog(false)
                refresh()
              }}
            />
          )}

          {decision && (
            <DecisionDialog
              decision={decision}
              mode={mode}
              requestId={data.id}
              onClose={() => setDecision(null)}
              onDone={() => {
                setDecision(null)
                refresh()
              }}
            />
          )}
        </>
      )}
    </div>
  )
}

/** Bukti keputusan Owner yang dicatat HR; hanya HR/Owner yang bisa membukanya (FR-LV-002). */
function Evidence({ request, mode }: { request: LeaveRequest; mode: LeaveMode }) {
  const [error, setError] = useState<string | null>(null)
  const withEvidence = request.approvals.filter((a) => a.hasEvidence)
  if (mode !== 'manager' || withEvidence.length === 0) {
    return null
  }
  const open = async (stage: ApprovalStage) => {
    setError(null)
    try {
      const blob = await apiBlob(`${leaveApiBase(mode)}/${request.id}/approvals/${stage}/evidence`)
      saveBlob(blob, `bukti-${letterNumber(request.id)}-${stage.toLowerCase()}${evidenceExtension(blob)}`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Gagal mengunduh bukti. Coba lagi.')
    }
  }
  return (
    <div className="border-t px-5 py-3">
      {error && <ErrorAlert message={error} className="mb-2" />}
      {withEvidence.map((a) => (
        <Button key={a.stage} size="sm" variant="ghost" className="min-h-11" onClick={() => void open(a.stage)}>
          <FileCheck2 />
          Unduh bukti tahap {a.stage === 'HR' ? 'HR' : 'Owner'}
        </Button>
      ))}
    </div>
  )
}

function LiveUpdates() {
  useDashboardStream()
  return null
}

function Item({ label, wide, children }: { label: string; wide?: boolean; children: ReactNode }) {
  return (
    <div className={wide ? 'sm:col-span-2' : undefined}>
      <dt className="label-caps text-muted-foreground">{label}</dt>
      <dd className="mt-1">{children}</dd>
    </div>
  )
}

function Attachments({ request, mode, onUploaded }: { request: LeaveRequest; mode: LeaveMode; onUploaded: () => void }) {
  const input = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const base = leaveApiBase(mode)
  const canUpload = (isPendingStatus(request.status) || request.status === 'DRAFT') && request.attachments.length < MAX_ATTACHMENTS

  const upload = async (file: File | undefined) => {
    if (!file) {
      return
    }
    const problem = attachmentProblem(file)
    if (problem) {
      setError(problem)
      return
    }
    setUploading(true)
    setError(null)
    try {
      await apiUpload(`${base}/${request.id}/attachments`, file)
      onUploaded()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Gagal mengunggah. Coba lagi.')
    } finally {
      setUploading(false)
      if (input.current) {
        input.current.value = ''
      }
    }
  }

  return (
    <section className="rounded-lg border glass">
      <div className="flex items-center justify-between gap-3 border-b px-5 py-4">
        <h2 className="text-base font-semibold">
          Surat pendukung
          {request.requiresDocument && <span className="ml-2 text-xs font-normal text-muted-foreground">(wajib)</span>}
        </h2>
        {canUpload && (
          <>
            <Button size="sm" variant="outline" disabled={uploading} onClick={() => input.current?.click()}>
              <Upload />
              {uploading ? 'Mengunggah…' : 'Unggah surat'}
            </Button>
            <input
              ref={input}
              type="file"
              accept={ATTACHMENT_ACCEPT}
              className="sr-only"
              aria-label="Pilih file surat"
              onChange={(e) => void upload(e.target.files?.[0])}
            />
          </>
        )}
      </div>
      {error && <ErrorAlert message={error} className="m-4" />}
      {request.attachments.length === 0 && <EmptyState message="Belum ada surat yang diunggah." />}
      <ul className="divide-y">
        {request.attachments.map((a) => (
          <li key={a.id} className="flex items-center justify-between gap-3 px-5 py-3">
            <div className="flex min-w-0 items-center gap-3">
              <Paperclip className="size-4 shrink-0 text-primary" />
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">{a.originalName}</p>
                <p className="text-xs text-muted-foreground">
                  {formatFileSize(a.sizeBytes)} · {formatDateTime(a.uploadedAt)} · {a.uploadedBy}
                </p>
              </div>
            </div>
            <Button size="sm" variant="ghost" asChild>
              <a href={`${base}/${request.id}/attachments/${a.id}`} download={a.originalName}>
                <Download />
                Unduh
              </a>
            </Button>
          </li>
        ))}
      </ul>
      {(isPendingStatus(request.status) || request.status === 'DRAFT') && (
        <p className="border-t px-5 py-3 text-xs text-muted-foreground">
          PDF, JPG, atau PNG, maks. 5 MB, maks. {MAX_ATTACHMENTS} file. Surat tidak bisa dihapus setelah diunggah.
        </p>
      )}
    </section>
  )
}

/** Pemakaian izin yang disetujui pada tahun yang sama, sebagai bahan pertimbangan (K-072, tanpa kuota). */
function Usage({ employeeId, year }: { employeeId: number; year: number }) {
  const { data } = useQuery({
    queryKey: ['/api/leave-requests', 'usage', employeeId, year],
    queryFn: () => api<LeaveUsage>(`/api/leave-requests/usage?employeeId=${employeeId}&year=${year}`),
  })
  return (
    <section className="rounded-lg border glass">
      <h2 className="border-b px-5 py-4 text-base font-semibold">Pemakaian tahun {year}</h2>
      {data?.types.length === 0 && <EmptyState message="Belum ada izin yang disetujui tahun ini." />}
      <ul className="divide-y">
        {data?.types.map((t) => (
          <li key={t.leaveTypeId} className="flex items-center justify-between gap-4 px-5 py-3 text-sm">
            <span>{t.leaveTypeName}</span>
            <span className="tabular-nums">
              {t.calendarDays} hari{t.workingDays != null && ` · ${t.workingDays} hari kerja`} · {t.requests}×
            </span>
          </li>
        ))}
      </ul>
      {data && data.pendingRequests > 0 && (
        <p className="border-t px-5 py-3 text-xs text-muted-foreground">{data.pendingRequests} pengajuan karyawan ini masih menunggu keputusan.</p>
      )}
      <p className="border-t px-5 py-3 text-xs text-muted-foreground">Kuota cuti belum diterapkan; angka ini hanya informasi.</p>
    </section>
  )
}

function DecisionDialog({
  decision,
  mode,
  requestId,
  onClose,
  onDone,
}: {
  decision: Decision
  mode: LeaveMode
  requestId: number
  onClose: () => void
  onDone: () => void
}) {
  const config = DECISIONS[decision]
  const noteRequired = config.noteRequired && mode === 'manager'
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setError(null)
    try {
      await api(`${leaveApiBase(mode)}/${requestId}/${decision}`, { method: 'POST', body: { note: note || null } })
      onDone()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open onOpenChange={(isOpen) => !isOpen && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">{config.title}</DialogTitle>
          <DialogDescription>Keputusan dicatat di audit log beserta nama Anda dan waktunya.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} className="flex flex-col gap-4">
          {error && <ErrorAlert message={error} />}
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="note">
              {noteRequired ? config.noteLabel : `${config.noteLabel.replace(' (opsional)', '')} (opsional)`}
              {noteRequired && <span className="font-normal text-muted-foreground">(wajib)</span>}
            </Label>
            <textarea
              id="note"
              className="min-h-24 rounded-md border border-input bg-card px-3 py-2 text-base md:text-sm"
              value={note}
              maxLength={1000}
              required={noteRequired}
              onChange={(e) => setNote(e.target.value)}
            />
          </div>
          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={onClose}>
              Kembali
            </Button>
            <Button type="submit" variant={decision === 'approve' ? 'default' : 'destructive'} disabled={saving}>
              {saving ? 'Menyimpan…' : config.button}
            </Button>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
