import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fingerprint, Trash2 } from 'lucide-react'
import { useCallback, useState } from 'react'

import { EnrollDialog } from '@/components/EnrollDialog'
import { ErrorAlert, SkeletonLines } from '@/components/feedback'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { Deletion, Employee, FingerprintOverview } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { type StreamEvent, useDashboardStream } from '@/lib/useDashboardStream'

const FINGERS = [1, 2] as const

/**
 * Bagian "Sidik Jari" di Detail Karyawan (spec §3.2, §17.8): persetujuan karyawan (K-091), status jari ke-1 dan
 * ke-2, pendaftaran dan penghapusan lewat alat rakitan. Template tidak pernah tampil di web.
 */
export function FingerprintSection({ employee }: { employee: Employee }) {
  const queryClient = useQueryClient()
  const queryKey = ['/api/employees', String(employee.id), 'fingerprints']
  const overview = useQuery({
    queryKey,
    queryFn: () => api<FingerprintOverview>(`/api/employees/${employee.id}/fingerprints`),
  })
  const [enrolling, setEnrolling] = useState<{ finger: number; commandId?: string } | null>(null)
  const [confirmFinger, setConfirmFinger] = useState<number | null>(null)

  const consent = useMutation({
    mutationFn: () => api<FingerprintOverview>(`/api/employees/${employee.id}/fingerprint-consent`, { method: 'POST' }),
    onSuccess: (data) => queryClient.setQueryData(queryKey, data),
  })

  const [deleteFinger, setDeleteFinger] = useState<number | null>(null)

  const retry = useMutation({
    mutationFn: (commandId: string) => api(`/api/device-commands/${commandId}/retry`, { method: 'POST' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey }),
  })

  const remove = useMutation({
    mutationFn: (finger: number) =>
      api<FingerprintOverview>(`/api/employees/${employee.id}/fingerprints/${finger}`, { method: 'DELETE' }),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKey, updated)
      setDeleteFinger(null)
    },
  })

  // Status penghapusan berubah saat mesin melapor; ambil ulang hanya untuk perintah milik karyawan ini.
  const deletionIds = (overview.data?.deletions ?? []).map((d) => d.commandId).join(',')
  const onEvent = useCallback(
    (event: StreamEvent) => {
      if (event.type === 'DEVICE_COMMAND' && event.commandId && deletionIds.split(',').includes(event.commandId)) {
        void queryClient.invalidateQueries({ queryKey: ['/api/employees', String(employee.id), 'fingerprints'] })
      }
    },
    [deletionIds, queryClient, employee.id],
  )
  useDashboardStream(onEvent)

  const data = overview.data
  const consented = Boolean(data?.consentAt)
  // Urutan sama dengan pemeriksaan di server.
  const blocked =
    !employee.active || employee.endDate
      ? 'Karyawan nonaktif.'
      : !employee.devicePin
        ? 'Isi PIN mesin karyawan dulu.'
        : !consented
          ? 'Centang persetujuan karyawan dulu.'
          : null

  return (
    <section className="rounded-lg border glass lg:col-span-3" aria-labelledby="sidik-jari">
      <h2 id="sidik-jari" className="flex items-center gap-2 border-b px-5 py-3.5 text-base font-semibold">
        <Fingerprint className="size-4 text-muted-foreground" aria-hidden="true" />
        Sidik Jari
      </h2>
      {overview.isPending && <SkeletonLines count={3} />}
      {overview.error && <ErrorAlert className="m-4" message={overview.error.message} />}
      {data && (
        <div className="flex flex-col divide-y">
          <div className="px-5 py-3">
            <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm font-semibold md:min-h-10">
              <input
                type="checkbox"
                className="size-4 accent-steel-600"
                checked={consented}
                disabled={consented || consent.isPending}
                onChange={() => consent.mutate()}
              />
              Karyawan sudah menyetujui penyimpanan sidik jari
            </label>
            {consented && (
              <p className="text-xs text-muted-foreground">
                Disetujui oleh {data.consentBy}, {formatDateTime(data.consentAt)}
              </p>
            )}
            {consent.error && <ErrorAlert className="mt-2" message={consent.error.message} />}
          </div>

          {FINGERS.map((finger) => {
            const record = data.fingers.find((f) => f.finger === finger)
            const active = data.activeCommands.find((c) => c.finger === finger)
            return (
              <div
                key={finger}
                role="group"
                aria-label={`Jari ke-${finger}`}
                className="flex flex-wrap items-center justify-between gap-3 px-5 py-3"
              >
                <div className="text-sm">
                  <p className="font-semibold">Jari ke-{finger}</p>
                  <p className="text-muted-foreground">
                    {active
                      ? `Sedang didaftarkan di ${active.deviceName}`
                      : record
                        ? `Terdaftar di ${record.deviceName}, ${formatDateTime(record.enrolledAt)}`
                        : 'Belum terdaftar'}
                  </p>
                </div>
                {active ? (
                  <Button size="sm" variant="secondary" onClick={() => setEnrolling({ finger, commandId: active.id })}>
                    Lihat proses
                  </Button>
                ) : (
                  <span className="flex flex-wrap items-center gap-1">
                    {record && (
                      <Button
                        variant="ghost"
                        size="sm"
                        className="text-danger"
                        aria-label={`Hapus jari ke-${finger}`}
                        onClick={() => {
                          remove.reset()
                          setDeleteFinger(finger)
                        }}
                      >
                        <Trash2 />
                        Hapus
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant={record ? 'outline' : 'default'}
                      disabled={Boolean(blocked)}
                      onClick={() => (record ? setConfirmFinger(finger) : setEnrolling({ finger }))}
                    >
                      {record ? 'Daftar ulang' : 'Daftarkan'}
                    </Button>
                  </span>
                )}
              </div>
            )
          })}

          {blocked && <p className="px-5 py-3 text-sm text-muted-foreground">{blocked}</p>}

          {data.deletions.length > 0 && (
            <section aria-labelledby="penghapusan-di-mesin" className="px-5 py-3">
              <h3 id="penghapusan-di-mesin" className="text-sm font-semibold">
                Penghapusan di mesin
              </h3>
              <ul className="mt-2 flex flex-col gap-2">
                {data.deletions.map((d) => (
                  <DeletionRow
                    key={d.commandId}
                    deletion={d}
                    retrying={retry.isPending && retry.variables === d.commandId}
                    onRetry={() => retry.mutate(d.commandId)}
                  />
                ))}
              </ul>
              {retry.error && <ErrorAlert className="mt-2" message={retry.error.message} />}
            </section>
          )}

          {data.zkPushDeviceActive && (data.deletions.length > 0 || !employee.active) && (
            <p className="px-5 py-3 text-sm text-muted-foreground">
              Hapus juga langsung di mesin X401 jika karyawan pernah didaftarkan di sana.
            </p>
          )}
        </div>
      )}

      {confirmFinger !== null && (
        <Dialog open onOpenChange={(open) => !open && setConfirmFinger(null)}>
          <DialogContent className={dialogSheetClass}>
            <DialogHeader>
              <DialogTitle className="font-heading text-lg">Daftar ulang jari ke-{confirmFinger}?</DialogTitle>
              <DialogDescription>
                Catatan jari ini diganti setelah pendaftaran baru berhasil. Sidik jari lama di mesin tetap ada sampai
                dihapus di mesin.
              </DialogDescription>
            </DialogHeader>
            <DialogFooter className="mt-2 gap-2">
              <Button variant="outline" onClick={() => setConfirmFinger(null)}>
                Batal
              </Button>
              <Button
                onClick={() => {
                  setEnrolling({ finger: confirmFinger })
                  setConfirmFinger(null)
                }}
              >
                Lanjutkan
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}

      {deleteFinger !== null && (
        <Dialog open onOpenChange={(open) => !open && !remove.isPending && setDeleteFinger(null)}>
          <DialogContent className={dialogSheetClass}>
            <DialogHeader>
              <DialogTitle className="font-heading text-lg">
                Hapus sidik jari jari ke-{deleteFinger} {employee.fullName} dari semua mesin?
              </DialogTitle>
              <DialogDescription>
                Catatan jari ini langsung dihapus. Mesin menghapusnya dari sensor saat tersambung; statusnya tampil di
                daftar "Penghapusan di mesin".
              </DialogDescription>
            </DialogHeader>
            {remove.error && <ErrorAlert message={remove.error.message} />}
            <DialogFooter className="mt-2 gap-2">
              <Button variant="outline" disabled={remove.isPending} onClick={() => setDeleteFinger(null)}>
                Batal
              </Button>
              <Button variant="destructive" disabled={remove.isPending} onClick={() => remove.mutate(deleteFinger)}>
                {remove.isPending ? 'Menghapus…' : 'Ya, hapus'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}

      {enrolling && (
        <EnrollDialog
          employee={employee}
          finger={enrolling.finger}
          commandId={enrolling.commandId}
          onClose={() => setEnrolling(null)}
        />
      )}
    </section>
  )
}

function DeletionRow({ deletion, retrying, onRetry }: { deletion: Deletion; retrying: boolean; onRetry: () => void }) {
  const failed = deletion.status === 'GAGAL'
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 text-sm">
      <span className="flex flex-col">
        <span className="font-medium">{deletion.deviceName}</span>
        <span className="text-xs text-muted-foreground">
          {deletion.finger === null ? 'Semua jari' : `Jari ke-${deletion.finger}`}
        </span>
      </span>
      <span className="flex items-center gap-2">
        <span className={failed ? 'text-danger' : 'text-muted-foreground'}>
          {failed ? `Gagal: ${deletion.message}` : deletion.message}
        </span>
        {deletion.retryable && (
          <Button size="sm" variant="outline" disabled={retrying} onClick={onRetry}>
            Coba lagi
          </Button>
        )}
      </span>
    </li>
  )
}
