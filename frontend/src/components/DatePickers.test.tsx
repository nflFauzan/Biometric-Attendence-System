import { fireEvent, render, screen, within } from '@testing-library/react'
import { useState, type ComponentType } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { DatePicker, DateTimePicker, TimePicker } from '@/components/DatePickers'
import { clearPicker, pickDate, pickDateTime, pickTime } from '@/test/pickers'

type PickerProps = { id: string; value: string; onChange: (v: string) => void; context?: string; clearable?: boolean; min?: string; max?: string; withSeconds?: boolean }

function Harness({ Picker, initial = '', onChange = () => {}, ...rest }: { Picker: ComponentType<PickerProps>; initial?: string; onChange?: (v: string) => void } & Partial<PickerProps>) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <label htmlFor="when">Waktu</label>
      <Picker
        id="when"
        value={value}
        context="Surat 0005"
        {...rest}
        onChange={(next) => {
          setValue(next)
          onChange(next)
        }}
      />
    </>
  )
}

const trigger = () => screen.getByLabelText('Waktu')

describe('DatePickers', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date(2026, 9, 26, 9, 0))
  })
  afterEach(() => vi.useRealTimers())

  describe('DatePicker', () => {
    it('shows placeholder, then the picked date', () => {
      const onChange = vi.fn()
      render(<Harness Picker={DatePicker} onChange={onChange} />)
      expect(trigger()).toHaveTextContent('Pilih tanggal')
      pickDate(trigger(), '2026-10-24')
      expect(onChange).toHaveBeenCalledWith('2026-10-24')
      expect(trigger()).toHaveTextContent('Sab, 24 Okt 2026')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('jumps across years and months for far dates such as a birth date', () => {
      const onChange = vi.fn()
      render(<Harness Picker={DatePicker} onChange={onChange} />)
      pickDate(trigger(), '1990-03-15')
      expect(onChange).toHaveBeenCalledWith('1990-03-15')
    })

    it('shows context and a chip that follows the selected day', () => {
      render(<Harness Picker={DatePicker} initial="2026-10-24" />)
      fireEvent.click(trigger())
      const sheet = screen.getByRole('dialog', { name: 'Pilih tanggal' })
      expect(within(sheet).getByText('Surat 0005')).toBeInTheDocument()
      expect(within(sheet).getByRole('button', { name: 'Sab, 24 Okt 2026' })).toHaveAttribute('aria-pressed', 'true')
      fireEvent.click(within(sheet).getByRole('button', { name: 'Sen, 12 Okt 2026' }))
      expect(within(sheet).getByText('Sen, 12 Okt 2026')).toBeInTheDocument()
    })

    it('blocks days, months and navigation outside min/max', () => {
      render(<Harness Picker={DatePicker} initial="2026-10-24" min="2026-10-05" max="2026-10-26" />)
      fireEvent.click(trigger())
      const sheet = screen.getByRole('dialog', { name: 'Pilih tanggal' })
      expect(within(sheet).getByRole('button', { name: 'Sen, 26 Okt 2026' })).toBeEnabled()
      expect(within(sheet).getByRole('button', { name: 'Sel, 27 Okt 2026' })).toBeDisabled()
      expect(within(sheet).getByRole('button', { name: 'Min, 4 Okt 2026' })).toBeDisabled()
      expect(within(sheet).getByRole('button', { name: 'Berikutnya' })).toBeDisabled()
      expect(within(sheet).getByRole('button', { name: 'Sebelumnya' })).toBeDisabled()

      fireEvent.click(within(sheet).getByRole('button', { name: /ganti bulan dan tahun/ }))
      expect(within(sheet).getByRole('button', { name: '2025' })).toBeDisabled()
      fireEvent.click(within(sheet).getByRole('button', { name: '2026' }))
      expect(within(sheet).getByRole('button', { name: 'November 2026' })).toBeDisabled()
      expect(within(sheet).getByRole('button', { name: 'Oktober 2026' })).toBeEnabled()
    })

    it('starts on the nearest allowed day when empty and today is out of range', () => {
      render(<Harness Picker={DatePicker} min="2026-11-03" />)
      fireEvent.click(trigger())
      expect(screen.getByText('Sel, 3 Nov 2026')).toBeInTheDocument()
    })

    it('offers Kosongkan only when clearable', () => {
      const onChange = vi.fn()
      const { unmount } = render(<Harness Picker={DatePicker} initial="2026-10-24" />)
      fireEvent.click(trigger())
      expect(screen.queryByRole('button', { name: 'Kosongkan' })).not.toBeInTheDocument()
      unmount()

      render(<Harness Picker={DatePicker} initial="2026-10-24" clearable onChange={onChange} />)
      clearPicker(trigger())
      expect(onChange).toHaveBeenCalledWith('')
      expect(trigger()).toHaveTextContent('Pilih tanggal')
    })
  })

  describe('TimePicker', () => {
    it('picks hour and minute and shows them in Indonesian format', () => {
      const onChange = vi.fn()
      render(<Harness Picker={TimePicker} onChange={onChange} />)
      expect(trigger()).toHaveTextContent('Pilih jam')
      pickTime(trigger(), '07:45')
      expect(onChange).toHaveBeenCalledWith('07:45')
      expect(trigger()).toHaveTextContent('07.45')
    })

    it('reads values with seconds from the API and clamps the wheels', () => {
      render(<Harness Picker={TimePicker} initial="08:30:00" />)
      expect(trigger()).toHaveTextContent('08.30')
      fireEvent.click(trigger())
      const hours = screen.getByRole('spinbutton', { name: 'Jam' })
      expect(hours).toHaveAttribute('aria-valuenow', '8')
      fireEvent.keyDown(hours, { key: 'End' })
      fireEvent.keyDown(hours, { key: 'ArrowDown' })
      expect(hours).toHaveAttribute('aria-valuenow', '23')
      fireEvent.keyDown(hours, { key: 'Home' })
      fireEvent.keyDown(hours, { key: 'ArrowUp' })
      expect(hours).toHaveAttribute('aria-valuenow', '0')
    })

    it('clears when clearable', () => {
      const onChange = vi.fn()
      render(<Harness Picker={TimePicker} initial="12:00" clearable onChange={onChange} />)
      clearPicker(trigger())
      expect(onChange).toHaveBeenCalledWith('')
    })
  })

  describe('DateTimePicker', () => {
    it('walks the date step then the time step and confirms', () => {
      const onChange = vi.fn()
      render(<Harness Picker={DateTimePicker} onChange={onChange} />)
      pickDateTime(trigger(), '2026-10-24T14:05')
      expect(onChange).toHaveBeenCalledWith('2026-10-24T14:05')
      expect(trigger()).toHaveTextContent('Sab, 24 Okt 2026 · 14.05')
    })

    it('goes back from the time step to the date step', () => {
      render(<Harness Picker={DateTimePicker} initial="2026-10-24T08:30" />)
      fireEvent.click(trigger())
      fireEvent.click(screen.getByRole('button', { name: /Pilih jam/ }))
      expect(screen.getByRole('dialog', { name: 'Pilih jam' })).toBeInTheDocument()
      expect(screen.getByRole('spinbutton', { name: 'Menit' })).toHaveAttribute('aria-valuenow', '30')
      fireEvent.click(screen.getByRole('button', { name: 'Kembali ke tanggal' }))
      expect(screen.getByRole('dialog', { name: 'Pilih tanggal' })).toBeInTheDocument()
    })

    it('adds a seconds wheel when withSeconds is set', () => {
      const onChange = vi.fn()
      render(<Harness Picker={DateTimePicker} initial="2026-10-20T07:00:15" withSeconds onChange={onChange} />)
      expect(trigger()).toHaveTextContent('Sel, 20 Okt 2026 · 07.00.15')
      pickDateTime(trigger(), '2026-10-21T06:30:45')
      expect(onChange).toHaveBeenCalledWith('2026-10-21T06:30:45')
    })
  })
})
