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
				// Endpoint mesin (adapter ZK-Push): tanpa login; mesin divalidasi dari nomor seri terdaftar (K-055).
				.requestMatchers("/iclock/**").permitAll()
				// Endpoint alat rakitan: tanpa login; mesin divalidasi dari kunci per mesin di controller (K-088).
				.requestMatchers("/device/v1/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
				.requestMatchers("/api/auth/**", "/api/me/**").authenticated()
				// Daftar rekan kerja: semua role yang login; kolomnya dibatasi TeamDtos (K-083).
				.requestMatchers(HttpMethod.GET, "/api/team/**").authenticated()
				.requestMatchers("/api/audit-logs/**").hasRole(OWNER)
				// Dokumentasi API hanya ada jika OPENAPI_ENABLED=true, dan tetap butuh login pengelola.
				.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
				.hasAnyRole(OWNER, HR_FINANCE)
				// Rencana 2: menerbitkan kunci alat rakitan hanya Owner (K-099). Harus sebelum /api/devices/**.
				.requestMatchers(HttpMethod.POST, "/api/devices/*/key").hasRole(OWNER)
				.requestMatchers("/api/branches/**", "/api/employee-categories/**", "/api/employees/**",
						"/api/devices/**", "/api/users/**")
				.hasAnyRole(OWNER, HR_FINANCE)
				// Aturan kerja: HR/Finance hanya membaca; mengubah hanya Owner (K-049).
				.requestMatchers(HttpMethod.GET, "/api/work-rules/**").hasAnyRole(OWNER, HR_FINANCE)
				.requestMatchers("/api/work-rules/**").hasRole(OWNER)
				.requestMatchers("/api/holidays/**", "/api/calculation/**").hasAnyRole(OWNER, HR_FINANCE)
				// Fase 3: rekap, koreksi (K-059), dan dashboard real-time. Absensi mandiri karyawan (ATT-021) belum.
				.requestMatchers("/api/attendance/**", "/api/dashboard/**").hasAnyRole(OWNER, HR_FINANCE)
				// Fase 4: jenis izin dibaca semua pengguna (untuk form pengajuan), diubah hanya Owner (K-070).
				// Pengajuan milik sendiri lewat /api/me/leave-requests (di atas: authenticated, kepemilikan dicek service).
				.requestMatchers(HttpMethod.GET, "/api/leave-types/**").authenticated()
				.requestMatchers("/api/leave-types/**").hasRole(OWNER)
				.requestMatchers("/api/leave-requests/**").hasAnyRole(OWNER, HR_FINANCE)
				.anyRequest().denyAll())
			// Mesin tidak memakai cookie sesi, jadi CSRF tidak relevan untuk /iclock/** dan /device/v1/**.
			.csrf(csrf -> csrf.spa().ignoringRequestMatchers("/iclock/**", "/device/v1/**"))
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
