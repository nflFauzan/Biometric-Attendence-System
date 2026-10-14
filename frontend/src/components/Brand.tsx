import { cn } from '@/lib/utils'

/**
 * Tanda brand sementara: kotak bergaris vertikal (motif spandek) dengan garis emas di atas.
 * Bukan logo resmi; diganti file SVG dari klien setelah tersedia (docs/pertanyaan-terbuka.md).
 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={cn('relative inline-block size-8 shrink-0 overflow-hidden rounded-md bg-steel-600', className)}
    >
      <span className="absolute inset-x-0 top-0 h-1 bg-gold-500" />
      <span className="motif-spandek absolute inset-x-1.5 top-2 bottom-1.5 text-white/70 [background-size:6px_100%]" />
    </span>
  )
}

/** Wordmark "TAKKA STEEL" dengan keterangan aplikasi. `tone` menyesuaikan latar terang/gelap. */
export function Wordmark({ tone = 'light', className }: { tone?: 'light' | 'dark'; className?: string }) {
  return (
    <span className={cn('flex items-center gap-2.5', className)}>
      <BrandMark />
      <span className="flex flex-col leading-none">
        <span
          className={cn(
            'font-heading text-[15px] font-extrabold tracking-wide',
            tone === 'dark' ? 'text-white' : 'text-primary',
          )}
        >
          TAKKA STEEL
        </span>
        <span
          className={cn(
            'mt-1 font-heading text-[9.5px] font-medium tracking-[0.18em] uppercase',
            tone === 'dark' ? 'text-steel-300' : 'text-muted-foreground',
          )}
        >
          Absensi &amp; Penggajian
        </span>
      </span>
    </span>
  )
}
