import { afterEach, describe, expect, it, vi } from 'vitest'

import { sampleRequest } from '@/components/leave/fixtures'
import { shareLetter } from '@/lib/leaveLetter'

describe('shareLetter', () => {
  afterEach(() => vi.unstubAllGlobals())

  const stubFetch = () =>
    vi.stubGlobal('fetch', vi.fn(async () => new Response(new Blob(['%PDF'], { type: 'application/pdf' }), { status: 200 })))

  it('shares the file when the browser can share files', async () => {
    stubFetch()
    const share = vi.fn(async () => {})
    vi.stubGlobal('navigator', { canShare: () => true, share })
    expect(await shareLetter('mine', sampleRequest())).toBe('shared')
    expect(share).toHaveBeenCalled()
  })

  it('falls back to a download', async () => {
    stubFetch()
    vi.stubGlobal('navigator', {})
    URL.createObjectURL = vi.fn(() => 'blob:x')
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    expect(await shareLetter('mine', sampleRequest())).toBe('downloaded')
    expect(click).toHaveBeenCalled()
  })
})
