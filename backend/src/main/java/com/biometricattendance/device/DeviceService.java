package com.biometricattendance.device;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.branch.BranchRepository;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.device.DeviceDtos.DeviceRequest;
import com.biometricattendance.device.DeviceDtos.DeviceResponse;

@Service
public class DeviceService {

	static final String ENTITY = "DEVICE";

	private final DeviceRepository repository;
	private final BranchRepository branches;
	private final AuditService audit;
	private final DeviceStatus status;
	private final UnregisteredDeviceTracker unregistered;

	public DeviceService(DeviceRepository repository, BranchRepository branches, AuditService audit,
			DeviceStatus status, UnregisteredDeviceTracker unregistered) {
		this.repository = repository;
		this.branches = branches;
		this.audit = audit;
		this.status = status;
		this.unregistered = unregistered;
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
		if (repository.existsBySerialNumberIgnoreCase(request.serialNumber().trim())) {
			throw ApiException.conflict("Serial number sudah terdaftar");
		}
		Device device = new Device();
		// K-057: tanpa tanggal batas, mesin baru hanya menerima scan sejak didaftarkan.
		device.setAcceptScansFrom(request.acceptScansFrom() == null ? Instant.now() : request.acceptScansFrom());
		apply(device, request);
		DeviceResponse created = response(repository.save(device));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		unregistered.forget(device.getSerialNumber());
		return created;
	}

	@Transactional
	public DeviceResponse update(Long id, DeviceRequest request) {
		Device device = repository.findById(id).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (repository.existsBySerialNumberIgnoreCaseAndIdNot(request.serialNumber().trim(), id)) {
			throw ApiException.conflict("Serial number sudah terdaftar");
		}
		DeviceResponse before = response(device);
		if (request.acceptScansFrom() != null) {
			device.setAcceptScansFrom(request.acceptScansFrom());
		}
		apply(device, request);
		DeviceResponse after = response(repository.save(device));
		audit.record("UPDATE", ENTITY, id, before, after);
		unregistered.forget(device.getSerialNumber());
		return after;
	}

	private DeviceResponse response(Device device) {
		return DeviceResponse.from(device, status.isOnline(device, Instant.now()));
	}

	private void apply(Device device, DeviceRequest request) {
		device.setName(request.name().trim());
		device.setSerialNumber(request.serialNumber().trim());
		device.setBranch(branches.findById(request.branchId()).orElseThrow(() -> ApiException.notFound("Cabang")));
		device.setActive(request.active());
	}

}
