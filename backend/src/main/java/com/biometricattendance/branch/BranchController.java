package com.biometricattendance.branch;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.branch.BranchDtos.BranchRequest;
import com.biometricattendance.branch.BranchDtos.BranchResponse;

import jakarta.validation.Valid;

/** Cabang tidak dihapus; nonaktifkan lewat field active. */
@RestController
@RequestMapping("/api/branches")
public class BranchController {

	private final BranchService service;

	public BranchController(BranchService service) {
		this.service = service;
	}

	@GetMapping
	List<BranchResponse> list() {
		return service.list();
	}

	@GetMapping("/{id}")
	BranchResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	BranchResponse create(@Valid @RequestBody BranchRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	BranchResponse update(@PathVariable Long id, @Valid @RequestBody BranchRequest request) {
		return service.update(id, request);
	}

}
