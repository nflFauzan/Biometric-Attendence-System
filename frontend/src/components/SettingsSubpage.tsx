import { ArrowLeft } from 'lucide-react'
import { Link, Outlet } from 'react-router'

/** Kerangka sub-halaman Pengaturan: tautan kembali ke daftar kartu, lalu halaman aslinya (K-145). */
export function SettingsSubpage() {
  return (
    <div className="flex flex-col gap-4">
      <Link
        to="/pengaturan"
        aria-label="Kembali ke Pengaturan"
        className="inline-flex min-h-10 w-fit items-center gap-1.5 rounded-md text-sm font-semibold text-muted-foreground transition-colors hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        Pengaturan
      </Link>
      <Outlet />
    </div>
  )
}
