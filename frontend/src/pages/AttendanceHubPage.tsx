import { PageHeader } from '@/components/PageHeader'
import { PageTabs, useActiveTab } from '@/components/PageTabs'
import { usePeriodFilter } from '@/components/PeriodFilter'
import { useFixCounts } from '@/lib/fixCounts'
import { AttendanceDailyTab } from '@/pages/AttendancePage'
import { FixListTab } from '@/pages/FixListTab'
import { OvertimeTab } from '@/pages/OvertimePage'

/** Absensi (K-145): rekap harian, hal yang perlu dibereskan, dan validasi lembur dalam satu menu bertab. */
export function AttendanceHubPage() {
  const counts = useFixCounts(usePeriodFilter())
  const fixTotal = (counts.reviewDays.count ?? 0) + (counts.heldPending.count ?? 0) + (counts.unmatched.count ?? 0)
  const tabs = [
    { value: 'harian', label: 'Harian' },
    { value: 'bereskan', label: 'Perlu Dibereskan', badge: fixTotal },
    { value: 'lembur', label: 'Lembur', badge: counts.overtimePending.count ?? 0 },
  ]
  const active = useActiveTab(tabs)

  return (
    <div className="flex flex-col gap-5">
      <PageHeader title="Absensi" />
      <PageTabs tabs={tabs} label="Sub menu absensi" />
      {active === 'harian' && <AttendanceDailyTab />}
      {active === 'bereskan' && <FixListTab />}
      {active === 'lembur' && <OvertimeTab />}
    </div>
  )
}
