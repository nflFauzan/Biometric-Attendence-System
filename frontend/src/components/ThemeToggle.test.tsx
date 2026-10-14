import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'

import { ThemeToggle } from '@/components/ThemeToggle'
import { THEME_KEY } from '@/lib/theme'

describe('tombol tema', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
  })

  it('menyebut tema aktif dan berganti terang → gelap → ikut perangkat', () => {
    render(<ThemeToggle />)
    const button = screen.getByRole('button', { name: 'Tema: Ikut perangkat. Ganti tema' })

    fireEvent.click(button)
    expect(localStorage.getItem(THEME_KEY)).toBe('light')
    expect(button).toHaveAccessibleName('Tema: Terang. Ganti tema')

    fireEvent.click(button)
    expect(document.documentElement).toHaveClass('dark')
    expect(button).toHaveAccessibleName('Tema: Gelap. Ganti tema')

    fireEvent.click(button)
    expect(localStorage.getItem(THEME_KEY)).toBe('system')
  })
})
