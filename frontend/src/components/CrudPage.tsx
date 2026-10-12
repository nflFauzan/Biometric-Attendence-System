import { useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, MinusCircle, Pencil, Plus } from 'lucide-react'
import { useState, type ReactNode } from 'react'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { FormDialog, type FieldDef, type FormValues } from '@/components/FormDialog'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { cn } from '@/lib/utils'

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
  /** `primary` = judul kartu di HP, `status` = badge di pojok kartu. */
  role?: 'primary' | 'status'
  /** Kode/serial ditampilkan dengan font mono. */
  mono?: boolean
}

interface CrudPageProps<T extends { id: number }> {
  title: string
  description?: string
  endpoint: string
  columns: Column<T>[]
  fields: (editing: T | null) => FieldDef[]
  emptyValues: FormValues
  toValues: (row: T) => FormValues
  toBody: (values: FormValues, editing: T | null) => unknown
  rowActions?: (row: T) => ReactNode
  /** Query string untuk daftar, mis. { year: '2026' }. PUT/POST tetap ke endpoint tanpa query. */
  listParams?: Record<string, string>
  /** Kontrol tambahan di atas tabel, mis. pemilih tahun. */
  toolbar?: ReactNode
}

export function ActiveBadge({ active }: { active: boolean }) {
  return active ? (
    <Badge variant="success">
      <CheckCircle2 />
      Aktif
    </Badge>
  ) : (
    <Badge variant="neutral">
      <MinusCircle />
      Nonaktif
    </Badge>
  )
}

/**
 * Halaman daftar + tambah/ubah yang dipakai bersama oleh menu master data.
 * Desktop/tablet: tabel kerapatan nyaman. HP: daftar kartu (docs/design-system.md §6.4).
 */
export function CrudPage<T extends { id: number }>(props: CrudPageProps<T>) {
  const queryClient = useQueryClient()
  const query = props.listParams ? `?${new URLSearchParams(props.listParams)}` : ''
  const { data, isPending, error } = useQuery({
    queryKey: [props.endpoint, props.listParams ?? null],
    queryFn: () => api<T[]>(`${props.endpoint}${query}`),
  })
  const [dialog, setDialog] = useState<{ editing: T | null } | null>(null)

  const save = async (values: FormValues) => {
    const editing = dialog?.editing ?? null
    const body = props.toBody(values, editing)
    if (editing) {
      await api(`${props.endpoint}/${editing.id}`, { method: 'PUT', body })
    } else {
      await api(props.endpoint, { method: 'POST', body })
    }
    await queryClient.invalidateQueries({ queryKey: [props.endpoint] })
  }

  const openCreate = () => setDialog({ editing: null })
  const primary = props.columns.find((column) => column.role === 'primary') ?? props.columns[0]
  const status = props.columns.find((column) => column.role === 'status')
  const details = props.columns.filter((column) => column !== primary && column !== status)

  const actions = (row: T) => (
    <>
      {props.rowActions?.(row)}
      <Button variant="ghost" size="sm" onClick={() => setDialog({ editing: row })}>
        <Pencil />
        Ubah
      </Button>
    </>
  )

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title={props.title}
        description={props.description}
        actions={
          <Button
            onClick={openCreate}
            className="max-md:fixed max-md:right-4 max-md:bottom-[calc(5rem+env(safe-area-inset-bottom))] max-md:z-20 max-md:shadow-lg"
          >
            <Plus />
            Tambah
          </Button>
        }
      />

      {props.toolbar}

      {error && <ErrorAlert message={error.message} />}

      <section className="overflow-hidden rounded-lg border bg-card">
        {data && (
          <p className="border-b px-4 py-2.5 text-xs text-muted-foreground tabular-nums">{data.length} data</p>
        )}
        {isPending && <SkeletonLines />}
        {data?.length === 0 && (
          <EmptyState
            message={`Belum ada data ${props.title.toLowerCase()}.`}
            action={
              <Button variant="secondary" size="sm" onClick={openCreate}>
                <Plus />
                Tambah {props.title.toLowerCase()}
              </Button>
            }
          />
        )}

        {data && data.length > 0 && (
          <>
            <div className="hidden md:block">
              <Table>
                <TableHeader>
                  <TableRow className="bg-steel-50 hover:bg-steel-50 dark:bg-secondary dark:hover:bg-secondary">
                    {props.columns.map((column) => (
                      <TableHead key={column.header} scope="col" className="label-caps h-11 px-4 text-muted-foreground">
                        {column.header}
                      </TableHead>
                    ))}
                    <TableHead className="w-0 px-4">
                      <span className="sr-only">Aksi</span>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {data.map((row) => (
                    <TableRow key={row.id} className="hover:bg-accent/60">
                      {props.columns.map((column) => (
                        <TableCell
                          key={column.header}
                          className={cn(
                            'h-[52px] px-4',
                            column === primary && 'font-semibold',
                            column.mono && 'font-mono text-[13px]',
                          )}
                        >
                          {column.cell(row)}
                        </TableCell>
                      ))}
                      <TableCell className="px-4">
                        <div className="flex justify-end gap-1">{actions(row)}</div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>

            <ul className="divide-y md:hidden">
              {data.map((row) => (
                <li key={row.id} className="flex flex-col gap-3 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <p className="min-w-0 font-semibold break-words">{primary.cell(row)}</p>
                    {status?.cell(row)}
                  </div>
                  {details.length > 0 && (
                    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
                      {details.map((column) => (
                        <div key={column.header} className="contents">
                          <dt className="text-muted-foreground">{column.header}</dt>
                          <dd className={cn('min-w-0 break-words', column.mono && 'font-mono text-[13px]')}>
                            {column.cell(row)}
                          </dd>
                        </div>
                      ))}
                    </dl>
                  )}
                  <div className="-ml-3 flex flex-wrap gap-1">{actions(row)}</div>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      {dialog && (
        <FormDialog
          open
          title={dialog.editing ? `Ubah ${props.title}` : `Tambah ${props.title}`}
          fields={props.fields(dialog.editing)}
          initialValues={dialog.editing ? props.toValues(dialog.editing) : props.emptyValues}
          onClose={() => setDialog(null)}
          onSubmit={save}
        />
      )}
    </div>
  )
}
