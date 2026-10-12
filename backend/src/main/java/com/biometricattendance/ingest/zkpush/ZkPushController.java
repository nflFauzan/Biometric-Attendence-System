package com.biometricattendance.ingest.zkpush;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceContactService;
import com.biometricattendance.ingest.ScanIngestService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Adapter protokol push ZKTeco (ADMS) untuk mesin uji Solution X401 (K-054, K-055). Tanpa login; mesin
 * dikenali dari nomor seri yang terdaftar dan aktif. Balasan "OK" untuk data scan hanya dikirim setelah
 * transaksi penyimpanan selesai (K-061); selain itu mesin menyimpan scan dan mengirim ulang.
 */
@RestController
@RequestMapping("/iclock")
public class ZkPushController {

	private static final Logger log = LoggerFactory.getLogger(ZkPushController.class);

	static final int MAX_BODY_BYTES = 1024 * 1024;

	private final DeviceContactService contacts;
	private final ScanIngestService ingest;

	public ZkPushController(DeviceContactService contacts, ScanIngestService ingest) {
		this.contacts = contacts;
		this.ingest = ingest;
	}

	/** Handshake: mesin meminta konfigurasi saat menyala. */
	@GetMapping("/cdata")
	ResponseEntity<String> handshake(@RequestParam("SN") String sn, HttpServletRequest request) {
		Optional<Device> device = contacts.contact(sn, request.getRemoteAddr(), null, null);
		if (device.isEmpty()) {
			return unknownDevice(sn);
		}
		return text(HttpStatus.OK, ZkPushOptions.handshake(device.get(), Instant.now()));
	}

	/** Data dari mesin. Hanya tabel ATTLOG yang disimpan; tabel lain dibalas OK tanpa disimpan (K-063). */
	@PostMapping("/cdata")
	ResponseEntity<String> upload(@RequestParam("SN") String sn,
			@RequestParam(name = "table", required = false) String table,
			@RequestParam(name = "Stamp", required = false) String stamp, HttpServletRequest request)
			throws IOException {
		byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
		if (bytes.length > MAX_BODY_BYTES) {
			return text(HttpStatus.CONTENT_TOO_LARGE, "TOO LARGE");
		}
		Optional<Device> device = contacts.contact(sn, request.getRemoteAddr(), null, null);
		if (device.isEmpty()) {
			return unknownDevice(sn);
		}
		if (!"ATTLOG".equalsIgnoreCase(table)) {
			log.info("Mesin {} mengirim tabel {} ({} byte); tidak disimpan", sn, table, bytes.length);
			return text(HttpStatus.OK, "OK");
		}
		ZkPushParser.Batch batch = ZkPushParser.parseAttlog(new String(bytes, StandardCharsets.UTF_8));
		ingest.ingest(device.get().getId(), batch.events(), batch.rejected(), stamp);
		return text(HttpStatus.OK, "OK");
	}

	/** Heartbeat. Parameter INFO (jika ada) dimulai dengan versi firmware. Belum ada perintah untuk mesin. */
	@GetMapping("/getrequest")
	ResponseEntity<String> heartbeat(@RequestParam("SN") String sn,
			@RequestParam(name = "INFO", required = false) String info, HttpServletRequest request) {
		String firmware = info == null || info.isBlank() ? null : info.split(",", 2)[0].trim();
		if (contacts.contact(sn, request.getRemoteAddr(), firmware, info).isEmpty()) {
			return unknownDevice(sn);
		}
		return text(HttpStatus.OK, "OK");
	}

	/** Hasil perintah dari server. Server belum mengirim perintah, jadi hanya dicatat sebagai kontak. */
	@PostMapping("/devicecmd")
	ResponseEntity<String> commandResult(@RequestParam("SN") String sn, HttpServletRequest request) {
		if (contacts.contact(sn, request.getRemoteAddr(), null, null).isEmpty()) {
			return unknownDevice(sn);
		}
		return text(HttpStatus.OK, "OK");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<String> missingParameter(MissingServletRequestParameterException ex) {
		return text(HttpStatus.BAD_REQUEST, "BAD REQUEST: " + ex.getParameterName());
	}

	/** Gagal menyimpan: jangan balas OK agar mesin mengirim ulang (K-020). */
	@ExceptionHandler(Exception.class)
	ResponseEntity<String> failure(Exception ex) {
		log.error("Gagal memproses kiriman mesin", ex);
		return text(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR");
	}

	/** K-067: mesin tidak terdaftar/nonaktif ditolak tanpa OK, sehingga scan tetap disimpan di mesin. */
	private static ResponseEntity<String> unknownDevice(String sn) {
		log.warn("Mesin tidak terdaftar atau nonaktif mencoba terhubung: {}", sn);
		return text(HttpStatus.UNAUTHORIZED, "UNKNOWN DEVICE");
	}

	private static ResponseEntity<String> text(HttpStatus status, String body) {
		return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
	}

}
