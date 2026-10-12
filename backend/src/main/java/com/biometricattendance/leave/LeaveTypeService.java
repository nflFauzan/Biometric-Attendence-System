package com.biometricattendance.leave;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.common.ApiException;
import com.biometricattendance.leave.LeaveDtos.LeaveTypeRequest;
import com.biometricattendance.leave.LeaveDtos.LeaveTypeResponse;

/** Jenis izin/cuti (K-070): diatur Owner, tidak dihapus agar pengajuan lama tetap terbaca. */
@Service
public class LeaveTypeService {

	static final String ENTITY = "LEAVE_TYPE";

	private final LeaveTypeRepository repository;
	private final AuditService audit;

	public LeaveTypeService(LeaveTypeRepository repository, AuditService audit) {
		this.repository = repository;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<LeaveTypeResponse> list(boolean activeOnly) {
		return (activeOnly ? repository.findByActiveTrueOrderByNameAsc() : repository.findAllByOrderByNameAsc())
			.stream()
			.map(LeaveTypeResponse::from)
			.toList();
	}

	@Transactional
	public LeaveTypeResponse create(LeaveTypeRequest request) {
		String name = request.name().trim();
		if (repository.existsByNameIgnoreCase(name)) {
			throw ApiException.conflict("Jenis izin dengan nama tersebut sudah ada");
		}
		LeaveType type = new LeaveType();
		apply(type, request);
		LeaveTypeResponse created = LeaveTypeResponse.from(repository.save(type));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		return created;
	}

	@Transactional
	public LeaveTypeResponse update(Long id, LeaveTypeRequest request) {
		LeaveType type = repository.findById(id).orElseThrow(() -> ApiException.notFound("Jenis izin"));
		if (repository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
			throw ApiException.conflict("Jenis izin dengan nama tersebut sudah ada");
		}
		LeaveTypeResponse before = LeaveTypeResponse.from(type);
		apply(type, request);
		LeaveTypeResponse after = LeaveTypeResponse.from(repository.save(type));
		audit.record("UPDATE", ENTITY, id, before, after);
		return after;
	}

	private static void apply(LeaveType type, LeaveTypeRequest request) {
		type.setName(request.name().trim());
		type.setRequiresDocument(request.requiresDocument());
		type.setPaid(request.paid());
		type.setDescription(request.description() == null || request.description().isBlank() ? null
				: request.description().trim());
		type.setActive(request.active());
	}

}
