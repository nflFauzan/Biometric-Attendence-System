package com.biometricattendance.category;

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

import com.biometricattendance.category.EmployeeCategoryDtos.CategoryRequest;
import com.biometricattendance.category.EmployeeCategoryDtos.CategoryResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/employee-categories")
public class EmployeeCategoryController {

	private final EmployeeCategoryService service;

	public EmployeeCategoryController(EmployeeCategoryService service) {
		this.service = service;
	}

	@GetMapping
	List<CategoryResponse> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
		return service.update(id, request);
	}

}
