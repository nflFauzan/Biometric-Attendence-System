import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, ArrowRight, Check, ExternalLink, Save, Send } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'

import { ErrorAlert, SkeletonLines } from '@/components/feedback'
import { StepAttachments } from '@/components/leave/wizard/StepAttachments'
import { StepDetails } from '@/components/leave/wizard/StepDetails'
import { StepReview } from '@/components/leave/wizard/StepReview'
import { StepType } from '@/components/leave/wizard/StepType'
import { draftBody, EMPTY_FORM, formFromRequest, type WizardForm } from '@/components/leave/wizard/form'
import { PageHeader } from '@/components/PageHeader'
import { Button } from '@/components/ui/button'
import { api, ApiError } from '@/lib/api'
import { inclusiveDays, letterFieldForError, wizardStepForError, type WizardStep } from '@/lib/leave'
import { downloadLetter, letterFileName, saveBlob } from '@/lib/leaveLetter'
import type { LeaveRequest, LeaveType } from '@/lib/types'
import { cn } from '@/lib/utils'

const BASE = '/api/me/leave-requests'
const STEP_LABELS = ['Jenis', 'Tanggal & isian', 'Lampiran & tanda tangan', 'Pratinjau']
/** URL PDF yang dibuka di tab baru dilepas setelah tab sempat memuatnya. */
const REVOKE_DELAY_MS = 60_000

type Busy = 'next' | 'back' | 'draft' | 'submit' | 'pdf' | null

function readStep(value: string | null): WizardStep {
  const n = Number(value)
  return n >= 1 && n <= 4 ? (Math.trunc(n) as WizardStep) : 1
}

