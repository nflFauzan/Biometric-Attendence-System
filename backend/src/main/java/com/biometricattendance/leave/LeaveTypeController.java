package com.biometricattendance.leave;

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

import com.biometricattendance.common.CurrentUser;
import com.biometricattendance.leave.LeaveDtos.LeaveTypeRequest;
import com.biometricattendance.leave.LeaveDtos.LeaveTypeResponse;

import jakarta.validation.Valid;

/** Jenis izin/cuti. Semua pengguna yang login bisa membaca (Karyawan hanya yang aktif); mengubah hanya Owner. */
@RestController
@RequestMapping("/api/leave-types")
public class LeaveTypeController {

	private final LeaveTypeService service;

	public LeaveTypeController(LeaveTypeService service) {
		this.service = service;
	}

	@GetMapping
	List<LeaveTypeResponse> list() {
		boolean manager = CurrentUser.hasRole("OWNER") || CurrentUser.hasRole("HR_FINANCE");
		return service.list(!manager);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LeaveTypeResponse create(@Valid @RequestBody LeaveTypeRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	LeaveTypeResponse update(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequest request) {
		return service.update(id, request);
	}

}
