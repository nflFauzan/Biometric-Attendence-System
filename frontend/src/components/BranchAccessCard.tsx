import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2 } from 'lucide-react'
import { useState } from 'react'

import { ErrorAlert, SkeletonLines } from '@/components/feedback'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { api } from '@/lib/api'
import { branchAccessKey } from '@/lib/branchAccess'
import type { Branch, BranchAccess, Device, Employee, FingerprintOverview } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'

/**
 * Kartu "Cabang absen" di Detail Karyawan (K-103): cabang penempatan selalu boleh, cabang lain dicentang sebagai
 * tambahan. Mencabut cabang yang mesinnya menyimpan sidik jari meminta konfirmasi, karena catatan jarinya dihapus.
 * Owner dan Finance & Accounting mengubahnya (K-135); role lain melihatnya dalam mode hanya baca.
 */
export function BranchAccessCard({ employee, readOnly = false }: { employee: Employee; readOnly?: boolean }) {
  const queryClient = useQueryClient()
  const access = useQuery({
    queryKey: branchAccessKey(employee.id),
    queryFn: () => api<BranchAccess>(`/api/employees/${employee.id}/branches`),
  })
  const branches = useQuery({
    queryKey: ['/api/branches'],
    queryFn: () => api<Branch[]>('/api/branches'),
  })
  const devices = useQuery({
    queryKey: ['/api/devices'],
    queryFn: () => api<Device[]>('/api/devices'),
  })
  const fingerprints = useQuery({
    queryKey: ['/api/employees', String(employee.id), 'fingerprints'],
    queryFn: () => api<FingerprintOverview>(`/api/employees/${employee.id}/fingerprints`),
  })

  const [draft, setDraft] = useState<number[] | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  const saved = access.data?.extraBranchIds ?? []
  const selected = draft ?? saved
  const dirty = draft !== null && [...draft].sort().join() !== [...saved].sort().join()
  const placementId = access.data?.placementBranchId ?? employee.branchId
  const options = (branches.data ?? []).filter((b) => b.active || b.id === placementId || selected.includes(b.id))

  // Jari yang akan terhapus: milik mesin di cabang yang dicabut dari daftar tersimpan.
  const revoked = saved.filter((id) => !selected.includes(id))
  const doomed = new Map<number, { name: string; count: number }>()
  // Jari di mesin yang tidak ada di daftar mesin: cabangnya tidak diketahui, jadi dianggap mungkin ikut terhapus.
  let unknown = 0
  for (const record of fingerprints.data?.fingers ?? []) {
    const device = devices.data?.find((d) => d.id === record.deviceId)
    if (!device) {
      unknown += 1
    } else if (revoked.includes(device.branchId)) {
      const entry = doomed.get(device.id) ?? { name: device.name, count: 0 }
      entry.count += 1
      doomed.set(device.id, entry)
    }
  }

  // Simpan hanya aman setelah data jari dan mesin termuat; kalau tidak, konfirmasi penghapusan bisa terlewat.
  const dataReady = fingerprints.isSuccess && devices.isSuccess
  const dataFailed = fingerprints.isError || devices.isError
  const needsConfirm = doomed.size > 0 || (revoked.length > 0 && unknown > 0)

  const save = useMutation({
    mutationFn: () =>
      api<BranchAccess>(`/api/employees/${employee.id}/branches`, {
        method: 'PUT',
        body: { extraBranchIds: selected },
      }),
    onSuccess: (result) => {
      queryClient.setQueryData(branchAccessKey(employee.id), result)
      void queryClient.invalidateQueries({
        queryKey: ['/api/employees', String(employee.id)],
        exact: true,
      })
      void queryClient.invalidateQueries({
        queryKey: ['/api/employees', String(employee.id), 'fingerprints'],
      })
      setNotice(
        result.removedFingerprints > 0
          ? `Cabang absen disimpan. ${result.removedFingerprints} sidik jari dihapus.`
          : 'Cabang absen disimpan.',
      )
      setDraft(null)
      setConfirming(false)
    },
    onError: () => setConfirming(false),
  })

  const toggle = (id: number, checked: boolean) => {
    setNotice(null)
    setDraft(checked ? [...selected, id] : selected.filter((x) => x !== id))
  }

  return (
    <section className="rounded-lg border glass lg:col-span-3" aria-labelledby="cabang-absen">
      <h2 id="cabang-absen" className="flex items-center gap-2 border-b px-5 py-3.5 text-base font-semibold">
        <Building2 className="size-4 text-muted-foreground" aria-hidden="true" />
        Cabang absen
      </h2>
      {(access.isPending || branches.isPending) && <SkeletonLines count={2} />}
      {access.error && <ErrorAlert className="m-4" message={access.error.message} />}
      {branches.error && <ErrorAlert className="m-4" message={branches.error.message} />}
      {access.data && branches.data && (
        <div className="flex flex-col gap-3 px-5 py-4">
          <p className="text-sm text-muted-foreground">Karyawan boleh absen di mesin pada cabang yang dicentang.</p>
          <ul className="flex flex-col gap-1">
            {options.map((branch) => {
              const isPlacement = branch.id === placementId
              const id = `cabang-absen-${branch.id}`
              return (
                <li key={branch.id} className="flex min-h-11 items-center gap-3 md:min-h-9">
                  <Checkbox
                    id={id}
                    checked={isPlacement || selected.includes(branch.id)}
                    disabled={isPlacement || readOnly || save.isPending}
                    onCheckedChange={(checked) => toggle(branch.id, checked === true)}
                  />
                  <label htmlFor={id} className="text-sm font-medium">
                    {branch.name}
                  </label>
                  {isPlacement && <span className="text-xs text-muted-foreground">Penempatan</span>}
                </li>
              )
            })}
          </ul>
          {dataFailed && <ErrorAlert message="Data sidik jari belum bisa dimuat. Muat ulang halaman." />}
          {save.error && <ErrorAlert message={save.error.message} />}
          {notice && (
            <p role="status" className="text-sm font-semibold">
              {notice}
            </p>
          )}
          {readOnly ? (
            <p className="text-sm text-muted-foreground">Hanya Owner dan Finance & Accounting yang dapat mengubah cabang absen.</p>
          ) : (
            <div>
              <Button
                size="sm"
                disabled={!dirty || !dataReady || save.isPending}
                onClick={() => (needsConfirm ? setConfirming(true) : save.mutate())}
              >
                {save.isPending ? 'Menyimpan…' : 'Simpan'}
              </Button>
            </div>
          )}
        </div>
      )}

      {confirming && (
        <Dialog open onOpenChange={(open) => !open && !save.isPending && setConfirming(false)}>
          <DialogContent className={dialogSheetClass}>
            <DialogHeader>
              <DialogTitle className="font-heading text-lg">Cabut izin absen cabang?</DialogTitle>
              <DialogDescription>Catatan sidik jari berikut dihapus dari karyawan ini.</DialogDescription>
            </DialogHeader>
            <ul className="flex flex-col gap-1 text-sm font-semibold">
              {[...doomed.entries()].map(([deviceId, d]) => (
                <li key={deviceId}>{`${d.count} sidik jari di mesin ${d.name} akan dihapus.`}</li>
              ))}
              {unknown > 0 && <li>{`${unknown} sidik jari di mesin yang tidak dikenal mungkin ikut dihapus.`}</li>}
            </ul>
            <DialogFooter className="mt-2 gap-2">
              <Button variant="outline" disabled={save.isPending} onClick={() => setConfirming(false)}>
                Batal
              </Button>
              <Button variant="destructive" disabled={save.isPending} onClick={() => save.mutate()}>
                {save.isPending ? 'Menyimpan…' : 'Ya, simpan'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      )}
    </section>
  )
}
