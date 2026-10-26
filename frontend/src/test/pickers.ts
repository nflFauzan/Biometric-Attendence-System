import { fireEvent, screen, within } from '@testing-library/react'

import { formatDayLabel } from '@/lib/format'

/*
 * Mengisi DatePicker / TimePicker / DateTimePicker lewat UI seperti pengguna:
 * buka sheet, lompat ke tahun dan bulan lewat judul kalender, ketuk tanggal, atur roda jam dengan keyboard.
 */

const MONTHS_LONG = ['Januari', 'Februari', 'Maret', 'April', 'Mei', 'Juni', 'Juli', 'Agustus', 'September', 'Oktober', 'November', 'Desember']

/** "YYYY-MM-DD" lewat DatePicker. */
export function pickDate(trigger: HTMLElement, iso: string) {
  fireEvent.click(trigger)
  const sheet = screen.getByRole('dialog', { name: 'Pilih tanggal' })
  chooseDay(sheet, iso)
  fireEvent.click(within(sheet).getByRole('button', { name: 'Pilih' }))
}

/** "HH:mm" lewat TimePicker. */
export function pickTime(trigger: HTMLElement, time: string) {
  fireEvent.click(trigger)
  const sheet = screen.getByRole('dialog', { name: 'Pilih jam' })
  setWheels(sheet, time)
  fireEvent.click(within(sheet).getByRole('button', { name: 'Pilih' }))
}

/** "YYYY-MM-DDTHH:mm[:ss]" lewat DateTimePicker. */
export function pickDateTime(trigger: HTMLElement, value: string) {
  const [date, time] = value.split('T')
  fireEvent.click(trigger)
  const sheet = screen.getByRole('dialog', { name: 'Pilih tanggal' })
  chooseDay(sheet, date)
  fireEvent.click(within(sheet).getByRole('button', { name: /Pilih jam/ }))
  setWheels(sheet, time)
  fireEvent.click(within(sheet).getByRole('button', { name: /Konfirmasi/ }))
}

/** Tekan "Kosongkan" di sheet pemilih mana pun. */
export function clearPicker(trigger: HTMLElement) {
  fireEvent.click(trigger)
  fireEvent.click(within(screen.getByRole('dialog', { name: /^Pilih/ })).getByRole('button', { name: 'Kosongkan' }))
}

function chooseDay(sheet: HTMLElement, iso: string) {
  const [year, month] = iso.split('-').map(Number)
  fireEvent.click(within(sheet).getByRole('button', { name: /ganti bulan dan tahun/ }))
  for (let guard = 0; !within(sheet).queryByRole('button', { name: String(year) }); guard++) {
    if (guard > 50) {
      throw new Error(`Tahun ${year} tidak bisa dicapai`)
    }
    // Judul mode tahun berbentuk "2016–2027".
    const firstYear = Number(within(sheet).getByRole('button', { name: 'Kembali ke kalender' }).textContent?.slice(0, 4))
    fireEvent.click(within(sheet).getByRole('button', { name: year < firstYear ? 'Sebelumnya' : 'Berikutnya' }))
  }
  fireEvent.click(within(sheet).getByRole('button', { name: String(year) }))
  fireEvent.click(within(sheet).getByRole('button', { name: `${MONTHS_LONG[month - 1]} ${year}` }))
  fireEvent.click(within(sheet).getByRole('button', { name: formatDayLabel(iso) }))
}

function setWheels(sheet: HTMLElement, time: string) {
  const [hour, minute, second] = time.split(':').map(Number)
  setWheel(within(sheet).getByRole('spinbutton', { name: 'Jam' }), hour)
  setWheel(within(sheet).getByRole('spinbutton', { name: 'Menit' }), minute)
  if (second !== undefined) {
    setWheel(within(sheet).getByRole('spinbutton', { name: 'Detik' }), second)
  }
}

function setWheel(wheel: HTMLElement, value: number) {
  fireEvent.keyDown(wheel, { key: 'Home' })
  for (let i = 0; i < value; i++) {
    fireEvent.keyDown(wheel, { key: 'ArrowDown' })
  }
}
