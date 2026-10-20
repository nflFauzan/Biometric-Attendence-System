package com.biometricattendance.ingest.rakitan;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceContactService;
import com.biometricattendance.device.KeyContact;
import com.biometricattendance.devicecommand.DeviceCommand;
import com.biometricattendance.devicecommand.DeviceCommandService;
import com.biometricattendance.devicesettings.DeviceSettingsService;
import com.biometricattendance.devicesettings.KeyRotationService;
import com.biometricattendance.devicesettings.NetworkDtos.NetworkPayload;
import com.biometricattendance.devicesettings.NetworkService;
import com.biometricattendance.ingest.rakitan.RakitanDtos.CommandItem;
import com.biometricattendance.ingest.rakitan.RakitanDtos.ProgressRequest;
import com.biometricattendance.ingest.rakitan.RakitanDtos.ResultRequest;
import com.biometricattendance.ingest.rakitan.RakitanDtos.ErrorBody;
import com.biometricattendance.ingest.rakitan.RakitanDtos.HeartbeatRequest;
import com.biometricattendance.ingest.IngestResult;
import com.biometricattendance.ingest.ScanIngestService;
import com.biometricattendance.ingest.rakitan.RakitanDtos.HeartbeatResponse;
import com.biometricattendance.ingest.rakitan.RakitanDtos.ScanBatch;
import com.biometricattendance.ingest.rakitan.RakitanDtos.ScansResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Adapter protokol alat rakitan {@code /device/v1} (K-087, docs/protokol-alat-rakitan.md). Mesin dikenali dari
 * kunci di header {@code Authorization: Bearer} (K-088); endpoint ini hanya menerima mesin {@code RAKITAN_V1}.
 */
@RestController
@RequestMapping("/device/v1")
public class RakitanController {

	private static final Logger log = LoggerFactory.getLogger(RakitanController.class);
	private static final String BEARER = "Bearer ";

	public static final int MAX_SCANS = 200;

	private final DeviceContactService contacts;
	private final RakitanScanParser parser;
	private final ScanIngestService ingest;
	private final DeviceCommandService commands;
	private final DeviceSettingsService settings;
	private final NetworkService networks;
	private final KeyRotationService keyRotation;
	private final Duration machineTimeout;

	public RakitanController(DeviceContactService contacts, RakitanScanParser parser, ScanIngestService ingest,
			DeviceCommandService commands, DeviceSettingsService settings, NetworkService networks,
			KeyRotationService keyRotation, @Value("${app.device.enroll.machine-timeout:60s}") Duration machineTimeout) {
		this.settings = settings;
		this.networks = networks;
		this.keyRotation = keyRotation;
		this.contacts = contacts;
		this.parser = parser;
		this.ingest = ingest;
		this.commands = commands;
		this.machineTimeout = machineTimeout;
	}

	/**
	 * Kiriman scan (§5). Balasan 200 hanya setelah transaksi simpan selesai (K-020/K-061); ingest() berjalan dalam
	 * transaksinya sendiri dan sudah commit saat method ini membalas.
	 */
	@PostMapping("/scans")
	ResponseEntity<?> scans(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestBody ScanBatch body, HttpServletRequest request) {
		KeyContact contact = contacts.contactByKey(key(authorization), request.getRemoteAddr(), null, null, null, null);
		if (contact.status() != KeyContact.Status.ACCEPTED) {
			return refused(contact);
		}
		if (body.scans() == null) {
			return error(HttpStatus.BAD_REQUEST, "FORMAT_SALAH");
		}
		if (body.scans().size() > MAX_SCANS) {
			return error(HttpStatus.CONTENT_TOO_LARGE, "BATCH_TERLALU_BESAR");
		}
		Device device = contact.device();
		RakitanScanParser.Parsed parsed = parser.parse(body.scans(), ZoneId.of(device.getBranch().getTimezone()));
		IngestResult result = ingest.ingest(device.getId(), parsed.events(), parsed.rejected(), null);
		return ResponseEntity.ok(new ScansResponse(parsed.accepted(), result.saved(), result.duplicates(),
				result.ignored(), result.rejected()));
	}

	/** Heartbeat setiap 5 detik: mencatat kontak dan mengirim jam server untuk RTC mesin (§4). */
	@PostMapping("/heartbeat")
	ResponseEntity<?> heartbeat(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestBody HeartbeatRequest body, HttpServletRequest request) {
		String ip = body.ip() != null && !body.ip().isBlank() ? body.ip().trim() : request.getRemoteAddr();
		KeyContact contact = contacts.contactByKey(key(authorization), ip, body.firmware(), info(body),
				body.sensor() == null ? null : body.sensor().used(),
				body.sensor() == null ? null : body.sensor().capacity());
		if (contact.status() != KeyContact.Status.ACCEPTED) {
			return refused(contact);
		}
		Device device = contact.device();
		ZoneId zone = ZoneId.of(device.getBranch().getTimezone());
		Instant now = Instant.now();
		DeviceCommandService.Delivery delivery = commands.deliver(device, now);
		List<CommandItem> items = delivery.commands()
			.stream()
			.map(c -> item(device, c))
			.flatMap(Optional::stream)
			.toList();
		return ResponseEntity.ok(new HeartbeatResponse(formatServerTime(now, zone), zone.getId(), device.getName(),
				device.getBranch().getName(), items, delivery.cancel().stream().map(UUID::toString).toList(),
				settings.packageFor(device, body.settingsVersion())));
	}

