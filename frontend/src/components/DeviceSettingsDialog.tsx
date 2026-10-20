import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { type ReactNode, useCallback, useState } from 'react'

import { DeviceSettingsForm } from '@/components/DeviceSettingsForm'
import { ErrorAlert } from '@/components/feedback'
import { fieldClass } from '@/components/FormDialog'
import { PasswordConfirmField } from '@/components/PasswordConfirmField'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { api, ApiError } from '@/lib/api'
import type { Device, DeviceCommand, DeviceSettingsResponse, DeviceSettingsValues, NetworkView } from '@/lib/types'
import { dialogSheetClass } from '@/lib/ui'
import { type StreamEvent, useDashboardStream } from '@/lib/useDashboardStream'

const MAX_WIFI = 5

const errorMessage = (e: unknown) => (e instanceof ApiError ? e.message : 'Terjadi kesalahan. Coba lagi.')

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3 border-t pt-4 first:border-t-0 first:pt-0">
      <h3 id={id} className="font-heading text-base font-semibold">
        {title}
      </h3>
      {children}
    </section>
  )
}

const settingsKey = (deviceId: number) => ['/api/devices', deviceId, 'settings']
const networkKey = (deviceId: number) => ['/api/devices', deviceId, 'network']

function useSettings(deviceId: number) {
  return useQuery({
    queryKey: settingsKey(deviceId),
    queryFn: () => api<DeviceSettingsResponse>(`/api/devices/${deviceId}/settings`),
  })
}

/** Status versi paket pengaturan ringan (spec §18.2). */
function versionText(settings: DeviceSettingsResponse) {
  if (settings.version === 0) {
    return 'Belum pernah disimpan dari web; mesin memakai pengaturan bawaan.'
  }
  return settings.appliedVersion === settings.version
    ? `Versi ${settings.version} sudah diterapkan mesin.`
    : `Versi ${settings.version} menunggu diterapkan mesin.`
}

function LightSettings({ device }: { device: Device }) {
  const queryClient = useQueryClient()
  const settings = useSettings(device.id)
  const [saved, setSaved] = useState<number | null>(null)
  if (settings.error) {
    return <ErrorAlert message={errorMessage(settings.error)} />
  }
  if (!settings.data) {
    return <p className="text-sm text-muted-foreground">Memuat…</p>
  }
  return (
    <>
      <p className="text-sm text-muted-foreground">{versionText(settings.data)}</p>
      <DeviceSettingsForm
        idPrefix={`settings-${device.id}`}
        initial={settings.data.values}
        submitLabel="Simpan pengaturan"
        onSubmit={async (values) => {
          const result = await api<DeviceSettingsResponse>(`/api/devices/${device.id}/settings`, {
            method: 'PUT',
            body: values,
          })
          queryClient.setQueryData(settingsKey(device.id), result)
          void queryClient.invalidateQueries({ queryKey: ['/api/devices'], exact: true })
          setSaved(result.version)
        }}
      />
      {saved !== null && (
        <p className="text-sm text-success" role="status">
          Tersimpan sebagai versi {saved}. Mesin menerapkannya pada heartbeat berikutnya.
        </p>
      )}
    </>
  )
}

/** Perintah jaringan/kunci terakhir; hanya bisa dibatalkan selama belum dikirim (spec §18.4). */
function LatestCommand({ command, onChanged }: { command: DeviceCommand; onChanged: (c: DeviceCommand) => void }) {
  const [error, setError] = useState<string | null>(null)
  const [cancelling, setCancelling] = useState(false)
  const cancel = async () => {
    setCancelling(true)
    setError(null)
    try {
      onChanged(await api<DeviceCommand>(`/api/device-commands/${command.id}/cancel`, { method: 'POST' }))
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setCancelling(false)
    }
  }
  return (
    <div className="flex flex-col gap-2 rounded-md border bg-muted/40 p-3 text-sm" role="status" aria-live="polite">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span>
          <span className="text-muted-foreground">Perintah terakhir: </span>
          {command.message}
        </span>
        {command.status === 'MENUNGGU' && (
          <Button type="button" variant="outline" size="sm" disabled={cancelling} onClick={cancel}>
            Batalkan
          </Button>
        )}
      </div>
      {error && <ErrorAlert message={error} />}
    </div>
  )
}

function useNetwork(deviceId: number) {
  const queryClient = useQueryClient()
  const network = useQuery({
    queryKey: networkKey(deviceId),
    queryFn: () => api<NetworkView>(`/api/devices/${deviceId}/network`),
  })
  const latestId = network.data?.latest?.id
  const onEvent = useCallback(
    (event: StreamEvent) => {
      if (event.type === 'DEVICE_COMMAND' && event.commandId && event.commandId === latestId) {
        void queryClient.invalidateQueries({ queryKey: networkKey(deviceId) })
      }
    },
    [deviceId, latestId, queryClient],
  )
  useDashboardStream(onEvent)
  const setLatest = (command: DeviceCommand) =>
    queryClient.setQueryData<NetworkView>(networkKey(deviceId), (current) =>
      current ? { ...current, latest: command } : current,
    )
  return { network, setLatest }
}

