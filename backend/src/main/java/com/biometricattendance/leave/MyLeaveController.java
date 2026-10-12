package com.biometricattendance.leave;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.biometricattendance.leave.LeaveDtos.AttachmentResponse;
import com.biometricattendance.leave.LeaveDtos.DecisionRequest;
import com.biometricattendance.leave.LeaveDtos.LeaveRequestResponse;
import com.biometricattendance.leave.LeaveDtos.SubmitLeaveRequest;

import jakarta.validation.Valid;

/**
 * Pengajuan izin/cuti milik pengguna yang login (K-073). Karyawan ditentukan dari akun, bukan dari input, dan
 * pengajuan karyawan lain dibalas 404.
 */
@RestController
@RequestMapping("/api/me/leave-requests")
public class MyLeaveController {

	private final LeaveService service;

	public MyLeaveController(LeaveService service) {
		this.service = service;
	}

	@GetMapping
	List<LeaveRequestResponse> list() {
		return service.mine();
	}

	@GetMapping("/{id}")
	LeaveRequestResponse get(@PathVariable Long id) {
		return service.getMine(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	LeaveRequestResponse submit(@Valid @RequestBody SubmitLeaveRequest request) {
		return service.submitMine(request);
	}

	@PostMapping("/{id}/cancel")
	LeaveRequestResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest body) {
		return service.cancelMine(id, body == null ? null : body.note());
	}

	@PostMapping(path = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	AttachmentResponse upload(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
		return service.addMyAttachment(id, file);
	}

	@GetMapping("/{id}/attachments/{attachmentId}")
	ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId) {
		return LeaveController.fileResponse(service.downloadMine(id, attachmentId));
	}

}
