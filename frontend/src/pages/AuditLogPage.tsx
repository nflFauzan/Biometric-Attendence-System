import { useQuery } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useState } from 'react'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/format'
import type { AuditLog, PageResponse } from '@/lib/types'

const ENTITY_LABELS: Record<string, string> = {
  BRANCH: 'Cabang',
  EMPLOYEE_CATEGORY: 'Kategori',
  EMPLOYEE: 'Karyawan',
  DEVICE: 'Mesin',
  USER_ACCOUNT: 'Akun',
}

const ACTION_LABELS: Record<string, string> = {
  CREATE: 'Tambah',
  UPDATE: 'Ubah',
  RESET_PASSWORD: 'Reset password',
  CHANGE_PASSWORD: 'Ganti password',
}

function ActionBadge({ action }: { action: string }) {
  return <Badge variant="info">{ACTION_LABELS[action] ?? action}</Badge>
}

function Changes({ log }: { log: AuditLog }) {
  return (
    <details className="group">
      <summary className="cursor-pointer text-xs font-semibold text-steel-600 select-none">Lihat perubahan</summary>
      <div className="mt-2 grid gap-2 text-xs">
        <div className="rounded-md bg-danger-bg/60 p-2">
          <p className="label-caps mb-1 text-danger">Sebelum</p>
          <pre className="font-mono break-all whitespace-pre-wrap">{log.beforeData ?? '—'}</pre>
        </div>
        <div className="rounded-md bg-success-bg/60 p-2">
          <p className="label-caps mb-1 text-success">Sesudah</p>
          <pre className="font-mono break-all whitespace-pre-wrap">{log.afterData ?? '—'}</pre>
        </div>
      </div>
    </details>
  )
}

/** Tabel padat (Enterprise Data-Dense) di desktop, daftar kartu di HP (docs/design-system.md §6.4). */
export function AuditLogPage() {
  const [page, setPage] = useState(0)
  const { data, isPending, error } = useQuery({
    queryKey: ['/api/audit-logs', page],
    queryFn: () => api<PageResponse<AuditLog>>(`/api/audit-logs?page=${page}&size=50`),
  })

  const entity = (log: AuditLog) => `${ENTITY_LABELS[log.entityType] ?? log.entityType} #${log.entityId}`

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title="Audit Log" description="Riwayat perubahan data: siapa, kapan, dan nilai sebelum/sesudah." />

      {error && <ErrorAlert message={error.message} />}

      <section className="overflow-hidden rounded-lg border bg-card">
        {data && (
          <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.totalElements} catatan</p>
        )}
        {isPending && <SkeletonLines count={5} />}
        {data?.content.length === 0 && <EmptyState message="Belum ada perubahan data yang tercatat." />}

        {data && data.content.length > 0 && (
          <>
            <div className="hidden md:block">
              <Table className="text-[13px]">
                <TableHeader>
                  <TableRow className="bg-steel-50 hover:bg-steel-50 dark:bg-secondary dark:hover:bg-secondary">
                    {['Waktu', 'Pengguna', 'Aksi', 'Data', 'Perubahan'].map((header) => (
                      <TableHead key={header} scope="col" className="label-caps h-10 px-3 text-muted-foreground">
                        {header}
                      </TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.content.map((log) => (
                    <TableRow key={log.id} className="align-top hover:bg-accent/60">
                      <TableCell className="h-10 px-3 tabular-nums">{formatDateTime(log.occurredAt)}</TableCell>
                      <TableCell className="px-3 font-semibold">{log.actorUsername ?? '—'}</TableCell>
                      <TableCell className="px-3">
                        <ActionBadge action={log.action} />
                      </TableCell>
                      <TableCell className="px-3">{entity(log)}</TableCell>
                      <TableCell className="max-w-md px-3 whitespace-normal">
                        <Changes log={log} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <ul className="divide-y md:hidden">
              {data.content.map((log) => (
                <li key={log.id} className="flex flex-col gap-2 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="font-semibold">{entity(log)}</p>
                      <p className="text-xs text-muted-foreground tabular-nums">
                        {log.actorUsername ?? '—'} · {formatDateTime(log.occurredAt)}
                      </p>
                    </div>
                    <ActionBadge action={log.action} />
                  </div>
                  <Changes log={log} />
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      {data && data.totalPages > 1 && (
        <nav aria-label="Halaman audit log" className="flex items-center justify-between gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
            <ChevronLeft />
            Sebelumnya
          </Button>
          <span className="text-sm text-muted-foreground tabular-nums">
            Halaman {page + 1} dari {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
            Berikutnya
            <ChevronRight />
          </Button>
        </nav>
      )}
    </div>
  )
}
