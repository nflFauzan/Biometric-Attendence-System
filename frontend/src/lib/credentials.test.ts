import { describe, expect, it } from 'vitest'

import { credentialMessage, suggestLocalPart, suggestPassword, suggestUsername } from '@/lib/credentials'

describe('suggestLocalPart', () => {
  it.each([
    ['Muhammad Dafa', 'dafa'],
    ['Siti Balqis', 'balqis'],
    ['M Rochmat', 'rochmat'],
    ['M. Rochmat', 'rochmat'],
    ['Eni Daniyati', 'eni'],
    ["O'Neil Putra", 'oneil'],
    ['Siti', 'siti'],
    ['  ', ''],
  ])('%s → %s', (name, expected) => {
    expect(suggestLocalPart(name)).toBe(expected)
  })
})

describe('suggestUsername', () => {
  it('menambahkan domain @takka.id', () => {
    expect(suggestUsername('Eni Daniyati')).toBe('eni@takka.id')
  })

  it('nama kosong → kosong', () => {
    expect(suggestUsername('')).toBe('')
  })
})

describe('suggestPassword', () => {
  it('nama-ID-takka untuk karyawan', () => {
    expect(suggestPassword('eni@takka.id', '006')).toBe('eni-006-takka')
  })

  it('nama-takka untuk akun tanpa karyawan', () => {
    expect(suggestPassword('owner', null)).toBe('owner-takka')
  })

  it('username kosong → kosong', () => {
    expect(suggestPassword('', '006')).toBe('')
  })
})

const LINE = '━━━━━━━━━━━━━━━━━━━━━'

describe('credentialMessage', () => {
  it('versi akun baru sama persis dengan spec §6', () => {
    const text = credentialMessage({
      kind: 'created',
      name: 'Eni Daniyati',
      username: 'eni@takka.id',
      password: 'eni-006-takka',
      origin: 'https://absen.example',
    })
    expect(text).toBe(
      [
        '*Halo Kak Eni Daniyati!*',
        '',
        'Akun *Sistem Absensi Takka Steel* Kakak sudah dibuat oleh Admin.',
        '',
        'Berikut kredensial untuk masuk:',
        LINE,
        '*Link Login*      : https://absen.example/login',
        '*Username*        : eni@takka.id',
        '*Password*        : eni-006-takka',
        LINE,
        '',
        '*Catatan:*',
        'Silakan login menggunakan password di atas, lalu Kakak akan diminta membuat password baru pilihan sendiri.',
        '',
        '_Salam hangat,_',
        '*Admin Takka Steel*',
      ].join('\n'),
    )
  })

  it('versi reset memakai kalimat reset dan label Password Baru', () => {
    const text = credentialMessage({
      kind: 'reset',
      name: 'Eni Daniyati',
      username: 'eni@takka.id',
      password: 'eni-006-takka',
      origin: 'https://absen.example',
    })
    expect(text).toContain('Password akun *Sistem Absensi Takka Steel* Kakak telah direset oleh Admin.')
    expect(text).toContain('kredensial baru untuk masuk')
    expect(text).toContain('*Password Baru*   : eni-006-takka')
    expect(text).not.toContain('sudah dibuat')
  })
})
