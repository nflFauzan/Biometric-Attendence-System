import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Fingerprint } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'

import { ErrorAlert } from '@/components/feedback'
import { selectClass } from '@/components/FormDialog'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { api } from '@/lib/api'
import type { CommandStatus, Device, DeviceCommand, Employee } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { useDashboardStream, type StreamEvent } from '@/lib/useDashboardStream'

/** Cadangan bila SSE terputus (Fokus review #5). */
const POLL_MS = 3000

const isActiveCommand = (status: CommandStatus | undefined) => status === 'MENUNGGU' || status === 'DIKIRIM'

interface EnrollDialogProps {
  employee: Employee
  finger: number
  /** Perintah aktif yang sudah ada (mis. halaman dibuka ulang saat pendaftaran berjalan). */
  commandId?: string
  onClose: () => void
}

/**
 * Pendaftaran sidik jari dari web untuk alat rakitan (spec §3.2): pilih mesin, mulai, lalu ikuti status yang
 * dilaporkan mesin. Teks status berasal dari server; dialog hanya menampilkannya.
 */
export function EnrollDialog({ employee, finger, commandId: initialCommandId, onClose }: EnrollDialogProps) {
  const queryClient = useQueryClient()
  const [commandId, setCommandId] = useState(initialCommandId)
  const [deviceId, setDeviceId] = useState('')

  const devices = useQuery({ queryKey: ['/api/devices'], queryFn: () => api<Device[]>('/api/devices') })
  const candidates = (devices.data ?? []).filter((d) => d.protocol === 'RAKITAN_V1' && d.active && d.online)
  // Bawaan: mesin di cabang karyawan, lalu alat rakitan online pertama.
  const selected =
    deviceId || String((candidates.find((d) => d.branchId === employee.branchId) ?? candidates[0])?.id ?? '')

  const command = useQuery({
    queryKey: ['/api/device-commands', commandId],
    queryFn: () => api<DeviceCommand>(`/api/device-commands/${commandId}`),
    enabled: Boolean(commandId),
    refetchInterval: (query) => (isActiveCommand(query.state.data?.status) ? POLL_MS : false),
  })

  const onEvent = useCallback(
    (event: StreamEvent) => {
      if (event.type === 'DEVICE_COMMAND' && event.commandId && event.commandId === commandId) {
        void queryClient.invalidateQueries({ queryKey: ['/api/device-commands', commandId] })
      }
    },
    [commandId, queryClient],
  )
  useDashboardStream(onEvent)

  const start = useMutation({
    mutationFn: () =>
      api<DeviceCommand>(`/api/employees/${employee.id}/fingerprints/enroll`, {
        method: 'POST',
        body: { deviceId: Number(selected), finger },
      }),
    onSuccess: (created) => {
      queryClient.setQueryData(['/api/device-commands', created.id], created)
      setCommandId(created.id)
    },
  })

  const cancel = useMutation({
    mutationFn: () => api<DeviceCommand>(`/api/device-commands/${commandId}/cancel`, { method: 'POST' }),
    onSuccess: (cancelled) => queryClient.setQueryData(['/api/device-commands', cancelled.id], cancelled),
  })

  const status = command.data?.status
  const finished = Boolean(status) && !isActiveCommand(status)
  useEffect(() => {
    if (status) {
      // Bagian "Sidik Jari" ikut diperbarui: jari terdaftar dan perintah aktif.
      void queryClient.invalidateQueries({ queryKey: ['/api/employees', String(employee.id), 'fingerprints'] })
    }
  }, [status, employee.id, queryClient])

  const error = start.error ?? cancel.error ?? command.error

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">Daftarkan jari ke-{finger}</DialogTitle>
          <DialogDescription>
            {employee.fullName}. Setelah mulai, minta karyawan menempelkan jari yang sama 3 kali di mesin.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          {!commandId && devices.data && candidates.length === 0 && (
            <p className="rounded-md border bg-secondary/50 px-3 py-2 text-sm">
              Tidak ada alat rakitan yang online. Mesin X401 tidak mendukung pendaftaran dari web; daftarkan langsung di
              mesin.
            </p>
          )}
          {!commandId && candidates.length > 0 && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="enroll-device" className="font-semibold">
                Mesin
              </Label>
              <select
                id="enroll-device"
                className={selectClass}
                value={selected}
                onChange={(e) => setDeviceId(e.target.value)}
              >
                {candidates.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.name} ({d.branchName})
                  </option>
                ))}
              </select>
            </div>
          )}
          {command.data && (
            <p
              role="status"
              className="flex items-center gap-3 rounded-md border bg-secondary/50 px-3 py-3 text-sm font-semibold"
            >
              <Fingerprint className="size-5 shrink-0 text-muted-foreground" aria-hidden="true" />
              {command.data.message}
            </p>
          )}
          {error && <ErrorAlert message={error.message} />}
        </div>

        <DialogFooter className="mt-2 gap-2">
          {!commandId && (
            <>
              <Button type="button" variant="outline" onClick={onClose}>
                Tutup
              </Button>
              {candidates.length > 0 && (
                <Button type="button" disabled={!selected || start.isPending} onClick={() => start.mutate()}>
                  {start.isPending ? 'Memulai…' : 'Mulai'}
                </Button>
              )}
            </>
          )}
          {commandId && !finished && (
            <Button type="button" variant="outline" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
              Batal
            </Button>
          )}
          {finished && (
            <Button type="button" onClick={onClose}>
              Tutup
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
