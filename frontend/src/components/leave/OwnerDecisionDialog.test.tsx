import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { sampleRequest } from '@/components/leave/fixtures'
import { OwnerDecisionDialog } from '@/components/leave/OwnerDecisionDialog'
import { mockFetch } from '@/test/fetchMock'

function open(onDone = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <OwnerDecisionDialog request={sampleRequest()} onClose={() => {}} onDone={onDone} />
    </QueryClientProvider>,
  )
  const dialog = screen.getByRole('dialog')
  fireEvent.change(within(dialog).getByLabelText(/Cara keputusan/), { target: { value: 'WHATSAPP' } })
  fireEvent.change(within(dialog).getByLabelText(/Waktu keputusan/), { target: { value: '2026-10-24T14:05' } })
  fireEvent.change(within(dialog).getByLabelText(/Catatan/), { target: { value: 'Owner setuju lewat WA' } })
  return { dialog, onDone }
}

describe('OwnerDecisionDialog', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('blocks WhatsApp without evidence and does not call fetch', () => {
    const fetch = mockFetch({})
    const { dialog } = open()
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan keputusan' }))
    expect(screen.getByText('Bukti wajib diunggah')).toBeInTheDocument()
    expect(fetch).not.toHaveBeenCalled()
  })

  it('posts multipart with decision and evidence', async () => {
    const fetch = mockFetch({
      'POST /api/leave-requests/5/owner-decision': () => ({ status: 200, body: sampleRequest({ status: 'DISETUJUI' }) }),
    })
    document.cookie = 'XSRF-TOKEN=abc'
    const { dialog, onDone } = open()
    const file = new File(['%PDF-1.4'], 'wa.pdf', { type: 'application/pdf' })
    fireEvent.change(within(dialog).getByLabelText(/Bukti/), { target: { files: [file] } })
    fireEvent.click(within(dialog).getByRole('button', { name: 'Simpan keputusan' }))

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    const call = fetch.mock.calls.find(([url]) => url === '/api/leave-requests/5/owner-decision')!
    const form = call[1]!.body as FormData
    expect(form.get('evidence')).toBe(file)
    const decision = form.get('decision') as Blob
    expect(decision.type).toBe('application/json')
    expect(JSON.parse(await decision.text())).toEqual({
      approved: true,
      method: 'WHATSAPP',
      decidedAt: '2026-10-24T14:05',
      note: 'Owner setuju lewat WA',
    })
  })
})
