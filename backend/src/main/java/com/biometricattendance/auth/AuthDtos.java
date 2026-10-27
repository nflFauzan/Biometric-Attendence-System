package com.biometricattendance.auth;

import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountDtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {
	}

	/** Username dibatasi seperti kolom user_accounts.username, agar kunci di batas login (K-138) tetap kecil. */
	public record LoginRequest(
			@NotBlank(message = "Username wajib diisi") @Size(max = 50, message = "Username maksimal 50 karakter") String username,
			@NotBlank(message = "Password wajib diisi") String password) {
	}

	public record ChangePasswordRequest(@NotBlank(message = "Password lama wajib diisi") String currentPassword,
			@NotBlank(message = "Password baru wajib diisi")
			@Size(min = UserAccountDtos.MIN_PASSWORD_LENGTH, max = 100, message = "Password minimal 8 karakter")
			String newPassword) {
	}

	public record CsrfResponse(String headerName, String token) {
	}

	public record MeResponse(String username, Role role, Long employeeId, String employeeName,
			boolean mustChangePassword) {

		public static MeResponse from(UserAccount account) {
			return new MeResponse(account.getUsername(), account.getRole(),
					account.getEmployee() == null ? null : account.getEmployee().getId(),
					account.getEmployee() == null ? null : account.getEmployee().getFullName(),
					account.isMustChangePassword());
		}

	}

}
