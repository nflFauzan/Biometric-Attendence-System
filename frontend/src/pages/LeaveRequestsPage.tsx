import { useQuery } from '@tanstack/react-query'
import { Paperclip, Plus } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { selectClass } from '@/components/FormDialog'
import { LeaveRequestForm } from '@/components/LeaveRequestForm'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import { formatLeaveRange, LEAVE_STATUS_LABELS, LEAVE_STATUS_TONES } from '@/lib/leave'
import type { LeaveRequest, LeaveStatus } from '@/lib/types'
import { useDashboardStream } from '@/lib/useDashboardStream'
import { cn } from '@/lib/utils'
import { useBranchOptions } from '@/pages/options'

const FILTERS: { value: LeaveStatus | ''; label: string }[] = [
  { value: 'MENUNGGU', label: 'Menunggu' },
  { value: 'DISETUJUI', label: 'Disetujui' },
  { value: 'DITOLAK', label: 'Ditolak' },
  { value: 'DIBATALKAN', label: 'Dibatalkan' },
  { value: '', label: 'Semua' },
]

const HEADERS = ['Karyawan', 'Jenis', 'Tanggal', 'Hari', 'Status', 'Diajukan', 'Surat']

/** Daftar pengajuan izin/cuti untuk Owner dan HR/Finance; bawaan menampilkan yang menunggu keputusan. */
export function LeaveRequestsPage() {
  useDashboardStream()
  const navigate = useNavigate()
  const branchOptions = useBranchOptions()
  const [status, setStatus] = useState<LeaveStatus | ''>('MENUNGGU')
  const [branchId, setBranchId] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const params = new URLSearchParams({ ...(status ? { status } : {}), ...(branchId ? { branchId } : {}) })
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/leave-requests', 'list', status, branchId],
    queryFn: () => api<LeaveRequest[]>(`/api/leave-requests?${params}`),
  })

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Izin & Cuti"
        description="Pengajuan dari karyawan masuk ke sini dan muncul sebagai notifikasi. Izin yang disetujui langsung tercermin di rekap absensi."
        actions={
          <Button onClick={() => setFormOpen(true)}>
            <Plus />
            Catat izin
          </Button>
        }
      />

      <div className="flex flex-wrap items-end gap-4">
        <div role="group" aria-label="Filter status" className="flex flex-wrap gap-2">
          {FILTERS.map((filter) => (
            <button
              key={filter.label}
              type="button"
              aria-pressed={status === filter.value}
              onClick={() => setStatus(filter.value)}
              className={cn(
                'h-9 rounded-full border px-4 text-sm font-semibold transition-colors',
                status === filter.value ? 'border-steel-600 bg-steel-600 text-white' : 'glass hover:border-steel-300',
              )}
            >
              {filter.label}
            </button>
          ))}
        </div>
        <div className="flex min-w-48 flex-col gap-1.5">
          <Label htmlFor="cabang" className="font-semibold">
            Cabang
          </Label>
          <select id="cabang" className={selectClass} value={branchId} onChange={(e) => setBranchId(e.target.value)}>
            <option value="">Semua cabang</option>
            {branchOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      {error && <ErrorAlert message={error.message} />}

      <section className="overflow-hidden rounded-lg border glass-strong">
        {data && <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.length} pengajuan</p>}
        {isPending && <SkeletonLines />}
        {data?.length === 0 && (
          <EmptyState message={status === 'MENUNGGU' ? 'Tidak ada pengajuan yang menunggu keputusan.' : 'Belum ada pengajuan.'} />
        )}
        {data && data.length > 0 && (
          <>
            <div className="hidden md:block">
              <Table>
                <TableHeader>
                  <TableRow className="glass-head hover:glass-head">
                    {HEADERS.map((header) => (
                      <TableHead key={header} scope="col" className="label-caps h-11 px-4 text-muted-foreground">
                        {header}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.map((r) => (
                    <TableRow key={r.id} className="hover:bg-accent/60">
                      <TableCell className="px-4">
                        <Link to={`/izin/${r.id}`} className="font-semibold text-steel-700 underline-offset-4 hover:underline dark:text-foreground">
                          {r.employeeName}
                        </Link>
                        <p className="text-xs text-muted-foreground">{r.branchName}</p>
                      </TableCell>
                      <TableCell className="px-4">{r.leaveTypeName}</TableCell>
                      <TableCell className="px-4 tabular-nums">{formatLeaveRange(r)}</TableCell>
                      <TableCell className="px-4 tabular-nums">{r.calendarDays}</TableCell>
                      <TableCell className="px-4">
                        <Badge variant={LEAVE_STATUS_TONES[r.status]}>{LEAVE_STATUS_LABELS[r.status]}</Badge>
                      </TableCell>
                      <TableCell className="px-4 text-sm tabular-nums">{formatDateTime(r.submittedAt)}</TableCell>
                      <TableCell className="px-4">
                        <AttachmentCount request={r} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <ul className="divide-y md:hidden">
              {data.map((r) => (
                <li key={r.id}>
                  <Link to={`/izin/${r.id}`} className="flex items-start justify-between gap-3 px-4 py-3 hover:bg-accent/60">
                    <div className="min-w-0">
                      <p className="truncate font-semibold">{r.employeeName}</p>
                      <p className="text-sm">
                        {r.leaveTypeName} · {formatLeaveRange(r)}
                      </p>
                      <p className="text-xs text-muted-foreground">
                        {r.branchName} · <AttachmentCount request={r} />
                      </p>
                    </div>
                    <Badge variant={LEAVE_STATUS_TONES[r.status]}>{LEAVE_STATUS_LABELS[r.status]}</Badge>
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <LeaveRequestForm
        open={formOpen}
        mode="manager"
        onClose={() => setFormOpen(false)}
        onCreated={(created) => {
          setFormOpen(false)
          navigate(`/izin/${created.id}`)
        }}
      />
    </div>
  )
}

function AttachmentCount({ request }: { request: LeaveRequest }) {
  const count = request.attachments.length
  if (count === 0) {
    return <span className={cn('text-xs', request.requiresDocument ? 'text-warning' : 'text-muted-foreground')}>{request.requiresDocument ? 'Belum ada (wajib)' : '—'}</span>
  }
  return (
    <span className="inline-flex items-center gap-1 text-xs">
      <Paperclip className="size-3.5" />
      {count}
    </span>
  )
}
