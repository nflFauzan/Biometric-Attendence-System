package com.biometricattendance.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EmployeeCategoryDtos {

	private EmployeeCategoryDtos() {
	}

	public record CategoryRequest(
			@NotBlank(message = "Kode wajib diisi") @Size(max = 30) String code,
			@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name,
			@NotNull(message = "Status aktif wajib diisi") Boolean active) {
	}

	public record CategoryResponse(Long id, String code, String name, boolean active) {

		public static CategoryResponse from(EmployeeCategory category) {
			return new CategoryResponse(category.getId(), category.getCode(), category.getName(), category.isActive());
		}

	}

}
