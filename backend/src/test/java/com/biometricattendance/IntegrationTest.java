package com.biometricattendance;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Basis test integrasi: satu Spring context dan satu PostgreSQL (Testcontainers) dipakai bersama.
 * Data antar test tidak dibersihkan, jadi setiap test memakai kode/username unik.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = { "app.bootstrap.owner-username=owner", "app.bootstrap.owner-password=owner-awal-123",
		"app.uploads.dir=target/test-uploads",
		"app.crypto.data-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=" })
public abstract class IntegrationTest {

	@Autowired
	private WebApplicationContext context;

	protected MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	protected static String unique(String prefix) {
		return prefix + UUID.randomUUID().toString().substring(0, 8);
	}

}
