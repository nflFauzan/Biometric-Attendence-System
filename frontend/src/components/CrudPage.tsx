import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'

import { FormDialog, type FieldDef, type FormValues } from '@/components/FormDialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
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
}

export function ActiveBadge({ active }: { active: boolean }) {
  return <Badge variant={active ? 'secondary' : 'outline'}>{active ? 'Aktif' : 'Nonaktif'}</Badge>
}

/** Halaman daftar + tambah/ubah yang dipakai bersama oleh menu master data. */
export function CrudPage<T extends { id: number }>(props: CrudPageProps<T>) {
  const queryClient = useQueryClient()
  const { data, isPending, error } = useQuery({
    queryKey: [props.endpoint],
    queryFn: () => api<T[]>(props.endpoint),
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

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-semibold">{props.title}</h1>
          {props.description && <p className="text-sm text-muted-foreground">{props.description}</p>}
        </div>
        <Button onClick={() => setDialog({ editing: null })}>Tambah</Button>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error.message}</AlertDescription>
        </Alert>
      )}

      <div className="rounded-lg border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              {props.columns.map((column) => (
                <TableHead key={column.header}>{column.header}</TableHead>
              ))}
              <TableHead className="w-0" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {isPending && (
              <TableRow>
                <TableCell colSpan={props.columns.length + 1}>Memuat…</TableCell>
              </TableRow>
            )}
            {data?.length === 0 && (
              <TableRow>
                <TableCell colSpan={props.columns.length + 1} className="text-muted-foreground">
                  Belum ada data.
                </TableCell>
              </TableRow>
            )}
            {data?.map((row) => (
              <TableRow key={row.id}>
                {props.columns.map((column) => (
                  <TableCell key={column.header}>{column.cell(row)}</TableCell>
                ))}
                <TableCell className="flex justify-end gap-2">
                  {props.rowActions?.(row)}
                  <Button variant="outline" size="sm" onClick={() => setDialog({ editing: row })}>
                    Ubah
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>

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

