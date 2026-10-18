import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fingerprint } from 'lucide-react'
import { useState } from 'react'

import { EnrollDialog } from '@/components/EnrollDialog'
import { ErrorAlert, SkeletonLines } from '@/components/feedback'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { Employee, FingerprintOverview } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'

const FINGERS = [1, 2] as const

/**
 * Bagian "Sidik Jari" di Detail Karyawan (spec §3.2): persetujuan karyawan (K-091), status jari ke-1 dan ke-2,
 * dan tombol pendaftaran lewat alat rakitan. Template tidak pernah tampil di web.
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
                  <Button
                    size="sm"
                    variant={record ? 'outline' : 'default'}
                    disabled={Boolean(blocked)}
                    onClick={() => (record ? setConfirmFinger(finger) : setEnrolling({ finger }))}
                  >
                    {record ? 'Daftar ulang' : 'Daftarkan'}
                  </Button>
                )}
              </div>
            )
          })}

          {blocked && <p className="px-5 py-3 text-sm text-muted-foreground">{blocked}</p>}
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
