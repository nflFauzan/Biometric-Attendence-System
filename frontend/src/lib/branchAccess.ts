/** Kunci query akses cabang; dipakai oleh kartu Cabang absen dan bagian Sidik Jari (menyaring mesin pendaftaran). */
export const branchAccessKey = (employeeId: number) => ['/api/employees', String(employeeId), 'branches']
