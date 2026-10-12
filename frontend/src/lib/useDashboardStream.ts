import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'

/** Isi event dari backend; hanya berarti "ada perubahan" (+ sedikit konteks untuk notifikasi). */
export interface StreamEvent {
  type: 'CONNECTED' | 'SCAN' | 'CORRECTION' | 'DEVICE_STATUS' | 'LEAVE'
  action?: 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CANCELLED' | 'ATTACHMENT_ADDED'
  requestId?: number
  employeeName?: string
  leaveType?: string
}

type Listener = (event: StreamEvent) => void

// Satu koneksi SSE dipakai bersama oleh semua komponen (layout + halaman), agar tidak membuka banyak koneksi.
let source: EventSource | null = null
let users = 0
let connected = false
const eventListeners = new Set<Listener>()
const statusListeners = new Set<(connected: boolean) => void>()

function setConnected(value: boolean) {
  connected = value
  statusListeners.forEach((listener) => listener(value))
}

function open() {
  if (source || typeof EventSource === 'undefined') {
    return
  }
  source = new EventSource('/api/dashboard/stream', { withCredentials: true })
  source.onopen = () => setConnected(true)
  source.onerror = () => setConnected(false)
  source.addEventListener('update', (message) => {
    let event: StreamEvent
    try {
      event = JSON.parse((message as MessageEvent<string>).data) as StreamEvent
    } catch {
      return
    }
    eventListeners.forEach((listener) => listener(event))
  })
}

function close() {
  source?.close()
  source = null
  setConnected(false)
}

/**
 * Berlangganan pemberitahuan real-time dari backend (SSE, DASH-006; notifikasi izin K-081). Data terbaru tetap
 * diambil ulang lewat API biasa. EventSource menyambung ulang sendiri bila koneksi putus.
 */
export function useDashboardStream(onEvent?: Listener): boolean {
  const queryClient = useQueryClient()
  const [isConnected, setIsConnected] = useState(connected)
  const onEventRef = useRef(onEvent)

  useEffect(() => {
    onEventRef.current = onEvent
  }, [onEvent])

  useEffect(() => {
    const listener: Listener = (event) => {
      void queryClient.invalidateQueries({ queryKey: ['/api/dashboard/summary'] })
      void queryClient.invalidateQueries({ queryKey: ['/api/attendance'] })
      void queryClient.invalidateQueries({ queryKey: ['/api/devices'] })
      if (event.type === 'LEAVE') {
        void queryClient.invalidateQueries({ queryKey: ['/api/leave-requests'] })
      }
      onEventRef.current?.(event)
    }
    eventListeners.add(listener)
    statusListeners.add(setIsConnected)
    users += 1
    open()
    return () => {
      eventListeners.delete(listener)
      statusListeners.delete(setIsConnected)
      users -= 1
      if (users === 0) {
        close()
      }
    }
  }, [queryClient])

  return isConnected
}
