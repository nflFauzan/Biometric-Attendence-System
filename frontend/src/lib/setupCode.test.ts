import { describe, expect, it } from 'vitest'

import { buildSetupCode, normalizeServerUrl } from '@/lib/setupCode'

describe('setupCode', () => {
  it('builds BAS1 code that decodes back to the payload', () => {
    const code = buildSetupCode('http://192.168.43.20:8888', 'RK-7F2A9C', 'k'.repeat(43))
    expect(code.startsWith('BAS1.')).toBe(true)
    expect(code).not.toMatch(/[+/=]/)
    const json = JSON.parse(atob(code.slice(5).replace(/-/g, '+').replace(/_/g, '/')))
    expect(json).toEqual({ server: 'http://192.168.43.20:8888', device: 'RK-7F2A9C', key: 'k'.repeat(43) })
  })

  it('normalizes server url', () => {
    expect(normalizeServerUrl(' http://10.0.0.5:8888/ ')).toBe('http://10.0.0.5:8888')
    expect(normalizeServerUrl('https://absen.example.com')).toBe('https://absen.example.com')
    expect(normalizeServerUrl('10.0.0.5:8888')).toBeNull()
    expect(normalizeServerUrl('http://')).toBeNull()
  })
})
