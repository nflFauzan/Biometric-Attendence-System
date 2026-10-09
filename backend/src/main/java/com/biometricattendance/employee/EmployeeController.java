package com.biometricattendance.employee;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.biometricattendance.employee.EmployeeDtos.EmployeeRequest;
import com.biometricattendance.employee.EmployeeDtos.EmployeeResponse;

import jakarta.validation.Valid;

/** Karyawan tidak dihapus; isi tanggal keluar dan nonaktifkan. */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

	private final EmployeeService service;

	public EmployeeController(EmployeeService service) {
		this.service = service;
	}

	@GetMapping
	List<EmployeeResponse> search(@RequestParam(required = false) Long branchId,
			@RequestParam(required = false) Boolean active, @RequestParam(required = false) String search) {
		return service.search(branchId, active, search);
	}

	@GetMapping("/{id}")
	EmployeeResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	EmployeeResponse create(@Valid @RequestBody EmployeeRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	EmployeeResponse update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest request) {
		return service.update(id, request);
	}

}
