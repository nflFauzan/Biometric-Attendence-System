import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { Card } from '@/components/ui/card'
import { Table, TableHeader, TableRow } from '@/components/ui/table'

describe('Table and Card Glass Components (21st.dev)', () => {
  it('Table container memiliki rounded-[20px] dan border-white/10', () => {
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <th>Header</th>
          </TableRow>
        </TableHeader>
      </Table>,
    )
    const container = screen.getByRole('table').closest('[data-slot="table-container"]')
    expect(container).not.toBeNull()
    expect(container?.className).toContain('rounded-[20px]')
    expect(container?.className).toContain('border-white/10')
  })

  it('TableHeader memiliki kelas sticky dan glass-head', () => {
    render(
      <Table>
        <TableHeader>
          <TableRow>
            <th>Header</th>
          </TableRow>
        </TableHeader>
      </Table>,
    )
    const thead = screen.getByRole('table').querySelector('thead')
    expect(thead).not.toBeNull()
    expect(thead?.className).toContain('glass-head')
    expect(thead?.className).toContain('sticky')
  })

  it('Card menggunakan token gw-panel gw-glass dan rounded-[24px]', () => {
    render(<Card>Isi kartu</Card>)
    const card = screen.getByText('Isi kartu')
    expect(card).toHaveClass('gw-panel')
    expect(card).toHaveClass('gw-glass')
    expect(card.className).toContain('rounded-[24px]')
  })
})
