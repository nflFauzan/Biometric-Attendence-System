import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  description?: ReactNode
  actions?: ReactNode
}

/** Judul halaman (token h1) dengan keterangan dan aksi di kanan (docs/design-system.md §4.4). */
export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-x-6 gap-y-3">
      <div className="min-w-0">
        <h1 className="text-xl leading-7 font-bold text-steel-800 md:text-2xl md:leading-8 dark:text-foreground">{title}</h1>
        {description && <p className="mt-1 max-w-[70ch] text-sm text-muted-foreground">{description}</p>}
      </div>
      {actions}
    </div>
  )
}
