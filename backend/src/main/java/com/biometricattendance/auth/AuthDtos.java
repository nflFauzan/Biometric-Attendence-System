package com.biometricattendance.auth;

import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountDtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record LoginRequest(@NotBlank(message = "Username wajib diisi") String username,
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
