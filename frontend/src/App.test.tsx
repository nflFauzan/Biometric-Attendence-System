import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('menampilkan status terhubung saat health check backend berhasil', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }))
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Biometric Attendance System' })).toBeInTheDocument()
    expect(await screen.findByText('Status server: terhubung')).toBeInTheDocument()
  })

  it('menampilkan status tidak terhubung saat backend tidak bisa dihubungi', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    render(<App />)
    expect(await screen.findByText('Status server: tidak terhubung')).toBeInTheDocument()
  })
})
