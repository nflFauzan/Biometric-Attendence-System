package com.biometricattendance.leave;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.biometricattendance.IntegrationTest;

/** Batas laju endpoint verifikasi publik per IP (spec 2026-10-24 bagian 8). Context terpisah: batas 3 per menit. */
@TestPropertySource(properties = "app.public-letters.rate-per-minute=3")
class PublicLetterRateLimitTests extends IntegrationTest {

	@Test
	void fourthRequestInTheSameMinuteIsRejected() throws Exception {
		for (int i = 0; i < 3; i++) {
			mockMvc.perform(get("/api/public/letters/" + "b".repeat(32))).andExpect(status().isNotFound());
		}
		mockMvc.perform(get("/api/public/letters/" + "b".repeat(32)))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.detail").value("Terlalu banyak permintaan, coba lagi sebentar"));
	}

}
