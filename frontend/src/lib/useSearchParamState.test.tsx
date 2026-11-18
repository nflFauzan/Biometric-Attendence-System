import { act, render, screen } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'

import { useSearchParamState } from '@/lib/useSearchParamState'

let setter: (value: string) => void = () => {}

function Probe() {
  const [value, set] = useSearchParamState('from', '2026-10-01')
  setter = set
  const location = useLocation()
  return (
    <>
      <p data-testid="value">{value}</p>
      <p data-testid="search">{location.search}</p>
    </>
  )
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Probe />
    </MemoryRouter>,
  )
}

describe('useSearchParamState', () => {
  it('membaca nilai dari query string', () => {
    renderAt('/x?from=2026-11-01&a=1')
    expect(screen.getByTestId('value')).toHaveTextContent('2026-11-01')
  })

  it('memakai nilai bawaan bila param tidak ada', () => {
    renderAt('/x')
    expect(screen.getByTestId('value')).toHaveTextContent('2026-10-01')
  })

  it('menulis nilai baru dan mempertahankan param lain', () => {
    renderAt('/x?from=2026-11-01&a=1')
    act(() => setter('2026-11-05'))
    expect(screen.getByTestId('search')).toHaveTextContent('?from=2026-11-05&a=1')
    expect(screen.getByTestId('value')).toHaveTextContent('2026-11-05')
  })

  it('menghapus param bila diisi nilai bawaan atau kosong', () => {
    renderAt('/x?from=2026-11-01&a=1')
    act(() => setter('2026-10-01'))
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?a=1$/)
    act(() => setter('2026-11-03'))
    act(() => setter(''))
    expect(screen.getByTestId('search')).toHaveTextContent(/^\?a=1$/)
  })
})
