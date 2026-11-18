import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { fieldClass, selectClass } from '@/components/FormDialog'
import { Dialog, DialogContent, DialogTrigger } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'

describe('Form, Input & Dialog Glass Primitives (21st.dev)', () => {
  it('Input memiliki rounded-xl dan styling translucent border', () => {
    render(<Input placeholder="Ketik sesuatu" />)
    const input = screen.getByPlaceholderText('Ketik sesuatu')
    expect(input.className).toContain('rounded-xl')
    expect(input.className).toContain('border-white/20')
  })

  it('DialogContent menggunakan token gw-panel gw-glass dan rounded-[24px]', () => {
    render(
      <Dialog open>
        <DialogContent>
          <p>Konten Dialog</p>
        </DialogContent>
      </Dialog>,
    )
    const content = screen.getByText('Konten Dialog').closest('[data-slot="dialog-content"]')
    expect(content).not.toBeNull()
    expect(content?.className).toContain('gw-panel')
    expect(content?.className).toContain('gw-glass')
    expect(content?.className).toContain('rounded-[24px]')
  })

  it('fieldClass dan selectClass memiliki rounded-xl dan latar semi-transparan', () => {
    expect(fieldClass).toContain('rounded-xl')
    expect(fieldClass).toContain('border-white/20')
    expect(selectClass).toContain('rounded-xl')
    expect(selectClass).toContain('border-white/20')
  })
})
