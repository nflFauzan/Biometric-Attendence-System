package com.biometricattendance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Metadata dokumen OpenAPI. Dokumen hanya tersedia jika OPENAPI_ENABLED=true
 * (lihat application.properties) dan hanya untuk Owner/HR-Finance (lihat SecurityConfig).
 */
@Configuration
public class OpenApiConfig {

	private static final String SESSION_COOKIE = "sessionCookie";

	@Bean
	OpenAPI apiDocumentation() {
		return new OpenAPI()
			.info(new Info().title("Biometric Attendance System API")
				.version("v1")
				.description("Login dengan POST /api/auth/login (session cookie). Request yang mengubah data "
						+ "wajib mengirim header X-XSRF-TOKEN sesuai cookie XSRF-TOKEN."))
			.components(new Components().addSecuritySchemes(SESSION_COOKIE,
					new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("SESSION")))
			.addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE));
	}

}
