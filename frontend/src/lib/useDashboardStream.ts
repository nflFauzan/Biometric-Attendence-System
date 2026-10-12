import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

/**
 * Berlangganan pemberitahuan real-time dari backend (SSE, DASH-006). Event hanya berarti "ada perubahan";
 * data terbaru tetap diambil ulang lewat API biasa. EventSource menyambung ulang sendiri bila koneksi putus.
 */
export function useDashboardStream(): boolean {
  const queryClient = useQueryClient()
  const [connected, setConnected] = useState(false)

  useEffect(() => {
    if (typeof EventSource === 'undefined') {
      return
    }
    const source = new EventSource('/api/dashboard/stream', { withCredentials: true })
    source.onopen = () => setConnected(true)
    source.onerror = () => setConnected(false)
    source.addEventListener('update', () => {
      void queryClient.invalidateQueries({ queryKey: ['/api/dashboard/summary'] })
      void queryClient.invalidateQueries({ queryKey: ['/api/attendance'] })
      void queryClient.invalidateQueries({ queryKey: ['/api/devices'] })
    })
    return () => source.close()
  }, [queryClient])

  return connected
}
