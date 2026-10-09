package com.biometricattendance.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.auth.AuthDtos.ChangePasswordRequest;
import com.biometricattendance.auth.AuthDtos.CsrfResponse;
import com.biometricattendance.auth.AuthDtos.LoginRequest;
import com.biometricattendance.auth.AuthDtos.MeResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** Login berbasis session cookie. Logout ditangani Spring Security di POST /api/auth/logout. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final AuthService authService;
	private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

	public AuthController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository, AuthService authService) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.authService = authService;
	}

	/** Memastikan cookie XSRF-TOKEN terkirim sebelum frontend melakukan POST pertama. */
	@GetMapping("/csrf")
	CsrfResponse csrf(CsrfToken token) {
		return new CsrfResponse(token.getHeaderName(), token.getToken());
	}

	@PostMapping("/login")
	MeResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		Authentication authentication = authenticationManager.authenticate(UsernamePasswordAuthenticationToken
			.unauthenticated(request.username().trim().toLowerCase(Locale.ROOT), request.password()));
		if (httpRequest.getSession(false) != null) {
			httpRequest.changeSessionId();
		}
		SecurityContext context = contextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		contextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
		MeResponse me = authService.recordLogin(authentication.getName());
		PasswordChangeGuard.setRequired(httpRequest.getSession(), me.mustChangePassword());
		return me;
	}

	@GetMapping("/me")
	MeResponse me(Authentication authentication) {
		return authService.me(authentication.getName());
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication,
			HttpServletRequest httpRequest) {
		authService.changePassword(authentication.getName(), request);
		PasswordChangeGuard.setRequired(httpRequest.getSession(), false);
	}

}