const messageOf = (e: unknown) => (e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')

/**
 * Wizard pengajuan izin 4 langkah (spec bagian 11). Langkah disimpan di URL (`?langkah=N`) supaya tetap benar
 * saat halaman dimuat ulang atau rute berganti dari /izin-saya/baru ke /izin-saya/:id/ubah. Setiap Lanjut/Kembali
 * menyimpan draft.
 */
export function LeaveWizardPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const step: WizardStep = id ? readStep(params.get('langkah')) : 1

  const detailKey = [BASE, 'detail', id]
  const detail = useQuery({ queryKey: detailKey, queryFn: () => api<LeaveRequest>(`${BASE}/${id}`), enabled: !!id })
  const types = useQuery({ queryKey: ['/api/leave-types'], queryFn: () => api<LeaveType[]>('/api/leave-types') })
  const activeTypes = types.data?.filter((t) => t.active)

  const [form, setForm] = useState<WizardForm>(EMPTY_FORM)
  const hydrated = useRef<string | null>(null)
  const [busy, setBusy] = useState<Busy>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  // Isian diambil dari draft sekali saja; sesudahnya yang diketik pengguna tidak ditimpa data server.
  useEffect(() => {
    if (id && detail.data && hydrated.current !== id) {
      hydrated.current = id
      setForm(formFromRequest(detail.data))
    }
  }, [id, detail.data])

  const type = activeTypes?.find((t) => t.id === form.leaveTypeId)
  const template = type?.letterTemplate ?? detail.data?.letterTemplate ?? 'UMUM'

  const patch = (change: Partial<WizardForm>) => {
    setForm((current) => ({ ...current, ...change }))
    setFieldErrors({})
  }

  const goTo = (target: number) => setParams({ langkah: String(target) })

  const remember = (request: LeaveRequest) => queryClient.setQueryData([BASE, 'detail', String(request.id)], request)

  /** Simpan draft: POST bila belum ada, selain itu PUT. */
  const save = async (): Promise<LeaveRequest> => {
    if (!id) {
      const created = await api<LeaveRequest>(BASE, { method: 'POST', body: { leaveTypeId: form.leaveTypeId, draft: true } })
      remember(created)
      return created
    }
    const updated = await api<LeaveRequest>(`${BASE}/${id}`, { method: 'PUT', body: draftBody(form, template) })
    remember(updated)
    return updated
  }

  const run = async (kind: Exclude<Busy, null>, action: () => Promise<void>) => {
    setBusy(kind)
    setError(null)
    setFieldErrors({})
    try {
      await action()
    } catch (e) {
      setError(messageOf(e))
    } finally {
      setBusy(null)
    }
  }

  const next = () =>
    run('next', async () => {
      if (step === 2 && form.startDate && form.endDate && inclusiveDays(form.startDate, form.endDate) == null) {
        setFieldErrors({ endDate: 'Tanggal selesai harus sama atau setelah tanggal mulai' })
        return
      }
      const saved = await save()
      if (!id) {
        // Rute berganti dan halaman dibuat ulang; langkah ikut di URL.
        navigate(`/izin-saya/${saved.id}/ubah?langkah=2`, { replace: true })
        return
      }
      goTo(step + 1)
    })

  /** Kembali selalu berpindah langkah; bila simpan gagal, isian dipertahankan dan galat tampil di langkah sebelumnya. */
  const back = async () => {
    if (!id || step <= 1) return
    setBusy('back')
    setError(null)
    setFieldErrors({})
    let failure: string | null = null
    try {
      await save()
    } catch (e) {
      failure = messageOf(e)
    }
    goTo(step - 1)
    setError(failure)
    setBusy(null)
  }

  const saveDraft = () =>
    run('draft', async () => {
      const saved = await save()
      void queryClient.invalidateQueries({ queryKey: [BASE] })
      navigate(`/izin-saya/${saved.id}`)
    })

  const viewPdf = () =>
    run('pdf', async () => {
      const saved = await save()
      const blob = await downloadLetter('mine', saved.id)
      const url = URL.createObjectURL(blob)
      const opened = window.open(url, '_blank')
      if (!opened) {
        // Popup diblokir: simpan sebagai berkas.
        saveBlob(blob, letterFileName(saved))
      }
      window.setTimeout(() => URL.revokeObjectURL(url), REVOKE_DELAY_MS)
    })

  const submit = async () => {
    setBusy('submit')
    setError(null)
    setFieldErrors({})
    try {
      const saved = await save()
      const sent = await api<LeaveRequest>(`${BASE}/${saved.id}/submit`, { method: 'POST' })
      remember(sent)
      void queryClient.invalidateQueries({ queryKey: [BASE] })
      void queryClient.invalidateQueries({ queryKey: ['/api/dashboard/summary'] })
      navigate(`/izin-saya/${sent.id}`)
    } catch (e) {
      const target = wizardStepForError(e)
      const message = messageOf(e)
      const field = target === 2 ? letterFieldForError(message, template) : null
      if (field) {
        setFieldErrors({ [field]: message })
      } else {
        setError(message)
      }
      if (target !== step) goTo(target)
    } finally {
      setBusy(null)
    }
  }

  const loading = !!id && detail.isPending
  const request = detail.data
  const notDraft = request && request.status !== 'DRAFT'
  const canNext = step === 1 ? form.leaveTypeId != null : true
  const disabled = busy != null

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-5">
      <Link to="/izin-saya" className="inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-primary hover:underline">
        <ArrowLeft className="size-4" />
        Kembali ke daftar
      </Link>
      <PageHeader title={id ? 'Ubah draft pengajuan' : 'Ajukan izin'} description="Isi empat langkah. Draft tersimpan setiap kali Anda berpindah langkah." />

      <StepIndicator step={step} />

      {detail.error && <ErrorAlert message={detail.error.message} />}
      {types.error && <ErrorAlert message={types.error.message} />}
      {loading && <SkeletonLines count={4} />}

      {notDraft && (
        <div className="flex flex-col gap-3">
          <ErrorAlert message="Hanya draft yang bisa diubah. Pengajuan ini sudah dikirim." />
          <Link to={`/izin-saya/${request.id}`} className="w-fit text-sm font-semibold text-primary hover:underline">
            Lihat pengajuan
          </Link>
        </div>
      )}

      {!loading && !notDraft && !detail.error && (
        <>
          {error && <ErrorAlert message={error} />}
          <div key={step} className="motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-right-4 motion-safe:duration-200">
            {step === 1 && <StepType types={activeTypes} value={form.leaveTypeId} onChange={(typeId) => patch({ leaveTypeId: typeId })} />}
            {step === 2 && <StepDetails form={form} template={template} errors={fieldErrors} onChange={patch} />}
            {step === 3 && request && <StepAttachments request={request} />}
            {step === 4 && request && <StepReview form={form} type={type} template={template} request={request} />}
          </div>

          <div className="flex flex-col-reverse gap-2 pt-2 sm:flex-row sm:flex-wrap sm:justify-between">
            {step === 1 ? (
              <Button asChild variant="outline" className="min-h-11">
                <Link to="/izin-saya">Batal</Link>
              </Button>
            ) : (
              <Button type="button" variant="outline" className="min-h-11" disabled={disabled} onClick={() => void back()}>
                <ArrowLeft />
                Kembali
              </Button>
            )}
            {step < 4 ? (
              <Button type="button" className="min-h-11" disabled={disabled || !canNext} onClick={() => void next()}>
                {busy === 'next' ? 'Menyimpan…' : 'Lanjut'}
                <ArrowRight />
              </Button>
            ) : (
              <div className="flex flex-col-reverse gap-2 sm:flex-row">
                <Button type="button" variant="outline" className="min-h-11" disabled={disabled} onClick={() => void viewPdf()}>
                  <ExternalLink />
                  {busy === 'pdf' ? 'Menyiapkan…' : 'Lihat PDF'}
                </Button>
                <Button type="button" variant="outline" className="min-h-11" disabled={disabled} onClick={() => void saveDraft()}>
                  <Save />
                  {busy === 'draft' ? 'Menyimpan…' : 'Simpan draft'}
                </Button>
                <Button type="button" className="min-h-11" disabled={disabled} onClick={() => void submit()}>
                  <Send />
                  {busy === 'submit' ? 'Mengirim…' : 'Kirim'}
                </Button>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}

function StepIndicator({ step }: { step: number }) {
  return (
    <nav aria-label="Langkah pengajuan">
      <ol className="flex items-center gap-2">
        {STEP_LABELS.map((label, index) => {
          const number = index + 1
          const done = number < step
          const current = number === step
          return (
            <li key={label} aria-current={current ? 'step' : undefined} className="flex min-w-0 flex-1 flex-col gap-1.5">
              <span className={cn('h-1 rounded-full', done || current ? 'bg-primary' : 'bg-steel-200 dark:bg-secondary')} />
              <span className={cn('flex items-center gap-1.5 text-xs font-semibold', current ? 'text-foreground' : 'text-muted-foreground')}>
                <span
                  aria-hidden="true"
                  className={cn('grid size-5 shrink-0 place-items-center rounded-full text-[11px]', done || current ? 'bg-primary text-primary-foreground' : 'bg-steel-100 dark:bg-secondary')}
                >
                  {done ? <Check className="size-3" /> : number}
                </span>
                <span className={cn('truncate', !current && 'max-sm:sr-only')}>{label}</span>
              </span>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
