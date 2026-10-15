package com.biometricattendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * K-086: dengan app.device.extra-port terisi, backend juga melayani request di port kedua (dipakai mesin X401
 * saat backend dijalankan lewat start-dev.bat, di luar Docker).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DevicePortConfigTests {

	private static final int EXTRA_PORT = freePort();

	@DynamicPropertySource
	static void extraPort(DynamicPropertyRegistry registry) {
		registry.add("app.device.extra-port", () -> String.valueOf(EXTRA_PORT));
	}

	@LocalServerPort
	private int port;

	@Test
	void extraPortServesTheSameApplication() throws Exception {
		assertThat(EXTRA_PORT).isNotEqualTo(port);
		HttpClient client = HttpClient.newHttpClient();

		HttpResponse<String> health = client.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + EXTRA_PORT + "/actuator/health")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(health.statusCode()).isEqualTo(200);
		assertThat(health.body()).contains("UP");
	}

	private static int freePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