	/** Satu perintah untuk balasan heartbeat; kosong jika isi rahasianya tidak bisa dibuka (NetworkService#payload). */
	private Optional<CommandItem> item(Device device, DeviceCommand c) {
		NetworkPayload network = null;
		String newKey = null;
		if (DeviceCommand.TYPE_APPLY_NETWORK.equals(c.getType())) {
			Optional<NetworkPayload> payload = networks.payload(c.getNetworkConfigId());
			if (payload.isEmpty()) {
				return Optional.empty();
			}
			network = payload.get();
		}
		if (DeviceCommand.TYPE_ROTATE_KEY.equals(c.getType())) {
			Optional<String> pending = keyRotation.pendingKey(device);
			if (pending.isEmpty()) {
				return Optional.empty();
			}
			newKey = pending.get();
		}
		return Optional.of(new CommandItem(c.getId().toString(), c.getType(), c.getDevicePin(), c.getFinger(),
				c.getDisplayName(), DeviceCommand.TYPE_ENROLL.equals(c.getType()) ? machineTimeout.toSeconds() : null,
				network, newKey));
	}

	/** Langkah tempelan jari selama ENROLL (§6). */
	@PostMapping("/commands/{id}/progress")
	ResponseEntity<?> progress(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@PathVariable String id, @RequestBody ProgressRequest body, HttpServletRequest request) {
		KeyContact contact = contacts.contactByKey(key(authorization), request.getRemoteAddr(), null, null, null, null);
		if (contact.status() != KeyContact.Status.ACCEPTED) {
			return refused(contact);
		}
		return reply(commands.progress(contact.device(), id, body.step()), false);
	}

	/** Hasil akhir perintah (§6). Hasil untuk perintah yang sudah selesai dibalas 200 agar mesin berhenti mengirim. */
	@PostMapping("/commands/{id}/result")
	ResponseEntity<?> result(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@PathVariable String id, @RequestBody ResultRequest body, HttpServletRequest request) {
		KeyContact contact = contacts.contactByKey(key(authorization), request.getRemoteAddr(), null, null, null, null);
		if (contact.status() != KeyContact.Status.ACCEPTED) {
			return refused(contact);
		}
		return reply(commands.result(contact.device(), id, body.status(), body.error(), body.pin(), body.detail()),
				true);
	}

	private static ResponseEntity<?> reply(DeviceCommandService.CommandUpdate update, boolean finalResult) {
		return switch (update) {
			case OK -> ResponseEntity.ok(Map.of());
			case NOT_FOUND -> error(HttpStatus.NOT_FOUND, "PERINTAH_TIDAK_DIKENAL");
			case NOT_ACTIVE -> finalResult ? ResponseEntity.ok(Map.of())
					: error(HttpStatus.CONFLICT, "PERINTAH_TIDAK_AKTIF");
			case BAD_REQUEST -> error(HttpStatus.BAD_REQUEST, "FORMAT_SALAH");
		};
	}

	/** Waktu di zona cabang, dipotong ke detik; detik selalu ditulis (OffsetDateTime.toString membuang ":00"). */
	public static String formatServerTime(Instant now, ZoneId zone) {
		return now.truncatedTo(ChronoUnit.SECONDS).atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException ex) {
		return error(HttpStatus.BAD_REQUEST, "FORMAT_SALAH");
	}

	/** Gagal menyimpan: jangan balas sukses agar mesin mengirim ulang (K-020). */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorBody> failure(Exception ex) {
		log.error("Gagal memproses kiriman alat rakitan", ex);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "GAGAL_SIMPAN");
	}

	static ResponseEntity<ErrorBody> refused(KeyContact contact) {
		if (contact.status() == KeyContact.Status.INACTIVE) {
			return error(HttpStatus.FORBIDDEN, "MESIN_NONAKTIF");
		}
		return error(HttpStatus.UNAUTHORIZED, "KUNCI_DITOLAK");
	}

	static ResponseEntity<ErrorBody> error(HttpStatus status, String code) {
		return ResponseEntity.status(status).body(new ErrorBody(code));
	}

	static String key(String authorization) {
		return authorization != null && authorization.startsWith(BEARER) ? authorization.substring(BEARER.length())
				: null;
	}

	/** Ringkasan heartbeat untuk detail mesin, mis. "uptime=3600s; antrean=2; sensor=12/150". */
	private static String info(HeartbeatRequest body) {
		List<String> parts = new ArrayList<>();
		if (body.uptimeSeconds() != null) {
			parts.add("uptime=" + body.uptimeSeconds() + "s");
		}
		if (body.pendingScans() != null) {
			parts.add("antrean=" + body.pendingScans());
		}
		if (body.sensor() != null && body.sensor().used() != null && body.sensor().capacity() != null) {
			parts.add("sensor=" + body.sensor().used() + "/" + body.sensor().capacity());
		}
		return parts.isEmpty() ? null : String.join("; ", parts);
	}

}
