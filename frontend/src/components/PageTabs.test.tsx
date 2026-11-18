import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'

import { PageTabs, useActiveTab } from '@/components/PageTabs'

const TABS = [
  { value: 'harian', label: 'Harian' },
  { value: 'bereskan', label: 'Perlu Dibereskan', badge: 3 },
  { value: 'lembur', label: 'Lembur', badge: 0 },
]

function Active() {
  return <p data-testid="active">{useActiveTab(TABS)}</p>
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PageTabs tabs={TABS} label="Sub menu absensi" />
      <Active />
    </MemoryRouter>,
  )
}

describe('PageTabs', () => {
  it('menandai tab aktif dari query string', () => {
    renderAt('/absensi?tab=lembur&from=2026-11-01&x=9')
    expect(screen.getByRole('tablist', { name: 'Sub menu absensi' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Lembur' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Harian' })).toHaveAttribute('aria-selected', 'false')
  })

  it('tautan tab membawa filter periode tetapi membuang param lain', () => {
    renderAt('/absensi?tab=lembur&from=2026-11-01&x=9')
    expect(screen.getByRole('tab', { name: 'Harian' })).toHaveAttribute('href', '/absensi?tab=harian&from=2026-11-01')
  })

  it('menampilkan badge hanya bila lebih dari 0', () => {
    renderAt('/absensi')
    expect(screen.getByRole('tab', { name: /Perlu Dibereskan/ })).toHaveTextContent('3')
    expect(screen.getByRole('tab', { name: 'Lembur' })).not.toHaveTextContent('0')
  })

  it('tab tak dikenal atau kosong jatuh ke tab pertama', () => {
    renderAt('/absensi?tab=ngawur')
    expect(screen.getByTestId('active')).toHaveTextContent('harian')
    expect(screen.getByRole('tab', { name: 'Harian' })).toHaveAttribute('aria-selected', 'true')
  })
})
