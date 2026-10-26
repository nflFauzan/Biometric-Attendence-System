package com.biometricattendance.employee;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
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
					message = "PIN mesin hanya huruf/angka, maksimal 20 karakter") String devicePin,
			@Email(message = "Format email tidak valid") @Size(max = 150) String email,
			Gender gender,
			@Past(message = "Tanggal lahir harus sebelum hari ini") LocalDate birthDate,
			@Size(max = 500, message = "Alamat maksimal 500 karakter") String address,
			@Size(max = 500, message = "Catatan maksimal 500 karakter") String notes) {
	}

	/** K-103: cabang absen tambahan; ID penempatan di daftar diabaikan, duplikat digabung. */
	public record BranchAccessRequest(@NotNull(message = "Daftar cabang wajib diisi") @Size(max = 50,
			message = "Maksimal 50 cabang") List<Long> extraBranchIds) {
	}

	public record BranchAccessResponse(Long placementBranchId, List<Long> extraBranchIds, int removedFingerprints) {
	}

	public record EmployeeResponse(Long id, String employeeNumber, String fullName, String phone, Long branchId,
			String branchName, Long categoryId, String categoryName, String position, LocalDate joinDate,
			LocalDate endDate, boolean active, String devicePin, String email, Gender gender, LocalDate birthDate,
			String address, String notes) {

		public static EmployeeResponse from(Employee e) {
			return new EmployeeResponse(e.getId(), e.getEmployeeNumber(), e.getFullName(), e.getPhone(),
					e.getBranch().getId(), e.getBranch().getName(), e.getCategory().getId(), e.getCategory().getName(),
					e.getPosition(), e.getJoinDate(), e.getEndDate(), e.isActive(), e.getDevicePin(),
					e.getEmail(), e.getGender(), e.getBirthDate(), e.getAddress(), e.getNotes());
		}

	}

}
