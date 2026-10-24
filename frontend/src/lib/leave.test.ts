import { describe, expect, it } from 'vitest'

import { ApiError } from '@/lib/api'
import { evidenceExtension, formatLeaveDays, formatLeaveRange, inclusiveDays, wizardStepForError } from '@/lib/leave'

describe('inclusiveDays', () => {
  it('menghitung hari kalender inklusif', () => {
    expect(inclusiveDays('2026-11-03', '2026-11-03')).toBe(1)
    expect(inclusiveDays('2026-11-03', '2026-11-05')).toBe(3)
    expect(inclusiveDays('2026-10-30', '2026-11-02')).toBe(4)
  })
  it('null bila tanggal kosong atau terbalik', () => {
    expect(inclusiveDays('', '2026-11-05')).toBeNull()
    expect(inclusiveDays('2026-11-05', '2026-11-03')).toBeNull()
  })
})

describe('wizardStepForError', () => {
  const err = (status: number, msg: string) => new ApiError(status, msg)
  it('isian langkah 2 dan tanggal mengarah ke langkah 2', () => {
    expect(wizardStepForError(err(400, 'Alamat selama cuti wajib diisi'))).toBe(2)
    expect(wizardStepForError(err(400, 'No. HP wajib diisi'))).toBe(2)
    expect(wizardStepForError(err(400, 'Keperluan cuti wajib diisi'))).toBe(2)
    expect(wizardStepForError(err(400, 'Tanggal mulai wajib diisi'))).toBe(2)
  })
  it('409 mengarah ke langkah 2', () => {
    expect(wizardStepForError(err(409, 'Bertabrakan dengan pengajuan lain'))).toBe(2)
  })
  it('surat atau lampiran mengarah ke langkah 3', () => {
    expect(wizardStepForError(err(400, 'Jenis ini wajib surat pendukung'))).toBe(3)
    expect(wizardStepForError(err(400, 'Lampiran wajib diunggah'))).toBe(3)
  })
  it('lainnya tetap di langkah 4', () => {
    expect(wizardStepForError(err(400, 'Terjadi kesalahan'))).toBe(4)
    expect(wizardStepForError(err(500, 'Server error'))).toBe(4)
    expect(wizardStepForError(new Error('x'))).toBe(4)
  })
})

describe('draft tanpa tanggal', () => {
  it('rentang dan jumlah hari menampilkan Tanggal belum diisi', () => {
    expect(formatLeaveRange({ startDate: null, endDate: null })).toBe('Tanggal belum diisi')
    expect(formatLeaveDays({ startDate: null, endDate: null, calendarDays: 0, workingDays: null })).toBe('Tanggal belum diisi')
    expect(formatLeaveDays({ startDate: '2026-11-03', endDate: '2026-11-05', calendarDays: 3, workingDays: 2 })).toBe('3 hari (2 hari kerja)')
  })
})

describe('evidenceExtension', () => {
  it('menurunkan ekstensi dari tipe blob', () => {
    expect(evidenceExtension(new Blob([], { type: 'application/pdf' }))).toBe('.pdf')
    expect(evidenceExtension(new Blob([], { type: 'image/png' }))).toBe('.png')
    expect(evidenceExtension(new Blob([], { type: 'image/jpeg' }))).toBe('.jpg')
    expect(evidenceExtension(new Blob([], { type: 'application/octet-stream' }))).toBe('')
  })
})

describe('rentang maksimal', () => {
  it('400 Rentang izin maksimal mengarah ke langkah 2', () => {
    expect(wizardStepForError(new ApiError(400, 'Rentang izin maksimal 30 hari'))).toBe(2)
  })
})
