package com.biometricattendance.account;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserAccountDtos {

	public static final int MIN_PASSWORD_LENGTH = 8;

	private UserAccountDtos() {
	}

	public record CreateUserRequest(
			@NotBlank(message = "Username wajib diisi")
			@Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "Username 3-50 karakter: huruf, angka, titik, _ atau -")
			String username,
			@NotBlank(message = "Password wajib diisi")
			@Size(min = MIN_PASSWORD_LENGTH, max = 100, message = "Password minimal 8 karakter")
			String password,
			@NotNull(message = "Role wajib dipilih") Role role,
			Long employeeId) {
	}

	public record UpdateUserRequest(@NotNull(message = "Role wajib dipilih") Role role, Long employeeId,
			@NotNull(message = "Status aktif wajib diisi") Boolean active) {
	}

	public record ResetPasswordRequest(
			@NotBlank(message = "Password wajib diisi")
			@Size(min = MIN_PASSWORD_LENGTH, max = 100, message = "Password minimal 8 karakter")
			String newPassword) {
	}

	/** Tidak pernah memuat password hash, termasuk di audit log. */
	public record UserResponse(Long id, String username, Role role, Long employeeId, String employeeName,
			boolean active, boolean mustChangePassword, Instant lastLoginAt) {

		public static UserResponse from(UserAccount account) {
			return new UserResponse(account.getId(), account.getUsername(), account.getRole(),
					account.getEmployee() == null ? null : account.getEmployee().getId(),
					account.getEmployee() == null ? null : account.getEmployee().getFullName(), account.isActive(),
					account.isMustChangePassword(), account.getLastLoginAt());
		}

	}

}
