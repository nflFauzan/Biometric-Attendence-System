import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ApprovalTimeline } from '@/components/leave/ApprovalTimeline'
import { hrApproval, sampleRequest } from '@/components/leave/fixtures'

describe('ApprovalTimeline', () => {
  it('shows a draft as not yet sent with no active step', () => {
    render(<ApprovalTimeline request={sampleRequest({ status: 'DRAFT', submittedAt: null, submittedBy: null, approvals: [] })} />)
    expect(screen.getByText('Diajukan').closest('li')).toHaveTextContent('Belum dikirim')
    expect(screen.queryByText('Sedang ditinjau')).not.toBeInTheDocument()
  })

  it('shows HR then Owner steps for double approval', () => {
    render(<ApprovalTimeline request={sampleRequest({ approvals: [hrApproval] })} />)
    expect(screen.getByText(/Sari HR/)).toBeInTheDocument()
    expect(screen.getByText(/lewat web/)).toBeInTheDocument()
    const owner = screen.getByText('Ditinjau Owner').closest('li')!
    expect(owner).toHaveTextContent('Sedang ditinjau')
    expect(screen.getByText('Ditinjau HR').closest('li')).not.toHaveTextContent('Sedang ditinjau')
  })

  it('shows recorded owner decision', () => {
    const ownerApproval = {
      ...hrApproval,
      stage: 'OWNER' as const,
      decidedBy: 'OWNER',
      decidedByName: 'Owner',
      method: 'WHATSAPP' as const,
      hasEvidence: true,
    }
    render(
      <ApprovalTimeline request={sampleRequest({ status: 'DISETUJUI', nextStage: null, approvals: [hrApproval, ownerApproval] })} />,
    )
    expect(screen.getByText(/lewat WhatsApp/)).toBeInTheDocument()
    expect(screen.getByText(/dicatat oleh Sari HR/)).toBeInTheDocument()
    expect(screen.queryByText('Sedang ditinjau')).not.toBeInTheDocument()
  })

  it('omits the Owner step for single approval types', () => {
    render(<ApprovalTimeline request={sampleRequest({ requiresOwnerApproval: false, status: 'PERLU_TINJAU_HR', nextStage: 'HR' })} />)
    expect(screen.queryByText('Ditinjau Owner')).not.toBeInTheDocument()
    expect(screen.getByText('Ditinjau HR').closest('li')).toHaveTextContent('Sedang ditinjau')
  })

  it('menandai tahap HR sebagai diputus langsung oleh Owner', () => {
    const owner = { ...hrApproval, stage: 'OWNER' as const, decidedBy: 'OWNER', decidedByName: 'Owner' }
    render(<ApprovalTimeline request={sampleRequest({ status: 'DISETUJUI', nextStage: null, approvals: [owner] })} />)
    const hr = screen.getByText('Ditinjau HR').closest('li')!
    expect(hr).toHaveTextContent('Disetujui langsung oleh Owner')
    expect(hr).not.toHaveTextContent('Dilewati')
  })

  it('menandai tahap HR sebagai ditolak langsung oleh Owner', () => {
    const owner = { ...hrApproval, stage: 'OWNER' as const, approved: false, decidedBy: 'OWNER', decidedByName: 'Owner' }
    render(<ApprovalTimeline request={sampleRequest({ status: 'DITOLAK', nextStage: null, approvals: [owner] })} />)
    expect(screen.getByText('Ditinjau HR').closest('li')).toHaveTextContent('Ditolak langsung oleh Owner')
  })

  it('marks the Owner step as skipped after rejection at the HR stage', () => {
    const rejected = { ...hrApproval, approved: false }
    render(<ApprovalTimeline request={sampleRequest({ status: 'DITOLAK', nextStage: null, approvals: [rejected] })} />)
    const owner = screen.getByText('Ditinjau Owner').closest('li')!
    expect(owner).toHaveTextContent('Dilewati')
    expect(owner).not.toHaveTextContent('Sedang ditinjau')
  })
})
