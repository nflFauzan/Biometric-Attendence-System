package com.biometricattendance.leave;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Alamat verifikasi publik pada surat: {@code <base>/verifikasi/<token>} (spec 2026-10-24 bagian 8). */
@Component
public class LetterLinks {

	private final String configuredBase;

	public LetterLinks(@Value("${app.public-base-url:}") String configuredBase) {
		this.configuredBase = configuredBase == null ? "" : configuredBase.trim();
	}

	/** Basis dari {@code app.public-base-url}; bila kosong, dari origin request yang sedang berjalan. */
	public String verifyUrl(String token) {
		String base = configuredBase.isEmpty() ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
				: configuredBase;
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		return base + "/verifikasi/" + token;
	}

}
