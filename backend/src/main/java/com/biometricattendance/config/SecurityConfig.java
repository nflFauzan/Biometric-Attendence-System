package com.biometricattendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;

import com.biometricattendance.common.ProblemResponses;

/**
 * Keamanan web: login dengan session cookie (disimpan di database), CSRF untuk SPA,
 * dan hak akses per role sesuai docs/kebutuhan.md.
 */
@Configuration
public class SecurityConfig {

	private static final String OWNER = "OWNER";
	private static final String HR_FINANCE = "HR_FINANCE";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
			throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
				.requestMatchers("/api/auth/**", "/api/me/**").authenticated()
				.requestMatchers("/api/audit-logs/**").hasRole(OWNER)
				// Dokumentasi API hanya ada jika OPENAPI_ENABLED=true, dan tetap butuh login pengelola.
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.hasAnyRole(OWNER, HR_FINANCE)
				.requestMatchers("/api/branches/**", "/api/employee-categories/**", "/api/employees/**",
						"/api/devices/**", "/api/users/**")
				.hasAnyRole(OWNER, HR_FINANCE)
				.anyRequest().denyAll())
			.csrf(csrf -> csrf.spa())
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, exception) -> ProblemResponses.write(response,
						HttpStatus.UNAUTHORIZED, ProblemResponses.UNAUTHORIZED))
				.accessDeniedHandler((request, response, exception) -> ProblemResponses.write(response,
						HttpStatus.FORBIDDEN, exception instanceof CsrfException ? ProblemResponses.CSRF_INVALID
								: ProblemResponses.FORBIDDEN)))
			.logout(logout -> logout.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)));
		return http.build();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

}
