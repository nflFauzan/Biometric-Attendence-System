package com.biometricattendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.biometricattendance.account.Role;
import com.biometricattendance.account.UserAccount;
import com.biometricattendance.account.UserAccountRepository;

/**
 * K-014 dan arsitektur.md §10.1: atribut cookie sesi diperiksa lewat server HTTP sungguhan
 * (bukan MockMvc), karena header Set-Cookie ditulis oleh filter Spring Session dan container.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieHttpTests {

	@LocalServerPort
	private int port;

	@Autowired
	private UserAccountRepository accounts;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void sessionCookieIsHttpOnlyAndSameSiteLax() throws Exception {
		UserAccount account = new UserAccount();
		account.setUsername("cookie-uji");
		account.setPasswordHash(passwordEncoder.encode("password-awal"));
		account.setRole(Role.HR_FINANCE);
		account.setMustChangePassword(false);
		accounts.save(account);

		HttpClient client = HttpClient.newHttpClient();
		HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).build(),
				HttpResponse.BodyHandlers.ofString());
		String xsrf = cookieValue(csrf.headers().allValues("Set-Cookie"), "XSRF-TOKEN");

		HttpResponse<String> login = client.send(HttpRequest.newBuilder(uri("/api/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-XSRF-TOKEN", xsrf)
			.header("Cookie", "XSRF-TOKEN=" + xsrf)
			.POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"cookie-uji\",\"password\":\"password-awal\"}"))
			.build(), HttpResponse.BodyHandlers.ofString());

		assertThat(login.statusCode()).isEqualTo(200);
		String session = login.headers()
			.allValues("Set-Cookie")
			.stream()
			.filter(header -> header.startsWith("SESSION="))
			.findFirst()
			.orElseThrow();
		assertThat(session).containsIgnoringCase("HttpOnly")
			.containsIgnoringCase("SameSite=Lax")
			.as("default development tanpa HTTPS")
			.doesNotContainIgnoringCase("Secure");
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static String cookieValue(List<String> setCookies, String name) {
		return setCookies.stream()
			.filter(header -> header.startsWith(name + "="))
			.map(header -> header.substring(name.length() + 1, header.indexOf(';')))
			.findFirst()
			.orElseThrow();
	}

}
