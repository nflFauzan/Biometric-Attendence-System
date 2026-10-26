import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { CredentialMessageDialog } from '@/components/CredentialMessageDialog'
import { credentialMessage } from '@/lib/credentials'
import { whatsAppSendUrl } from '@/lib/whatsapp'

const message = {
  kind: 'created' as const,
  name: 'Eni Contoh',
  username: 'eni@takka.id',
  password: 'eni-006-takka',
  phone: '0812-0000-0006',
}

const expectedText = () => credentialMessage({ ...message, origin: window.location.origin })

describe('Jendela pesan kredensial (K-133)', () => {
  afterEach(() => vi.restoreAllMocks())

  it('menampilkan pesan siap kirim dan peringatan sekali tampil', () => {
    render(<CredentialMessageDialog message={message} onClose={() => {}} />)
    const box = screen.getByLabelText('Pesan untuk karyawan')
    expect(box).toHaveValue(expectedText())
    expect(box).toHaveAttribute('readonly')
    expect(
      screen.getByText('Password ini hanya ditampilkan sekali. Untuk mengirim ulang, reset password.'),
    ).toBeInTheDocument()
  })

  it('Salin pesan menyalin teks ke clipboard', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    render(<CredentialMessageDialog message={message} onClose={() => {}} />)
    fireEvent.click(screen.getByRole('button', { name: 'Salin pesan' }))
    expect(writeText).toHaveBeenCalledWith(expectedText())
    expect(await screen.findByText('Pesan disalin')).toBeInTheDocument()
  })

  it('Kirim WA membuka wa.me ke nomor karyawan di tab baru', () => {
    render(<CredentialMessageDialog message={message} onClose={() => {}} />)
    const link = screen.getByRole('link', { name: 'Kirim WA' })
    expect(link).toHaveAttribute('href', whatsAppSendUrl(expectedText(), '0812-0000-0006'))
    expect(link).toHaveAttribute('target', '_blank')
  })

  it('tidak menyimpan password di localStorage', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    render(<CredentialMessageDialog message={message} onClose={() => {}} />)
    fireEvent.click(screen.getByRole('link', { name: 'Kirim WA' }))
    expect(setItem).not.toHaveBeenCalled()
  })
})
