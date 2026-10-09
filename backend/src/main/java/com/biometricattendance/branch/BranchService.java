package com.biometricattendance.branch;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.branch.BranchDtos.BranchRequest;
import com.biometricattendance.branch.BranchDtos.BranchResponse;
import com.biometricattendance.common.ApiException;

@Service
public class BranchService {

	static final String ENTITY = "BRANCH";

	private final BranchRepository repository;
	private final AuditService audit;

	public BranchService(BranchRepository repository, AuditService audit) {
		this.repository = repository;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<BranchResponse> list() {
		return repository.findAllByOrderByNameAsc().stream().map(BranchResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public BranchResponse get(Long id) {
		return BranchResponse.from(find(id));
	}

	@Transactional
	public BranchResponse create(BranchRequest request) {
		if (repository.existsByCodeIgnoreCase(request.code().trim())) {
			throw ApiException.conflict("Kode cabang sudah dipakai");
		}
		Branch branch = new Branch();
		apply(branch, request);
		BranchResponse created = BranchResponse.from(repository.save(branch));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		return created;
	}

	@Transactional
	public BranchResponse update(Long id, BranchRequest request) {
		Branch branch = find(id);
		if (repository.existsByCodeIgnoreCaseAndIdNot(request.code().trim(), id)) {
			throw ApiException.conflict("Kode cabang sudah dipakai");
		}
		BranchResponse before = BranchResponse.from(branch);
		apply(branch, request);
		BranchResponse after = BranchResponse.from(repository.save(branch));
		audit.record("UPDATE", ENTITY, id, before, after);
		return after;
	}

	Branch find(Long id) {
		return repository.findById(id).orElseThrow(() -> ApiException.notFound("Cabang"));
	}

	private void apply(Branch branch, BranchRequest request) {
		try {
			ZoneId.of(request.timezone().trim());
		}
		catch (DateTimeException ex) {
			throw ApiException.badRequest("Zona waktu tidak dikenal: " + request.timezone());
		}
		branch.setCode(request.code().trim().toUpperCase());
		branch.setName(request.name().trim());
		branch.setAddress(request.address());
		branch.setTimezone(request.timezone().trim());
		branch.setActive(request.active());
	}

}
