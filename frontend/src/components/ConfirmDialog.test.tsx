import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ConfirmDialog } from '@/components/ConfirmDialog'

const noop = () => {}

describe('ConfirmDialog', () => {
  it('teks sibuk bawaan Menghapus dan bisa diganti busyLabel', () => {
    const { rerender } = render(<ConfirmDialog open busy title="t" description="d" onConfirm={noop} onCancel={noop} />)
    expect(screen.getByRole('button', { name: 'Menghapus…' })).toBeDisabled()
    rerender(<ConfirmDialog open busy busyLabel="Menarik…" title="t" description="d" onConfirm={noop} onCancel={noop} />)
    expect(screen.getByRole('button', { name: 'Menarik…' })).toBeDisabled()
  })
})
