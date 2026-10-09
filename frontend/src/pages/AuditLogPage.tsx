import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import type { AuditLog, PageResponse } from '@/lib/types'

const ENTITY_LABELS: Record<string, string> = {
  BRANCH: 'Cabang',
  EMPLOYEE_CATEGORY: 'Kategori',
  EMPLOYEE: 'Karyawan',
  DEVICE: 'Mesin',
  USER_ACCOUNT: 'Akun',
}

export function AuditLogPage() {
  const [page, setPage] = useState(0)
  const { data, isPending } = useQuery({
    queryKey: ['/api/audit-logs', page],
    queryFn: () => api<PageResponse<AuditLog>>(`/api/audit-logs?page=${page}&size=50`),
  })

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="text-xl font-semibold">Audit Log</h1>
        <p className="text-sm text-muted-foreground">Riwayat perubahan data: siapa, kapan, dan nilai sebelum/sesudah.</p>
      </div>
      <div className="rounded-lg border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Waktu</TableHead>
              <TableHead>Pengguna</TableHead>
              <TableHead>Aksi</TableHead>
              <TableHead>Data</TableHead>
              <TableHead>Perubahan</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isPending && (
              <TableRow>
                <TableCell colSpan={5}>Memuat…</TableCell>
              </TableRow>
            )}
            {data?.content.map((log) => (
              <TableRow key={log.id}>
                <TableCell className="whitespace-nowrap">{new Date(log.occurredAt).toLocaleString('id-ID')}</TableCell>
                <TableCell>{log.actorUsername ?? '—'}</TableCell>
                <TableCell>{log.action}</TableCell>
                <TableCell>
                  {ENTITY_LABELS[log.entityType] ?? log.entityType} #{log.entityId}
                </TableCell>
                <TableCell className="max-w-md">
                  <details>
                    <summary className="cursor-pointer text-xs text-muted-foreground">Lihat</summary>
                    <pre className="mt-2 whitespace-pre-wrap break-all text-xs">
                      {`Sebelum: ${log.beforeData ?? '—'}\nSesudah: ${log.afterData ?? '—'}`}
                    </pre>
                  </details>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Sebelumnya
          </Button>
          <span className="text-sm">
            Halaman {page + 1} dari {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>
            Berikutnya
          </Button>
        </div>
      )}
    </div>
  )
}
