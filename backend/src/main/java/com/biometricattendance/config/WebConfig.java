package com.biometricattendance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.biometricattendance.auth.PasswordChangeGuard;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new PasswordChangeGuard()).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
	}

}
