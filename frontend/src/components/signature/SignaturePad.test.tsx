import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { SignaturePad } from '@/components/signature/SignaturePad'

const ctx = {
  beginPath: vi.fn(),
  moveTo: vi.fn(),
  lineTo: vi.fn(),
  stroke: vi.fn(),
  clearRect: vi.fn(),
  arc: vi.fn(),
  fill: vi.fn(),
  scale: vi.fn(),
  setTransform: vi.fn(),
  lineWidth: 0,
  lineCap: '',
  lineJoin: '',
  strokeStyle: '',
  fillStyle: '',
}

beforeEach(() => {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as unknown as CanvasRenderingContext2D)
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((cb) => cb(new Blob(['x'], { type: 'image/png' })))
  HTMLCanvasElement.prototype.setPointerCapture = vi.fn()
  HTMLCanvasElement.prototype.releasePointerCapture = vi.fn()
})

afterEach(() => {
  vi.restoreAllMocks()
  Object.values(ctx).forEach((v) => typeof v === 'function' && (v as ReturnType<typeof vi.fn>).mockClear())
})

function draw(canvas: HTMLElement) {
  fireEvent.pointerDown(canvas, { clientX: 5, clientY: 5, pointerId: 1 })
  fireEvent.pointerMove(canvas, { clientX: 40, clientY: 30, pointerId: 1 })
  fireEvent.pointerUp(canvas, { clientX: 40, clientY: 30, pointerId: 1 })
}

describe('SignaturePad', () => {
  it('Simpan nonaktif sampai ada coretan, lalu onSave menerima Blob PNG', () => {
    const onSave = vi.fn()
    render(<SignaturePad onSave={onSave} />)
    const save = screen.getByRole('button', { name: 'Simpan' })
    expect(save).toBeDisabled()

    draw(screen.getByLabelText('Area tanda tangan'))
    expect(save).toBeEnabled()

    fireEvent.click(save)
    expect(onSave).toHaveBeenCalledTimes(1)
    expect(onSave.mock.calls[0][0]).toBeInstanceOf(Blob)
  })

  it('Hapus coretan mengosongkan kanvas dan menonaktifkan Simpan lagi', () => {
    render(<SignaturePad onSave={vi.fn()} />)
    draw(screen.getByLabelText('Area tanda tangan'))
    fireEvent.click(screen.getByRole('button', { name: 'Hapus coretan' }))
    expect(screen.getByRole('button', { name: 'Simpan' })).toBeDisabled()
  })

  it('canvas memakai touch-action none dan tinggi 200 px', () => {
    render(<SignaturePad onSave={vi.fn()} />)
    const canvas = screen.getByLabelText('Area tanda tangan')
    expect(canvas.style.touchAction).toBe('none')
    expect(canvas.style.height).toBe('200px')
  })

  it('satu ketukan tanpa gerakan dihitung sebagai tinta dan menggambar titik', () => {
    render(<SignaturePad onSave={vi.fn()} />)
    const canvas = screen.getByLabelText('Area tanda tangan')
    fireEvent.pointerDown(canvas, { clientX: 10, clientY: 10, pointerId: 1 })
    fireEvent.pointerUp(canvas, { clientX: 10, clientY: 10, pointerId: 1 })
    expect(ctx.arc).toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Simpan' })).toBeEnabled()
  })

  describe('devicePixelRatio', () => {
    const original = window.devicePixelRatio
    let width = 300
    beforeEach(() => {
      Object.defineProperty(window, 'devicePixelRatio', { value: 2, configurable: true })
      vi.spyOn(HTMLCanvasElement.prototype, 'clientWidth', 'get').mockImplementation(() => width)
    })
    afterEach(() => {
      Object.defineProperty(window, 'devicePixelRatio', { value: original, configurable: true })
      width = 300
    })

    it('bitmap = ukuran CSS x dpr dan konteks diskalakan', () => {
      render(<SignaturePad onSave={vi.fn()} />)
      const canvas = screen.getByLabelText('Area tanda tangan') as HTMLCanvasElement
      expect(canvas.width).toBe(600)
      expect(canvas.height).toBe(400)
      expect(ctx.setTransform).toHaveBeenCalledWith(2, 0, 0, 2, 0, 0)
      expect(canvas.style.height).toBe('200px')
    })

    it('ukuran ulang saat jendela berubah, koordinat tetap dalam piksel CSS', () => {
      render(<SignaturePad onSave={vi.fn()} />)
      const canvas = screen.getByLabelText('Area tanda tangan') as HTMLCanvasElement
      width = 200
      fireEvent(window, new Event('resize'))
      expect(canvas.width).toBe(400)
      expect(canvas.height).toBe(400)
      fireEvent.pointerDown(canvas, { clientX: 7, clientY: 9, pointerId: 1 })
      expect(ctx.moveTo).toHaveBeenLastCalledWith(7, 9)
    })

    it('coretan dipertahankan setelah ukuran ulang', () => {
      render(<SignaturePad onSave={vi.fn()} />)
      draw(screen.getByLabelText('Area tanda tangan'))
      ctx.lineTo.mockClear()
      width = 250
      fireEvent(window, new Event('resize'))
      expect(ctx.lineTo).toHaveBeenCalled()
      expect(screen.getByRole('button', { name: 'Simpan' })).toBeEnabled()
    })
  })
})
