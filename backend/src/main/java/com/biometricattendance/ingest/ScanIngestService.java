package com.biometricattendance.ingest;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.attendance.AttendanceScanRepository;
import com.biometricattendance.attendance.RejectedScanLine;
import com.biometricattendance.attendance.RejectedScanLineRepository;
import com.biometricattendance.attendance.ScanFlag;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.device.Device;
import com.biometricattendance.device.DeviceRepository;
import com.biometricattendance.employee.Employee;
import com.biometricattendance.employee.EmployeeRepository;

/**
 * Inti penerimaan scan (K-054): tidak mengenal protokol mesin. Semua scan satu kiriman disimpan dalam satu
 * transaksi; adapter baru boleh membalas sukses ke mesin setelah method ini selesai (commit), sesuai K-020/K-061.
 */
@Service
public class ScanIngestService {

	private static final Logger log = LoggerFactory.getLogger(ScanIngestService.class);

	private final DeviceRepository devices;
	private final EmployeeRepository employees;
	private final AttendanceScanRepository scans;
	private final RejectedScanLineRepository rejectedLines;
	private final ApplicationEventPublisher events;
	private final Duration futureTolerance;

	public ScanIngestService(DeviceRepository devices, EmployeeRepository employees, AttendanceScanRepository scans,
			RejectedScanLineRepository rejectedLines, ApplicationEventPublisher events,
			@Value("${app.scan.future-tolerance}") Duration futureTolerance) {
		this.devices = devices;
		this.employees = employees;
		this.scans = scans;
		this.rejectedLines = rejectedLines;
		this.events = events;
		this.futureTolerance = futureTolerance;
	}

	/**
	 * @param deviceId mesin yang sudah divalidasi adapter
	 * @param stamp penanda posisi kiriman dari protokol (boleh null), disimpan untuk handshake berikutnya
	 */
	@Transactional
	public IngestResult ingest(Long deviceId, List<ScanEvent> batch, List<RejectedLine> rejected, String stamp) {
		Device device = devices.findWithBranch(deviceId).orElseThrow(() -> ApiException.notFound("Mesin"));
		// Jam mesin tidak membawa zona waktu: ditafsirkan dengan zona cabang tempat mesin berada.
		ZoneId zone = ZoneId.of(device.getBranch().getTimezone());
		Instant now = Instant.now();
		Map<String, Optional<Employee>> byPin = new HashMap<>();

		for (RejectedLine line : rejected) {
			rejectedLines.save(new RejectedScanLine(deviceId, line.rawLine(), line.reason()));
		}

		int saved = 0;
		int ignored = 0;
		for (ScanEvent event : batch) {
			Instant scannedAt = event.localTime().atZone(zone).toInstant();
			if (scannedAt.isBefore(device.getAcceptScansFrom())) {
				ignored++;
				continue;
			}
			Optional<Employee> employee = byPin.computeIfAbsent(event.pin(), employees::findByDevicePin);
			Set<ScanFlag> flags = EnumSet.noneOf(ScanFlag.class);
			if (employee.isEmpty()) {
				flags.add(ScanFlag.PIN_TIDAK_DIKENAL);
			}
			else if (!employee.get().getBranch().getId().equals(device.getBranch().getId())) {
				flags.add(ScanFlag.CABANG_BERBEDA);
			}
			if (scannedAt.isAfter(now.plus(futureTolerance))) {
				flags.add(ScanFlag.WAKTU_DI_MASA_DEPAN);
			}
			saved += scans.insertIgnoringDuplicate(deviceId, event.pin(), employee.map(Employee::getId).orElse(null),
					scannedAt, event.localTime(), event.statusCode(), event.verifyMode(), ScanFlag.join(flags),
					event.rawLine(), event.deviceScanId() == null ? null : event.deviceScanId().toString());
		}

		if (ignored > 0) {
			device.addIgnoredScans(ignored);
		}
		if (stamp != null && !stamp.isBlank()) {
			device.setLastAttlogStamp(stamp.length() > 30 ? stamp.substring(0, 30) : stamp);
		}
		if (saved > 0) {
			events.publishEvent(new ScansSavedEvent(deviceId, saved));
		}
		IngestResult result = new IngestResult(saved, batch.size() - ignored - saved, ignored, rejected.size());
		log.info("Mesin {}: {}", device.getSerialNumber(), result);
		return result;
	}

}
