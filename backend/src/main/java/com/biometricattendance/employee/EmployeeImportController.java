package com.biometricattendance.employee;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.common.ApiException;
import com.biometricattendance.employee.EmployeeImportDtos.CommitRequest;
import com.biometricattendance.employee.EmployeeImportDtos.CommitResponse;
import com.biometricattendance.employee.EmployeeImportDtos.PreviewResponse;

import jakarta.validation.Valid;

/** Impor karyawan dari template Excel client, hanya Owner (K-130, lihat SecurityConfig). */
@RestController
@RequestMapping("/api/employee-imports")
public class EmployeeImportController {

	private final EmployeeImportService service;

	public EmployeeImportController(EmployeeImportService service) {
		this.service = service;
	}

	@PostMapping("/preview")
	PreviewResponse preview(@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
		try (InputStream in = file.getInputStream()) {
			return service.preview(in, startDate);
		}
		catch (IOException e) {
			throw ApiException.badRequest("File bukan Excel .xlsx yang valid");
		}
	}

	@PostMapping
	CommitResponse commit(@Valid @RequestBody CommitRequest request) {
		return service.commit(request);
	}

}
