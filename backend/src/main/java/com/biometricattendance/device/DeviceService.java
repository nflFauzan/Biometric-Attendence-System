package com.biometricattendance.device;

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

	public DeviceService(DeviceRepository repository, BranchRepository branches, AuditService audit) {
		this.repository = repository;
		this.branches = branches;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<DeviceResponse> list() {
		return repository.findAllWithBranch().stream().map(DeviceResponse::from).toList();
	}

	@Transactional
	public DeviceResponse create(DeviceRequest request) {
		if (repository.existsBySerialNumberIgnoreCase(request.serialNumber().trim())) {
			throw ApiException.conflict("Serial number sudah terdaftar");
		}
		Device device = new Device();
		apply(device, request);
		DeviceResponse created = DeviceResponse.from(repository.save(device));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		return created;
	}

	@Transactional
	public DeviceResponse update(Long id, DeviceRequest request) {
		Device device = repository.findById(id).orElseThrow(() -> ApiException.notFound("Mesin"));
		if (repository.existsBySerialNumberIgnoreCaseAndIdNot(request.serialNumber().trim(), id)) {
			throw ApiException.conflict("Serial number sudah terdaftar");
		}
		DeviceResponse before = DeviceResponse.from(device);
		apply(device, request);
		DeviceResponse after = DeviceResponse.from(repository.save(device));
		audit.record("UPDATE", ENTITY, id, before, after);
		return after;
	}

	private void apply(Device device, DeviceRequest request) {
		device.setName(request.name().trim());
		device.setSerialNumber(request.serialNumber().trim());
		device.setBranch(branches.findById(request.branchId()).orElseThrow(() -> ApiException.notFound("Cabang")));
		device.setActive(request.active());
	}

}