interface WifiRow {
  ssid: string
  password: string
}

function NetworkSettings({ device, view, onCommand }: { device: Device; view: NetworkView; onCommand: (c: DeviceCommand) => void }) {
  const [rows, setRows] = useState<WifiRow[]>([{ ssid: '', password: '' }])
  const [serverUrl, setServerUrl] = useState('')
  const [password, setPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const wifi = rows.filter((row) => row.ssid.trim())
  const filled = wifi.length > 0 || serverUrl.trim() !== ''
  const latest = view.latest?.type === 'APPLY_NETWORK' ? view.latest : null

  const setRow = (index: number, change: Partial<WifiRow>) =>
    setRows((current) => current.map((row, i) => (i === index ? { ...row, ...change } : row)))

  const apply = async () => {
    setSaving(true)
    setError(null)
    try {
      onCommand(
        await api<DeviceCommand>(`/api/devices/${device.id}/network`, {
          method: 'POST',
          body: { confirmPassword: password, wifi, serverUrl: serverUrl.trim() || null },
        }),
      )
      // Password tidak disimpan di browser setelah dikirim.
      setRows((current) => current.map((row) => ({ ...row, password: '' })))
      setPassword('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      {view.currentWifi.length === 0 && !view.currentServerUrl ? (
        <p className="text-sm text-muted-foreground">Belum pernah diatur dari web</p>
      ) : (
        <dl className="grid gap-1 text-sm">
          <div>
            <dt className="inline text-muted-foreground">Wi-Fi yang berlaku: </dt>
            <dd className="inline">{view.currentWifi.length ? view.currentWifi.join(', ') : '—'}</dd>
          </div>
          <div>
            <dt className="inline text-muted-foreground">Alamat server: </dt>
            <dd className="inline font-mono">{view.currentServerUrl ?? '—'}</dd>
          </div>
        </dl>
      )}
      {latest && <LatestCommand command={latest} onChanged={onCommand} />}
      <p className="text-sm text-muted-foreground">
        Jika mesin gagal tersambung dengan pengaturan baru, mesin kembali ke pengaturan lama dan melaporkannya di sini.
      </p>
      {rows.map((row, index) => (
        <div key={index} className="grid gap-2 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`wifi-ssid-${device.id}-${index}`} className="font-semibold">
              Nama Wi-Fi {index + 1}
            </Label>
            <Input
              id={`wifi-ssid-${device.id}-${index}`}
              className={fieldClass}
              maxLength={32}
              value={row.ssid}
              onChange={(e) => setRow(index, { ssid: e.target.value })}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor={`wifi-password-${device.id}-${index}`} className="font-semibold">
              Password Wi-Fi {index + 1}
            </Label>
            <Input
              id={`wifi-password-${device.id}-${index}`}
              type="password"
              autoComplete="new-password"
              className={fieldClass}
              maxLength={63}
              value={row.password}
              onChange={(e) => setRow(index, { password: e.target.value })}
            />
          </div>
          {rows.length > 1 && (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-label={`Hapus Wi-Fi ${index + 1}`}
              onClick={() => setRows((current) => current.filter((_, i) => i !== index))}
            >
              <Trash2 />
            </Button>
          )}
        </div>
      ))}
      <p className="text-xs text-muted-foreground">
        Password Wi-Fi tidak pernah ditampilkan lagi. Kosongkan password untuk Wi-Fi yang sudah berlaku agar password
        tersimpan tetap dipakai; untuk Wi-Fi baru, password kosong berarti Wi-Fi terbuka. Urutan menentukan prioritas.
      </p>
      {rows.length < MAX_WIFI && (
        <div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setRows((current) => [...current, { ssid: '', password: '' }])}
          >
            <Plus />
            Tambah Wi-Fi
          </Button>
        </div>
      )}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor={`server-url-${device.id}`} className="font-semibold">
          Alamat server baru
        </Label>
        <Input
          id={`server-url-${device.id}`}
          className={fieldClass}
          inputMode="url"
          placeholder="http://192.168.1.10:8888"
          value={serverUrl}
          onChange={(e) => setServerUrl(e.target.value)}
        />
        <p className="text-xs text-muted-foreground">Kosongkan jika alamat server tidak diubah.</p>
      </div>
      <PasswordConfirmField id={`network-password-${device.id}`} value={password} onChange={setPassword} />
      {error && <ErrorAlert message={error} />}
      <div>
        <Button type="button" disabled={saving || !password || !filled} onClick={apply}>
          {saving ? 'Mengirim…' : 'Terapkan jaringan'}
        </Button>
      </div>
    </>
  )
}

