import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Banknote, CheckCircle2, History, Plus, Search } from 'lucide-react'
import { useState } from 'react'

import { EmptyState, ErrorAlert, SkeletonLines } from '@/components/feedback'
import { PageHeader } from '@/components/PageHeader'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { formatDateTime, formatDate, formatRupiah, localDateIso } from '@/lib/format'
import type { Employee, EmployeeLoan } from '@/lib/types'

export function LoansPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'AKTIF' | 'LUNAS'>('ALL')
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const [selectedLoan, setSelectedLoan] = useState<EmployeeLoan | null>(null)

  // Form State
  const [employeeId, setEmployeeId] = useState('')
  const [loanDate, setLoanDate] = useState(localDateIso(new Date()))
  const [totalAmount, setTotalAmount] = useState('')
  const [installmentAmount, setInstallmentAmount] = useState('')
  const [notes, setNotes] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  const { data: loans, isPending, error } = useQuery({
    queryKey: ['/api/loans'],
    queryFn: () => api<EmployeeLoan[]>('/api/loans'),
  })

  const { data: employees } = useQuery({
    queryKey: ['/api/employees'],
    queryFn: () => api<Employee[]>('/api/employees'),
  })

  const createMutation = useMutation({
    mutationFn: (body: {
      employeeId: number
      loanDate: string
      totalAmount: number
      installmentAmount: number
      notes?: string
    }) => api.post<EmployeeLoan>('/api/loans', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['/api/loans'] })
      setIsCreateOpen(false)
      resetForm()
    },
    onError: (err: any) => {
      setFormError(err.message || 'Gagal menambahkan kasbon')
    },
  })

  function resetForm() {
    setEmployeeId('')
    setLoanDate(localDateIso(new Date()))
    setTotalAmount('')
    setInstallmentAmount('')
    setNotes('')
    setFormError(null)
  }

  function handleCreateSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    const empId = Number(employeeId)
    const total = Number(totalAmount)
    const installment = Number(installmentAmount)

    if (!empId) {
      setFormError('Pilih karyawan terlebih dahulu')
      return
    }
    if (!total || total <= 0) {
      setFormError('Total pinjaman harus lebih besar dari 0')
      return
    }
    if (!installment || installment <= 0) {
      setFormError('Cicilan per periode harus lebih besar dari 0')
      return
    }

    createMutation.mutate({
      employeeId: empId,
      loanDate,
      totalAmount: total,
      installmentAmount: installment,
      notes: notes.trim() || undefined,
    })
  }

  const activeLoans = loans?.filter((l) => l.status === 'AKTIF') || []
  const totalActiveRemaining = activeLoans.reduce((sum, l) => sum + (Number(l.remainingAmount) || 0), 0)

  const filteredLoans = (loans || []).filter((l) => {
    const matchesSearch =
      l.loanNumber.toLowerCase().includes(search.toLowerCase()) ||
      l.employeeName.toLowerCase().includes(search.toLowerCase()) ||
      l.branchName.toLowerCase().includes(search.toLowerCase())
    const matchesStatus = statusFilter === 'ALL' || l.status === statusFilter
    return matchesSearch && matchesStatus
  })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Kasbon & Piutang Karyawan"
        description="Pencatatan pinjaman karyawan, alokasi cicilan per periode, dan pemotongan otomatis pada laporan upah & gaji."
        actions={
          <Button onClick={() => setIsCreateOpen(true)} className="gap-2">
            <Plus className="size-4" />
            Tambah Kasbon
          </Button>
        }
      />

      {/* Ringkasan */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardContent className="flex items-center gap-4 p-4">
            <div className="rounded-lg bg-steel-100 p-3 text-steel-700 dark:bg-steel-800 dark:text-steel-200">
              <Banknote className="size-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Kasbon Aktif</p>
              <p className="text-2xl font-bold">{activeLoans.length} Karyawan</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center gap-4 p-4">
            <div className="rounded-lg bg-gold-100 p-3 text-gold-700 dark:bg-gold-950 dark:text-gold-300">
              <Banknote className="size-6" />
            </div>
            <div>
              <p className="text-xs font-medium text-muted-foreground">Total Sisa Piutang</p>
              <p className="text-2xl font-bold text-gold-600 dark:text-gold-400">
                {formatRupiah(totalActiveRemaining)}
              </p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Filter & Pencarian */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full max-w-sm">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Cari no. kasbon, nama, atau cabang..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant={statusFilter === 'ALL' ? 'default' : 'outline'}
            onClick={() => setStatusFilter('ALL')}
          >
            Semua
          </Button>
          <Button
            size="sm"
            variant={statusFilter === 'AKTIF' ? 'default' : 'outline'}
            onClick={() => setStatusFilter('AKTIF')}
          >
            Aktif
          </Button>
          <Button
            size="sm"
            variant={statusFilter === 'LUNAS' ? 'default' : 'outline'}
            onClick={() => setStatusFilter('LUNAS')}
          >
            Lunas
          </Button>
        </div>
      </div>

      {/* Error / Loading */}
      {error && <ErrorAlert message={error.message} />}
      {isPending && <SkeletonLines rows={5} />}

      {/* Tabel */}
      {!isPending && !error && (
        <div className="overflow-hidden rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>No. Pinjaman</TableHead>
                <TableHead>Karyawan</TableHead>
                <TableHead>Cabang</TableHead>
                <TableHead>Tanggal</TableHead>
                <TableHead className="text-right">Total Pinjaman</TableHead>
                <TableHead className="text-right">Cicilan / Periode</TableHead>
                <TableHead className="text-right">Sisa Pinjaman</TableHead>
                <TableHead className="text-center">Status</TableHead>
                <TableHead className="text-center">Aksi</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredLoans.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={9} className="h-32 text-center text-muted-foreground">
                    Tidak ada catatan kasbon.
                  </TableCell>
                </TableRow>
              ) : (
                filteredLoans.map((loan) => (
                  <TableRow key={loan.id}>
                    <TableCell className="font-mono text-xs font-semibold">{loan.loanNumber}</TableCell>
                    <TableCell className="font-medium">{loan.employeeName}</TableCell>
                    <TableCell>{loan.branchName}</TableCell>
                    <TableCell>{formatDate(loan.loanDate)}</TableCell>
                    <TableCell className="text-right font-medium">{formatRupiah(loan.totalAmount)}</TableCell>
                    <TableCell className="text-right text-muted-foreground">{formatRupiah(loan.installmentAmount)}</TableCell>
                    <TableCell className="text-right font-semibold text-gold-600 dark:text-gold-400">
                      {formatRupiah(loan.remainingAmount)}
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge variant={loan.status === 'AKTIF' ? 'default' : 'secondary'}>
                        {loan.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-center">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setSelectedLoan(loan)}
                        className="gap-1.5 text-xs"
                      >
                        <History className="size-3.5" />
                        Riwayat
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Dialog Tambah Kasbon */}
      <Dialog open={isCreateOpen} onOpenChange={setIsCreateOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Tambah Kasbon Baru</DialogTitle>
            <DialogDescription>
              Catat pinjaman baru untuk karyawan. Cicilan akan terpotong otomatis saat tutup buku laporan.
            </DialogDescription>
          </DialogHeader>

          <form onSubmit={handleCreateSubmit} className="flex flex-col gap-4">
            {formError && <ErrorAlert message={formError} />}

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="employee">Karyawan</Label>
              <select
                id="employee"
                value={employeeId}
                onChange={(e) => setEmployeeId(e.target.value)}
                className="h-10 rounded-md border border-input bg-card px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                required
              >
                <option value="">-- Pilih Karyawan --</option>
                {(employees || [])
                  .filter((e) => e.active)
                  .map((e) => (
                    <option key={e.id} value={e.id}>
                      {e.fullName} ({e.branchName} - {e.categoryName || 'Umum'})
                    </option>
                  ))}
              </select>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="loanDate">Tanggal Pinjaman</Label>
              <Input
                id="loanDate"
                type="date"
                value={loanDate}
                onChange={(e) => setLoanDate(e.target.value)}
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1.5">
                <Label htmlFor="totalAmount">Total Pinjaman (Rp)</Label>
                <Input
                  id="totalAmount"
                  type="number"
                  placeholder="Misal 1000000"
                  value={totalAmount}
                  onChange={(e) => setTotalAmount(e.target.value)}
                  min={1}
                  required
                />
              </div>

              <div className="flex flex-col gap-1.5">
                <Label htmlFor="installmentAmount">Cicilan per Periode (Rp)</Label>
                <Input
                  id="installmentAmount"
                  type="number"
                  placeholder="Misal 100000"
                  value={installmentAmount}
                  onChange={(e) => setInstallmentAmount(e.target.value)}
                  min={1}
                  required
                />
              </div>
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="notes">Catatan / Alasan</Label>
              <Input
                id="notes"
                placeholder="Misal: Keperluan berobat keluarga"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>

            <DialogFooter className="mt-2">
              <Button type="button" variant="outline" onClick={() => setIsCreateOpen(false)}>
                Batal
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Menyimpan...' : 'Simpan Kasbon'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Dialog Riwayat Pelunasan */}
      <Dialog open={!!selectedLoan} onOpenChange={(open) => !open && setSelectedLoan(null)}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Riwayat Pemotongan Kasbon</DialogTitle>
            <DialogDescription>
              {selectedLoan?.loanNumber} — {selectedLoan?.employeeName} ({selectedLoan?.branchName})
            </DialogDescription>
          </DialogHeader>

          <div className="flex flex-col gap-3">
            <div className="grid grid-cols-3 gap-2 rounded-lg bg-muted p-3 text-center text-xs">
              <div>
                <p className="text-muted-foreground">Total Pinjaman</p>
                <p className="font-semibold">{formatRupiah(selectedLoan?.totalAmount)}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Cicilan / Periode</p>
                <p className="font-semibold">{formatRupiah(selectedLoan?.installmentAmount)}</p>
              </div>
              <div>
                <p className="text-muted-foreground">Sisa Kewajiban</p>
                <p className="font-semibold text-gold-600 dark:text-gold-400">
                  {formatRupiah(selectedLoan?.remainingAmount)}
                </p>
              </div>
            </div>

            <div className="max-h-60 overflow-y-auto rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tanggal</TableHead>
                    <TableHead>Periode</TableHead>
                    <TableHead className="text-right">Nominal</TableHead>
                    <TableHead>Catatan</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(!selectedLoan?.repayments || selectedLoan.repayments.length === 0) ? (
                    <TableRow>
                      <TableCell colSpan={4} className="h-20 text-center text-xs text-muted-foreground">
                        Belum ada pemotongan atau pelunasan.
                      </TableCell>
                    </TableRow>
                  ) : (
                    selectedLoan.repayments.map((r) => (
                      <TableRow key={r.id}>
                        <TableCell className="text-xs">{formatDate(r.repaymentDate)}</TableCell>
                        <TableCell className="text-xs font-medium">{r.periodLabel}</TableCell>
                        <TableCell className="text-right text-xs font-semibold text-emerald-600">
                          {formatRupiah(r.amount)}
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">{r.notes || '—'}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setSelectedLoan(null)}>
              Tutup
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

