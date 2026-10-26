package com.biometricattendance.employee;

import java.time.LocalDate;
import java.util.List;

import com.biometricattendance.account.Role;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Impor karyawan dua langkah (K-130): pratinjau dari file, lalu simpan baris yang sudah diperiksa Owner. */
public final class EmployeeImportDtos {

	private EmployeeImportDtos() {
	}

	/** Satu baris pratinjau; username dan role boleh diubah Owner sebelum disimpan. ID dan nama hasil pencocokan. */
	public record PreviewRow(int rowNumber, String employeeNumber, String fullName, String unit, Long categoryId,
			String categoryName, String position, Long branchId, String branchName, String phone, String email,
			Gender gender, LocalDate birthDate, String address, String notes, String accountStatus, String username,
			Role role, List<String> errors) {
	}

	public record PreviewResponse(LocalDate startDate, List<PreviewRow> rows, int errorCount) {
	}

	public record CommitRequest(@NotNull(message = "Tanggal mulai wajib diisi") LocalDate startDate,
			@NotEmpty(message = "Tidak ada baris untuk diimpor") @Size(max = EmployeeImportParser.MAX_ROWS,
					message = "Maksimal 500 karyawan per impor") @Valid List<PreviewRow> rows) {
	}

	/** Password awal hanya dikembalikan sekali, di respons ini; tidak pernah dicatat di audit log. */
	public record CreatedAccount(String employeeNumber, String fullName, String username, Role role,
			String initialPassword) {
	}

	public record CommitResponse(int employeesCreated, int accountsCreated, List<CreatedAccount> accounts) {
	}

}
