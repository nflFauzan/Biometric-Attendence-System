import { useQuery } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'

import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { Badge } from '@/components/ui/badge'
import { api } from '@/lib/api'
import { toId } from '@/lib/form'
import { formatDateTime, fromDateTimeLocal, toDateTimeLocal } from '@/lib/format'
import type { Device, UnregisteredAttempt } from '@/lib/types'
import { useBranchOptions } from '@/pages/options'

/** Mesin yang mencoba terhubung tetapi nomor serinya belum terdaftar (K-067). */
function UnregisteredBanner() {
  const { data } = useQuery({
    queryKey: ['/api/devices', 'unregistered'],
    queryFn: () => api<UnregisteredAttempt[]>('/api/devices/unregistered-attempts'),
  })
  if (!data?.length) {
    return null
  }
  return (
    <section className="rounded-lg border border-warning/30 bg-warning-bg p-4 text-sm">
      <p className="flex items-center gap-2 font-semibold text-warning">
        <AlertTriangle className="size-4" aria-hidden="true" />
        Mesin belum terdaftar mencoba terhubung
      </p>
      <p className="mt-1 text-muted-foreground">
        Scan dari mesin ini ditolak dan tetap tersimpan di mesin. Tambahkan mesin dengan serial number berikut agar
        scannya diterima.
      </p>
      <ul className="mt-2 flex flex-col gap-1">
        {data.map((attempt) => (
          <li key={attempt.serialNumber}>
            <span className="font-mono font-semibold">{attempt.serialNumber}</span>
            <span className="text-muted-foreground">
              {' '}
              · {attempt.ip ?? 'IP tidak diketahui'} · terakhir {formatDateTime(attempt.lastAttemptAt)} · {attempt.attempts}×
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}

export function DevicesPage() {
  const branchOptions = useBranchOptions()
  return (
    <CrudPage<Device>
      title="Mesin Absensi"
      description="Mesin dikenali dari serial number-nya. Status online berdasarkan kontak terakhir mesin ke server."
      endpoint="/api/devices"
      toolbar={<UnregisteredBanner />}
      columns={[
        { header: 'Nama', cell: (d) => d.name, role: 'primary' },
        { header: 'Serial number', cell: (d) => d.serialNumber, mono: true },
        { header: 'Cabang', cell: (d) => d.branchName },
        {
          header: 'Koneksi',
          cell: (d) => (
            <span className="flex flex-col gap-0.5">
              <Badge variant={d.online ? 'success' : 'neutral'}>{d.online ? 'Online' : 'Offline'}</Badge>
              <span className="text-xs text-muted-foreground">
                {d.lastSeenAt ? `${formatDateTime(d.lastSeenAt)}${d.lastIp ? ` · ${d.lastIp}` : ''}` : 'Belum pernah terhubung'}
              </span>
            </span>
          ),
        },
        { header: 'Firmware', cell: (d) => d.firmware ?? '—' },
        {
          header: 'Terima scan sejak',
          cell: (d) => (
            <span className="flex flex-col gap-0.5">
              {formatDateTime(d.acceptScansFrom)}
              {d.ignoredScanCount > 0 && (
                <span className="text-xs text-muted-foreground">{d.ignoredScanCount} scan lama diabaikan</span>
              )}
            </span>
          ),
        },
        { header: 'Status', cell: (d) => <ActiveBadge active={d.active} />, role: 'status' },
      ]}
      fields={() => [
        { name: 'name', label: 'Nama', required: true },
        { name: 'serialNumber', label: 'Serial number', required: true, hint: 'Persis seperti di menu info mesin.' },
        { name: 'branchId', label: 'Cabang', type: 'select', options: branchOptions, required: true },
        {
          name: 'acceptScansFrom',
          label: 'Terima scan sejak',
          type: 'datetime-local',
          hint: 'Scan sebelum waktu ini (riwayat lama di mesin) tidak disimpan. Kosongkan = sejak sekarang.',
        },
        { name: 'active', label: 'Aktif', type: 'checkbox' },
      ]}
      emptyValues={{ name: '', serialNumber: '', branchId: '', acceptScansFrom: '', active: true }}
      toValues={(d) => ({
        name: d.name,
        serialNumber: d.serialNumber,
        branchId: String(d.branchId),
        acceptScansFrom: toDateTimeLocal(d.acceptScansFrom),
        active: d.active,
      })}
      toBody={(v) => ({
        name: v.name,
        serialNumber: v.serialNumber,
        branchId: toId(v.branchId),
        acceptScansFrom: fromDateTimeLocal(String(v.acceptScansFrom)),
        active: v.active,
      })}
    />
  )
}
