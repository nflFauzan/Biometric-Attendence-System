import { PageHeader } from '@/components/PageHeader'
import { PageTabs, useActiveTab } from '@/components/PageTabs'
import { LoansPage } from '@/pages/LoansPage'
import { ReportsPage } from '@/pages/ReportsPage'

const TABS = [
  { value: 'laporan', label: 'Laporan' },
  { value: 'kasbon', label: 'Kasbon' },
]

/** Gaji & Laporan (K-145): unduh laporan upah/gaji dan kelola kasbon yang dipotong dari upah, dalam satu menu. */
export function PayrollPage() {
  const tab = useActiveTab(TABS)
  return (
    <div className="flex flex-col gap-5">
      <PageHeader title="Gaji & Laporan" />
      <PageTabs tabs={TABS} label="Sub menu gaji" />
      {tab === 'laporan' && <ReportsPage />}
      {tab === 'kasbon' && <LoansPage />}
    </div>
  )
}
