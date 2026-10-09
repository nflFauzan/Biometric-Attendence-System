package com.biometricattendance.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.biometricattendance.audit.AuditService;
import com.biometricattendance.category.EmployeeCategoryDtos.CategoryRequest;
import com.biometricattendance.category.EmployeeCategoryDtos.CategoryResponse;
import com.biometricattendance.common.ApiException;

@Service
public class EmployeeCategoryService {

	static final String ENTITY = "EMPLOYEE_CATEGORY";

	private final EmployeeCategoryRepository repository;
	private final AuditService audit;

	public EmployeeCategoryService(EmployeeCategoryRepository repository, AuditService audit) {
		this.repository = repository;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<CategoryResponse> list() {
		return repository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
	}

	@Transactional
	public CategoryResponse create(CategoryRequest request) {
		if (repository.existsByCodeIgnoreCase(request.code().trim())) {
			throw ApiException.conflict("Kode kategori sudah dipakai");
		}
		EmployeeCategory category = new EmployeeCategory();
		apply(category, request);
		CategoryResponse created = CategoryResponse.from(repository.save(category));
		audit.record("CREATE", ENTITY, created.id(), null, created);
		return created;
	}

	@Transactional
	public CategoryResponse update(Long id, CategoryRequest request) {
		EmployeeCategory category = repository.findById(id).orElseThrow(() -> ApiException.notFound("Kategori"));
		if (repository.existsByCodeIgnoreCaseAndIdNot(request.code().trim(), id)) {
			throw ApiException.conflict("Kode kategori sudah dipakai");
		}
		CategoryResponse before = CategoryResponse.from(category);
		apply(category, request);
		CategoryResponse after = CategoryResponse.from(repository.save(category));
		audit.record("UPDATE", ENTITY, id, before, after);
		return after;
	}

	private void apply(EmployeeCategory category, CategoryRequest request) {
		category.setCode(request.code().trim().toUpperCase());
		category.setName(request.name().trim());
		category.setActive(request.active());
	}

}