function KeySettings({ device, view, onCommand }: { device: Device; view: NetworkView; onCommand: (c: DeviceCommand) => void }) {
  const [password, setPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const latest = view.latest?.type === 'ROTATE_KEY' ? view.latest : null
  const rotate = async () => {
    setSaving(true)
    setError(null)
    try {
      onCommand(
        await api<DeviceCommand>(`/api/devices/${device.id}/rotate-key`, {
          method: 'POST',
          body: { confirmPassword: password },
        }),
      )
      setPassword('')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }
  return (
    <>
      <p className="text-sm text-muted-foreground">
        Mesin menerima kunci baru lewat koneksinya dan memakainya sendiri. Kunci lama tetap berlaku sampai mesin memakai
        kunci baru (paling lama 24 jam). Untuk mesin yang tidak bisa terhubung, pakai &quot;Buat kode baru&quot;.
      </p>
      {latest && <LatestCommand command={latest} onChanged={onCommand} />}
      <PasswordConfirmField id={`rotate-password-${device.id}`} value={password} onChange={setPassword} />
      {error && <ErrorAlert message={error} />}
      <div>
        <Button type="button" disabled={saving || !password} onClick={rotate}>
          {saving ? 'Mengirim…' : 'Ganti kunci'}
        </Button>
      </div>
    </>
  )
}

/** Bagian Jaringan dan Kunci (Owner saja); keduanya memakai data jaringan yang sama. */
function OwnerSections({ device }: { device: Device }) {
  const { network, setLatest } = useNetwork(device.id)
  const body = (content: (view: NetworkView) => ReactNode) =>
    network.error ? (
      <ErrorAlert message={errorMessage(network.error)} />
    ) : network.data ? (
      content(network.data)
    ) : (
      <p className="text-sm text-muted-foreground">Memuat…</p>
    )
  return (
    <>
      <Section id={`network-${device.id}`} title="Jaringan">
        {body((view) => <NetworkSettings device={device} view={view} onCommand={setLatest} />)}
      </Section>
      <Section id={`key-${device.id}`} title="Kunci">
        {body((view) => <KeySettings device={device} view={view} onCommand={setLatest} />)}
      </Section>
    </>
  )
}

/** Dialog "Pengaturan" satu alat rakitan (spec §18.9). */
export function DeviceSettingsDialog({ device, isOwner, onClose }: { device: Device; isOwner: boolean; onClose: () => void }) {
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={`${dialogSheetClass} max-h-[90vh] overflow-y-auto`}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">Pengaturan {device.name}</DialogTitle>
          <DialogDescription>Perubahan dikirim ke mesin pada heartbeat berikutnya.</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <Section id={`light-${device.id}`} title="Tampilan & suara">
            <LightSettings device={device} />
          </Section>
          {isOwner && <OwnerSections device={device} />}
        </div>
      </DialogContent>
    </Dialog>
  )
}

/** "Pengaturan semua mesin": nilai awal dari satu mesin, disimpan ke semua alat rakitan aktif. */
export function AllDevicesSettingsDialog({ source, count, onClose }: { source: Device; count: number; onClose: () => void }) {
  const queryClient = useQueryClient()
  const settings = useSettings(source.id)
  const [saved, setSaved] = useState<number | null>(null)
  const save = async (values: DeviceSettingsValues) => {
    const result = await api<DeviceSettingsResponse[]>('/api/device-settings', { method: 'PUT', body: values })
    void queryClient.invalidateQueries({ queryKey: ['/api/devices'] })
    setSaved(result.length)
  }
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className={`${dialogSheetClass} max-h-[90vh] overflow-y-auto`}>
        <DialogHeader>
          <DialogTitle className="font-heading text-lg">Pengaturan semua mesin</DialogTitle>
          <DialogDescription>
            Disimpan ke {count} alat rakitan aktif. Nilai awal diambil dari {source.name}.
          </DialogDescription>
        </DialogHeader>
        {settings.error ? (
          <ErrorAlert message={errorMessage(settings.error)} />
        ) : settings.data ? (
          <DeviceSettingsForm
            idPrefix="settings-all"
            initial={settings.data.values}
            submitLabel="Simpan ke semua mesin"
            onSubmit={save}
          />
        ) : (
          <p className="text-sm text-muted-foreground">Memuat…</p>
        )}
        {saved !== null && (
          <p className="text-sm text-success" role="status">
            Tersimpan ke {saved} mesin.
          </p>
        )}
      </DialogContent>
    </Dialog>
  )
}
