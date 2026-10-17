package com.biometricattendance.device;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.device.DeviceDtos.DeviceRequest;
import com.biometricattendance.device.DeviceDtos.DeviceResponse;
import com.biometricattendance.device.DeviceDtos.IssuedKey;

@Service
public class DeviceService {

	static final String ENTITY = "DEVICE";

	private final DeviceRepository repository;
	private final BranchRepository branches;
	private final AuditService audit;
	private final DeviceStatus status;
	private final UnregisteredDeviceTracker unregistered;
	private final String setupServerUrl;

	public DeviceService(DeviceRepository repository, BranchRepository branches, AuditService audit,
			DeviceStatus status, UnregisteredDeviceTracker unregistered,
			@Value("${app.device.public-url:}") String setupServerUrl) {
		this.repository = repository;
		this.branches = branches;
		this.audit = audit;
		this.status = status;
		this.unregistered = unregistered;
		this.setupServerUrl = StringUtils.hasText(setupServerUrl) ? setupServerUrl.trim() : null;
	}

	@Transactional(readOnly = true)
	public List<DeviceResponse> list() {
		Instant now = Instant.now();
		return repository.findAllWithBranch().stream().map(d -> DeviceResponse.from(d, status.isOnline(d, now))).toList();
	}

	public List<UnregisteredDeviceTracker.Attempt> unregisteredAttempts() {
		return unregistered.list();
	}

	@Transactional
	public DeviceResponse create(DeviceRequest request) {
		boolean rakitan = Device.PROTOCOL_RAKITAN.equals(request.protocol());
		Device device = new Device();
		String key = null;
		if (rakitan) {
			// K-099: menerbitkan kunci hanya untuk Owner.
			if (!CurrentUser.hasRole("OWNER")) {
				throw ApiException.forbidden("Hanya Owner yang bisa menambah alat rakitan");
			}
			device.setProtocol(Device.PROTOCOL_RAKITAN);
			device.setSerialNumber(newDeviceId());
			key = DeviceKeys.newKey();
			device.issueKey(DeviceKeys.hash(key));
		}
		else {
			String serial = requireSerial(request);
			if (repository.existsBySerialNumberIgnoreCase(serial)) {
				throw ApiException.conflict("Serial number sudah terdaftar");
			}
			device.setSerialNumber(serial);
		}
		// K-057: tanpa tanggal batas, mesin baru hanya menerima scan sejak didaftarkan.
		device.setAcceptScansFrom(request.acceptScansFrom() == null ? Instant.now() : request.acceptScansFrom());
		apply(device, request);
		DeviceResponse created = response(repository.save(device));
		// Kunci tidak pernah masuk audit log (K-088).
		audit.record("CREATE", ENTITY, created.id(), null, created);
		unregistered.forget(device.getSerialNumber());
		return key == null ? created : created.withKey(new IssuedKey(key, setupServerUrl));
	}

	@Transactional
	public DeviceResponse update(Long id, DeviceRequest request) {
		Device device = repository.findById(id).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (request.protocol() != null && !request.protocol().equals(device.getProtocol())) {
			throw ApiException.badRequest("Jenis mesin tidak bisa diubah. Buat mesin baru.");
		}
		if (!device.isRakitan()) {
			String serial = requireSerial(request);
			if (repository.existsBySerialNumberIgnoreCaseAndIdNot(serial, id)) {
				throw ApiException.conflict("Serial number sudah terdaftar");
			}
		}
		DeviceResponse before = response(device);
		if (request.acceptScansFrom() != null) {
			device.setAcceptScansFrom(request.acceptScansFrom());
		}
		if (!device.isRakitan()) {
			// ID alat rakitan dibuat server dan tidak bisa diubah (K-099).
			device.setSerialNumber(request.serialNumber().trim());
		}
		apply(device, request);
		DeviceResponse after = response(repository.save(device));
		audit.record("UPDATE", ENTITY, id, before, after);
		unregistered.forget(device.getSerialNumber());
		return after;
	}

	/** "Buat kode baru": kunci baru langsung menggantikan kunci lama (K-088, spec §6.1). */
	@Transactional
	public DeviceResponse issueKey(Long id) {
		Device device = repository.findWithBranch(id).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (!device.isRakitan()) {
			throw ApiException.badRequest("Mesin ZK-Push tidak memakai kunci");
		}
		DeviceResponse before = response(device);
		String key = DeviceKeys.newKey();
		device.issueKey(DeviceKeys.hash(key));
		DeviceResponse after = response(repository.save(device));
		audit.record("ISSUE_KEY", ENTITY, id, before, after);
		return after.withKey(new IssuedKey(key, setupServerUrl));
	}

	private String newDeviceId() {
		String id = DeviceKeys.newDeviceId();
		while (repository.existsBySerialNumberIgnoreCase(id)) {
			id = DeviceKeys.newDeviceId();
		}
		return id;
	}

	private static String requireSerial(DeviceRequest request) {
		if (!StringUtils.hasText(request.serialNumber())) {
			throw ApiException.badRequest("Serial number wajib diisi");
		}
		return request.serialNumber().trim();
	}

	private DeviceResponse response(Device device) {
		return DeviceResponse.from(device, status.isOnline(device, Instant.now()));
	}

	private void apply(Device device, DeviceRequest request) {
		device.setName(request.name().trim());
		device.setBranch(branches.findById(request.branchId()).orElseThrow(() -> ApiException.notFound("Cabang")));
		device.setActive(request.active());
	}

}
