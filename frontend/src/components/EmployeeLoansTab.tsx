import { useQuery } from '@tanstack/react-query'
import { ArrowRight, CheckCircle2, Clock, XCircle, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api'
import { formatDate, formatRupiah } from '@/lib/format'
import type { EmployeeLoan } from '@/lib/types'

const STATUS: Record<EmployeeLoan['status'], { label: string; variant: 'warning' | 'success' | 'neutral'; icon: LucideIcon }> = {
  AKTIF: { label: 'Aktif', variant: 'warning', icon: Clock },
  LUNAS: { label: 'Lunas', variant: 'success', icon: CheckCircle2 },
  DIBATALKAN: { label: 'Dibatalkan', variant: 'neutral', icon: XCircle },
}

/** Kasbon satu karyawan (hanya lihat). Pencatatan dan pemotongan tetap di Gaji & Laporan, tab Kasbon (K-145). */
export function EmployeeLoansTab({ employeeId }: { employeeId: number }) {
  const { data, error, isPending } = useQuery({
    queryKey: ['/api/loans', 'employee', employeeId],
    queryFn: () => api<EmployeeLoan[]>(`/api/loans?employeeId=${employeeId}`),
  })

  const manage = (
    <Button variant="secondary" size="sm" asChild>
      <Link to="/gaji?tab=kasbon">
        Kelola kasbon
        <ArrowRight />
      </Link>
    </Button>
  )

  return (
    <section aria-labelledby="kasbon-karyawan" className="overflow-x-auto rounded-lg border glass">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-3.5">
        <h2 id="kasbon-karyawan" className="text-base font-semibold">
          Kasbon
        </h2>
        {manage}
      </div>
      {error && <ErrorAlert className="m-4" message={error.message} />}
      {isPending && <SkeletonLines count={2} />}
      {data?.length === 0 && <EmptyState message="Belum ada kasbon." />}
      {data && data.length > 0 && (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b text-left label-caps text-muted-foreground">
              <th className="px-5 py-2">Tanggal</th>
              <th className="px-3 py-2">No. kasbon</th>
              <th className="px-3 py-2 text-right">Jumlah</th>
              <th className="px-3 py-2 text-right">Cicilan</th>
              <th className="px-3 py-2 text-right">Sisa</th>
              <th className="px-5 py-2">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y">
            {data.map((loan) => (
              <tr key={loan.id}>
                <td className="px-5 py-3">{formatDate(loan.loanDate)}</td>
                <td className="px-3 py-3 font-mono text-[13px]">{loan.loanNumber}</td>
                <td className="px-3 py-3 text-right tabular-nums">{formatRupiah(loan.totalAmount)}</td>
                <td className="px-3 py-3 text-right tabular-nums">{formatRupiah(loan.installmentAmount)}</td>
                <td className="px-3 py-3 text-right font-semibold tabular-nums">{formatRupiah(loan.remainingAmount)}</td>
                <td className="px-5 py-3">
                  <StatusBadge status={loan.status} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

function StatusBadge({ status }: { status: EmployeeLoan['status'] }) {
  const { label, variant, icon: Icon } = STATUS[status]
  return (
    <Badge variant={variant}>
      <Icon aria-hidden="true" />
      {label}
    </Badge>
  )
}
