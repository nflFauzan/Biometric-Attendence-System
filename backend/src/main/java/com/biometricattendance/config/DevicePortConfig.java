package com.biometricattendance.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Port tambahan untuk mesin saat backend dijalankan langsung (start-dev.bat), bukan lewat Docker (K-086).
 * Mesin X401 mengirim ke port 8888 karena tombol 0 keypad rusak (K-060); di Docker port itu dipetakan ke 8080,
 * di luar Docker backend membuka port kedua ini. Kosong (default) berarti tidak ada port tambahan.
 */
@Configuration
public class DevicePortConfig {

	@Bean
	WebServerFactoryCustomizer<TomcatServletWebServerFactory> deviceExtraPort(
			@Value("${app.device.extra-port:}") String extraPort) {
		return factory -> {
			if (!StringUtils.hasText(extraPort)) {
				return;
			}
			Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
			connector.setPort(Integer.parseInt(extraPort.trim()));
			factory.addAdditionalConnectors(connector);
		};
	}

}
