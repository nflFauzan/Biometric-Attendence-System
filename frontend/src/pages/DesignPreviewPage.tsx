import GlassWorkspace from '@/components/ui/adjustable-glass-dashboard'
import { loadWallpaper } from '@/lib/glassTheme'

export function DesignPreviewPage() {
  const currentBg = loadWallpaper()

  return (
    <div className="w-full min-h-screen bg-neutral-950">
      <GlassWorkspace
        brand="Takka Steel Attendance"
        userName="Admin Takka"
        backgroundImage={currentBg}
        balance={142}
        balanceChange="+94.7% hadir hari ini"
        account="Pabrik Utama & Cabang"
        periodLabel="November 2026"
        footerNote="* Data absensi & penggajian sinkron otomatis dari mesin X401"
        savingsNote="Target Kehadiran Bulanan"
        transactions={[
          {
            id: 'tx-1',
            name: 'Budi Santoso',
            category: 'Hadir Tepat Waktu',
            date: 'Hari ini, 07:54',
            amount: 1,
            status: 'Completed',
          },
          {
            id: 'tx-2',
            name: 'Siti Aminah',
            category: 'Lembur 2 Jam',
            date: 'Kemarin, 19:00',
            amount: 2,
            status: 'Completed',
          },
          {
            id: 'tx-3',
            name: 'Agus Setiawan',
            category: 'Izin Terencana',
            date: 'Hari ini',
            amount: 1,
            status: 'Pending',
          },
        ]}
        goals={[
          {
            id: 'g-1',
            name: 'Target Kehadiran Pabrik',
            target: 150,
            current: 142,
            date: 'Nov 2026',
          },
          {
            id: 'g-2',
            name: 'Penyelesaian Klaim Lembur',
            target: 10,
            current: 8,
            date: 'Bulan Ini',
          },
        ]}
      />
    </div>
  )
}
export default DesignPreviewPage
