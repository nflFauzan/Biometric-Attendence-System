import { useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, KeyRound, SlidersHorizontal } from 'lucide-react'
import { useState } from 'react'

import { useAuth } from '@/auth/AuthContext'
import { ActiveBadge, CrudPage } from '@/components/CrudPage'
import { AllDevicesSettingsDialog, DeviceSettingsDialog } from '@/components/DeviceSettingsDialog'
import { ErrorAlert } from '@/components/feedback'
import { PasswordConfirmField } from '@/components/PasswordConfirmField'
import { SetupCodeDialog } from '@/components/SetupCodeDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { api, ApiError } from '@/lib/api'
import { toId } from '@/lib/form'
import { useDashboardStream } from '@/lib/useDashboardStream'
import { formatDateTime, fromDateTimeLocal, toDateTimeLocal } from '@/lib/format'
import type { Device, IssuedKey, UnregisteredAttempt } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { useBranchOptions } from '@/pages/options'

const PROTOCOL_LABELS: Record<Device['protocol'], string> = {
  ZK_PUSH: 'ZK-Push',
  RAKITAN_V1: 'Alat rakitan',
}

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

/** "Buat kode baru": kunci lama langsung tidak berlaku (K-088), jadi minta konfirmasi dulu. */
function NewKeyDialog({ device, onClose, onIssued }: { device: Device; onClose: () => void; onIssued: (d: Device) => void }) {
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [password, setPassword] = useState('')
  const issue = async () => {
    setSaving(true)
    setError(null)
    try {
      onIssued(
        await api<Device>(`/api/devices/${device.id}/key`, {
          method: 'POST',
          body: { confirmPassword: password },
        }),
      )
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')
    } finally {
      setSaving(false)
    }
  }
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={dialogSheetClass}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">Buat kode pengaturan baru?</DialogTitle>
          <DialogDescription>
            Kunci lama {device.name} langsung tidak berlaku. Mesin harus diatur ulang dengan kode baru.
          </DialogDescription>
        </DialogHeader>
        <PasswordConfirmField id="new-key-password" value={password} onChange={setPassword} />
        {error && <ErrorAlert message={error} />}
        <DialogFooter className="mt-2 gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Batal
          </Button>
          <Button type="button" onClick={issue} disabled={saving || !password}>
            {saving ? 'Membuat…' : 'Ya, buat kode baru'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

export function DevicesPage() {
  const branchOptions = useBranchOptions()
  const queryClient = useQueryClient()
  const { me } = useAuth()
  // K-099: hanya Owner yang menerbitkan kunci alat rakitan.
  const isOwner = me?.role === 'OWNER'
  const [setup, setSetup] = useState<{ device: Device; issuedKey: IssuedKey } | null>(null)
  const [newKeyFor, setNewKeyFor] = useState<Device | null>(null)
  const [settingsFor, setSettingsFor] = useState<Device | null>(null)
  const [settingsAll, setSettingsAll] = useState(false)
  // Status online/offline dan banner SN tak terdaftar ikut diperbarui tanpa memuat ulang halaman.
  useDashboardStream()
  // Data yang sama dengan daftar CrudPage (cache bersama), untuk tombol "Pengaturan semua mesin".
  const { data: devices } = useQuery({ queryKey: ['/api/devices'], queryFn: () => api<Device[]>('/api/devices') })
  const activeRakitan = (devices ?? []).filter((d) => d.protocol === 'RAKITAN_V1' && d.active)

  const showSetup = (device: Device) => {
    if (device.issuedKey) {
      setSetup({ device, issuedKey: device.issuedKey })
    }
  }

  return (
    <>
      <CrudPage<Device>
        title="Mesin Absensi"
        description="Mesin ZK-Push dikenali dari serial number-nya, alat rakitan dari kuncinya. Status online berdasarkan kontak terakhir mesin ke server."
        endpoint="/api/devices"
        toolbar={
          <>
            <UnregisteredBanner />
            {activeRakitan.length > 0 && (
              <div>
                <Button variant="outline" size="sm" onClick={() => setSettingsAll(true)}>
                  <SlidersHorizontal />
                  Pengaturan semua mesin
                </Button>
              </div>
            )}
          </>
        }
        onSaved={(saved, editing) => {
          if (!editing) {
            showSetup(saved)
          }
        }}
        rowActions={(d) =>
          d.protocol === 'RAKITAN_V1' ? (
            <>
              <Button variant="ghost" size="sm" onClick={() => setSettingsFor(d)}>
                <SlidersHorizontal />
                Pengaturan
              </Button>
              {isOwner && (
                <Button variant="ghost" size="sm" onClick={() => setNewKeyFor(d)}>
                  <KeyRound />
                  Buat kode baru
                </Button>
              )}
            </>
          ) : null
        }
        columns={[
          { header: 'Nama', cell: (d) => d.name, role: 'primary' },
          { header: 'Jenis', cell: (d) => PROTOCOL_LABELS[d.protocol] },
          { header: 'Serial number / ID', cell: (d) => d.serialNumber, mono: true },
          { header: 'Cabang', cell: (d) => d.branchName },
          {
            header: 'Koneksi',
            cell: (d) =>
              d.protocol === 'RAKITAN_V1' && d.keyIssued && !d.keyFirstUsedAt ? (
                <Badge variant="warning">Menunggu pengaturan</Badge>
              ) : (
                <span className="flex flex-col gap-0.5">
                  <Badge variant={d.online ? 'success' : 'neutral'}>{d.online ? 'Online' : 'Offline'}</Badge>
                  <span className="text-xs text-muted-foreground">
                    {d.lastSeenAt
                      ? `${formatDateTime(d.lastSeenAt)}${d.lastIp ? ` · ${d.lastIp}` : ''}`
                      : 'Belum pernah terhubung'}
                  </span>
                </span>
              ),
          },
          {
            header: 'Firmware',
            cell: (d) => (
              <span className="flex flex-col gap-0.5">
                {d.firmware ?? '—'}
                {d.lastInfo && <span className="text-xs text-muted-foreground">{d.lastInfo}</span>}
                {d.sensorUsed !== null && d.sensorCapacity !== null && (
                  <span className="text-xs text-muted-foreground">
                    Sensor {d.sensorUsed}/{d.sensorCapacity}
                  </span>
                )}
                {d.sensorAlmostFull && (
                  <span className="text-xs font-semibold text-warning">Kapasitas sensor hampir penuh.</span>
                )}
                {d.protocol === 'RAKITAN_V1' && d.settingsVersion > 0 && (
                  <span className="text-xs text-muted-foreground">
                    {d.settingsAppliedVersion === d.settingsVersion
                      ? 'Pengaturan terbaru sudah diterapkan ✓'
                      : `Menunggu mesin menerapkan versi ${d.settingsVersion}`}
                  </span>
                )}
              </span>
            ),
          },
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
        fields={(editing) => [
          // Jenis mesin hanya dipilih saat membuat; tidak bisa diubah (K-099).
          ...(editing
            ? []
            : [
                {
                  name: 'protocol',
                  label: 'Jenis mesin',
                  type: 'select' as const,
                  required: true,
                  options: [
                    { value: 'ZK_PUSH', label: 'Mesin ZK-Push (X401)' },
                    ...(isOwner ? [{ value: 'RAKITAN_V1', label: 'Alat rakitan' }] : []),
                  ],
                },
              ]),
          { name: 'name', label: 'Nama', required: true },
          ...(editing?.protocol === 'RAKITAN_V1'
            ? []
            : [
                {
                  name: 'serialNumber',
                  label: 'Serial number',
                  hint: editing
                    ? 'Persis seperti di menu info mesin.'
                    : 'Wajib untuk mesin ZK-Push, persis seperti di menu info mesin. Kosongkan untuk alat rakitan; ID dibuat otomatis.',
                },
              ]),
          { name: 'branchId', label: 'Cabang', type: 'select', options: branchOptions, required: true },
          {
            name: 'acceptScansFrom',
            label: 'Terima scan sejak',
            type: 'datetime-local',
            hint: 'Scan sebelum waktu ini (riwayat lama di mesin) tidak disimpan. Kosongkan = sejak sekarang.',
          },
          { name: 'active', label: 'Aktif', type: 'checkbox' },
        ]}
        emptyValues={{ protocol: 'ZK_PUSH', name: '', serialNumber: '', branchId: '', acceptScansFrom: '', active: true }}
        toValues={(d) => ({
          name: d.name,
          serialNumber: d.serialNumber,
          branchId: String(d.branchId),
          acceptScansFrom: toDateTimeLocal(d.acceptScansFrom),
          active: d.active,
        })}
        toBody={(v, editing) => ({
          ...(editing ? {} : { protocol: v.protocol }),
          name: v.name,
          serialNumber: v.serialNumber,
          branchId: toId(v.branchId),
          acceptScansFrom: fromDateTimeLocal(String(v.acceptScansFrom)),
          active: v.active,
        })}
      />

      {newKeyFor && (
        <NewKeyDialog
          device={newKeyFor}
          onClose={() => setNewKeyFor(null)}
          onIssued={(device) => {
            setNewKeyFor(null)
            void queryClient.invalidateQueries({ queryKey: ['/api/devices'] })
            showSetup(device)
          }}
        />
      )}
      {setup && <SetupCodeDialog device={setup.device} issuedKey={setup.issuedKey} onClose={() => setSetup(null)} />}
      {settingsFor && <DeviceSettingsDialog device={settingsFor} isOwner={isOwner} onClose={() => setSettingsFor(null)} />}
      {settingsAll && activeRakitan.length > 0 && (
        <AllDevicesSettingsDialog
          source={activeRakitan[0]}
          count={activeRakitan.length}
          onClose={() => setSettingsAll(false)}
        />
      )}
    </>
  )
}
