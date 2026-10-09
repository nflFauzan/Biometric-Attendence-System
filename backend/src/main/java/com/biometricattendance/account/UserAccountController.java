package com.biometricattendance.account;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.account.UserAccountDtos.CreateUserRequest;
import com.biometricattendance.account.UserAccountDtos.ResetPasswordRequest;
import com.biometricattendance.account.UserAccountDtos.UpdateUserRequest;
import com.biometricattendance.account.UserAccountDtos.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserAccountController {

	private final UserAccountService service;

	public UserAccountController(UserAccountService service) {
		this.service = service;
	}

	@GetMapping
	List<UserResponse> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
		return service.update(id, request);
	}

	@PostMapping("/{id}/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
		service.resetPassword(id, request);
	}

}
