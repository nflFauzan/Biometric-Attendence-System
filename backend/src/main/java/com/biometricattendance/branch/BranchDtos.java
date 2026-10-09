package com.biometricattendance.branch;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BranchDtos {

	private BranchDtos() {
	}

	public record BranchRequest(
			@NotBlank(message = "Kode wajib diisi") @Size(max = 20) String code,
			@NotBlank(message = "Nama wajib diisi") @Size(max = 100) String name,
			@Size(max = 255) String address,
			@NotBlank(message = "Zona waktu wajib diisi") @Size(max = 50) String timezone,
			@NotNull(message = "Status aktif wajib diisi") Boolean active) {
	}

	public record BranchResponse(Long id, String code, String name, String address, String timezone,
			boolean active) {

		public static BranchResponse from(Branch branch) {
			return new BranchResponse(branch.getId(), branch.getCode(), branch.getName(), branch.getAddress(),
					branch.getTimezone(), branch.isActive());
		}

	}

}
