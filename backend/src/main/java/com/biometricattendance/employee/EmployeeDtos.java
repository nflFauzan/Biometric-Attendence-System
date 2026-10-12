package com.biometricattendance.employee;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class EmployeeDtos {

	private EmployeeDtos() {
	}

	public record EmployeeRequest(
			@NotBlank(message = "Nomor karyawan wajib diisi") @Size(max = 30) String employeeNumber,
			@NotBlank(message = "Nama wajib diisi") @Size(max = 150) String fullName,
			@Size(max = 30) String phone,
			@NotNull(message = "Cabang wajib dipilih") Long branchId,
			@NotNull(message = "Kategori wajib dipilih") Long categoryId,
			@Size(max = 100) String position,
			@NotNull(message = "Tanggal masuk wajib diisi") LocalDate joinDate,
			LocalDate endDate,
			@NotNull(message = "Status aktif wajib diisi") Boolean active,
			@Pattern(regexp = "^\\s*$|^[A-Za-z0-9]{1,20}$",
					message = "PIN mesin hanya huruf/angka, maksimal 20 karakter") String devicePin) {
	}

	public record EmployeeResponse(Long id, String employeeNumber, String fullName, String phone, Long branchId,
			String branchName, Long categoryId, String categoryName, String position, LocalDate joinDate,
			LocalDate endDate, boolean active, String devicePin) {

		public static EmployeeResponse from(Employee e) {
			return new EmployeeResponse(e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getPhone(),
					e.getBranch().getId(), e.getBranch().getName(), e.getCategory().getId(), e.getCategory().getName(),
					e.getPosition(), e.getJoinDate(), e.getEndDate(), e.isActive(), e.getDevicePin());
		}

	}

}
