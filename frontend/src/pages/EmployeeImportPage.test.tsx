import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'

import App from '@/App'
import { AuthProvider } from '@/auth/AuthContext'
import type { ImportCommitResult, ImportPreview, ImportPreviewRow, Me } from '@/lib/types'
import { mockFetch } from '@/test/fetchMock'

function renderAt(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const LOAD = { timeout: 5000 }

const owner: Me = { username: 'owner', role: 'OWNER', employeeId: null, employeeName: null, mustChangePassword: false }

const row = (overrides: Partial<ImportPreviewRow> = {}): ImportPreviewRow => ({
  rowNumber: 2,
  employeeNumber: '002',
  fullName: 'Harfianti Contoh',
  unit: 'Office',
  categoryId: 1,
  categoryName: 'Kantor',
  position: 'Purchasing',
  branchId: 3,
  branchName: 'Takka 2',
  phone: '081200000000',
  email: null,
  gender: 'PEREMPUAN',
  birthDate: '1990-01-15',
  address: 'Jl. Contoh',
  notes: null,
  accountStatus: 'Aktif',
  username: 'harfianti@takka.id',
  role: 'EMPLOYEE',
  errors: [],
  ...overrides,
})

const preview = (rows: ImportPreviewRow[]): ImportPreview => ({
  startDate: '2026-11-01',
  rows,
  errorCount: rows.filter((r) => r.errors.length > 0).length,
})

const committed: ImportCommitResult = {
  employeesCreated: 1,
  accountsCreated: 1,
  accounts: [
    { employeeNumber: '002', fullName: 'Harfianti Contoh', username: 'harfi@takka.id', role: 'HR_FINANCE', initialPassword: 'harfi-002' },
  ],
}

async function uploadFile() {
  const input = await screen.findByLabelText('File Excel (.xlsx)', {}, LOAD)
  const file = new File(['x'], 'data.xlsx', { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
  fireEvent.change(input, { target: { files: [file] } })
  fireEvent.click(screen.getByRole('button', { name: 'Pratinjau' }))
}

describe('Impor karyawan (K-130)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('mengunggah file lalu menampilkan pratinjau', async () => {
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'POST /api/employee-imports/preview': () => ({ status: 200, body: preview([row()]) }),
    })
    renderAt('/karyawan/impor')
    await uploadFile()

    expect(await screen.findByText('Harfianti Contoh', {}, LOAD)).toBeInTheDocument()
    expect(screen.getByLabelText('Username baris 2')).toHaveValue('harfianti@takka.id')
    const call = fetch.mock.calls.find(([url]) => url === '/api/employee-imports/preview')!
    const body = call[1]!.body as FormData
    expect(body.get('file')).toBeInstanceOf(File)
    expect(body.get('startDate')).toMatch(/^\d{4}-\d{2}-01$/)
  })

  it('mengirim username dan role yang sudah diubah saat menyimpan', async () => {
    const fetch = mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'POST /api/employee-imports/preview': () => ({ status: 200, body: preview([row()]) }),
      'POST /api/employee-imports': () => ({ status: 200, body: committed }),
    })
    renderAt('/karyawan/impor')
    await uploadFile()

    fireEvent.change(await screen.findByLabelText('Username baris 2', {}, LOAD), { target: { value: 'harfi@takka.id' } })
    fireEvent.change(screen.getByLabelText('Role baris 2'), { target: { value: 'HR_FINANCE' } })
    fireEvent.click(screen.getByRole('button', { name: 'Simpan 1 karyawan' }))

    await waitFor(() => expect(fetch.mock.calls.some(([url, init]) => url === '/api/employee-imports' && init?.method === 'POST')).toBe(true))
    const call = fetch.mock.calls.find(([url, init]) => url === '/api/employee-imports' && init?.method === 'POST')!
    expect(JSON.parse(String(call[1]!.body))).toMatchObject({
      startDate: '2026-11-01',
      rows: [{ username: 'harfi@takka.id', role: 'HR_FINANCE', employeeNumber: '002' }],
    })
  })

  it('menampilkan masalah per baris dari server dan menahan tombol simpan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'POST /api/employee-imports/preview': () => ({ status: 200, body: preview([row()]) }),
      'POST /api/employee-imports': () => ({
        status: 409,
        body: {
          status: 409,
          detail: 'Ada 1 baris bermasalah; periksa ulang di pratinjau',
          rows: [row({ errors: ['Username harfianti@takka.id sudah dipakai'] })],
        },
      }),
    })
    renderAt('/karyawan/impor')
    await uploadFile()
    fireEvent.click(await screen.findByRole('button', { name: 'Simpan 1 karyawan' }, LOAD))

    expect(await screen.findByText('Username harfianti@takka.id sudah dipakai', {}, LOAD)).toBeInTheDocument()
    expect(screen.getByText('Ada 1 baris bermasalah; periksa ulang di pratinjau')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Simpan 1 karyawan' })).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Username baris 2'), { target: { value: 'harfi@takka.id' } })
    expect(screen.getByRole('button', { name: 'Simpan 1 karyawan' })).toBeEnabled()
  })

  it('menahan simpan bila username tidak berakhiran @takka.id', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'POST /api/employee-imports/preview': () => ({ status: 200, body: preview([row()]) }),
    })
    renderAt('/karyawan/impor')
    await uploadFile()
    fireEvent.change(await screen.findByLabelText('Username baris 2', {}, LOAD), { target: { value: 'harfi' } })
    expect(screen.getByRole('button', { name: 'Simpan 1 karyawan' })).toBeDisabled()
    expect(screen.getByText('Username harus berakhiran @takka.id')).toBeInTheDocument()
  })

  it('menampilkan password awal setelah tersimpan', async () => {
    mockFetch({
      'GET /api/auth/me': () => ({ status: 200, body: owner }),
      'POST /api/employee-imports/preview': () => ({ status: 200, body: preview([row()]) }),
      'POST /api/employee-imports': () => ({ status: 200, body: committed }),
    })
    renderAt('/karyawan/impor')
    await uploadFile()
    fireEvent.click(await screen.findByRole('button', { name: 'Simpan 1 karyawan' }, LOAD))

    expect(await screen.findByText('harfi-002', {}, LOAD)).toBeInTheDocument()
    expect(screen.getByText(/Password ini hanya ditampilkan sekali/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Unduh CSV' })).toBeInTheDocument()
  })
})
